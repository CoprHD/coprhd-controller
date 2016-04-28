/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import static com.emc.storageos.svcs.errorhandling.mappers.ServiceCodeExceptionMapper.getHTTPStatus;
import static com.emc.storageos.svcs.errorhandling.mappers.ServiceCodeExceptionMapper.getPreferedLocale;
import static com.emc.storageos.svcs.errorhandling.mappers.ServiceCodeExceptionMapper.toServiceError;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceErrorFactory.toXml;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.keystone.KeystoneConstants;
import com.emc.storageos.keystone.restapi.KeystoneApiClient;
import com.emc.storageos.keystone.restapi.KeystoneRestClientFactory;
import com.emc.storageos.keystone.restapi.model.response.AuthTokenResponse;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * Base class for all the filters that need to create a Bourne understandable user principal
 */
public abstract class AbstractRequestWrapperFilter implements Filter {
    private static final Logger _log = LoggerFactory.getLogger(AbstractRequestWrapperFilter.class);

    @Autowired
    private TokenValidator _tokenValidator;
    @Autowired
    private DbClient _dbClient;

    @Autowired
    protected StorageOSUserRepository _userRepo;
    @Autowired
    private KeystoneRestClientFactory _keystoneFactory;

    @Context
    protected HttpHeaders headers;

    @Override
    public void destroy() {
        // nothing to do
    }
    
    public KeystoneRestClientFactory getKeystoneFactory() {
        return _keystoneFactory;
    }

    /**
     * Creates and returns StorageOSUser from the StorageOSUserDAO given
     * also, populates roles for the user
     * 
     * @param userDAO
     * @param token sets the auth token for the user
     * @param proxyToken sets the proxy token for the user (optional)
     * @return
     */
    private StorageOSUser getStorageOSUser(final StorageOSUserDAO userDAO,
            final String token, final String proxyToken) {
        if (userDAO == null) {
            return null;
        }
        StorageOSUser user = _userRepo.findOne(userDAO);
        if (user != null) {
            user.setToken(token);
            if (proxyToken != null && !proxyToken.isEmpty()) {
                user.setProxyToken(proxyToken);
            }
        }
        return user;
    }

    /**
     * 
     * Looks at the tokens in a request. It will first look at the
     * authentication token. - If the token is valid and considerProxyToken is
     * false, return the corresponding user. - If the token is valid, and
     * considerProxyToken is true, and the corresponding user has the PROXY_USER
     * role, and there is a proxy token on the request, return the corresponding
     * proxy user. Strip it of SECURITY_ADMIN role, and mark is as a proxy user.
     * 
     * @param servletRequest
     * @param considerProxyToken
     * @return
     */
    protected StorageOSUser getStorageOSUserFromRequest(
            final ServletRequest servletRequest, boolean considerProxyToken) {

        StorageOSUser authenticatingUser = getUserFromRequestInternal(servletRequest, false);
        if (authenticatingUser == null) {
            _log.debug("No token");
            return null;
        }
        if (!considerProxyToken || !containsProxyToken(servletRequest)) {
            return authenticatingUser;
        } else {
            if (!authenticatingUser.getRoles().contains(Role.PROXY_USER.toString())) {
                _log.error("User {} does not have PROXY_USER role and is attempting to use a proxy token.",
                        authenticatingUser.getUserName());
                throw APIException.forbidden.userCannotUseProxyTokens(authenticatingUser
                        .getUserName());
            }
            StorageOSUser proxiedUser = getUserFromRequestInternal(servletRequest, true);
            if (proxiedUser == null) {
                _log.error("Could not find proxied user or proxy token is invalid.");
                throw APIException.unauthorized.invalidProxyToken(authenticatingUser
                        .getUserName());
            }
            _log.info("Proxy user {} running as proxied user {}",
                    authenticatingUser.getUserName(),
                    proxiedUser.getUserName());
            // Remove the SECURITY_ADMIN role for the proxy user.
            // Have to rebuild the role set because roles is an unmodifiable collection.
            Set<String> originalRoles = proxiedUser.getRoles();
            HashSet<String> newRoles = new HashSet<String>();
            for (String r : originalRoles) {
                if (!r.equalsIgnoreCase(Role.SECURITY_ADMIN.toString()) &&
                        !r.equalsIgnoreCase(Role.RESTRICTED_SECURITY_ADMIN.toString())) {
                    newRoles.add(r);
                }
            }
            proxiedUser.setRoles(newRoles);
            proxiedUser.setIsProxied(true);
            return proxiedUser;
        }
    }

    /**
     * Retrieves token from the request header or cookie, and looks up StorageOSUser from it
     * 
     * @param servletRequest
     * @param proxyLookup: If true, will look for a proxy token. If false, look for a auth token only.
     * @return StorageOSUser if a validation of the token succeeds. Otherwise null
     */
    private StorageOSUser getUserFromRequestInternal(final ServletRequest servletRequest,
            boolean proxyLookup) {
        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        String authToken = req.getHeader(RequestProcessingUtils.AUTH_TOKEN_HEADER);
        String proxyToken = req.getHeader(RequestProcessingUtils.AUTH_PROXY_TOKEN_HEADER);
        String keystoneUserAuthToken = req.getHeader(RequestProcessingUtils.KEYSTONE_AUTH_TOKEN_HEADER);
        if (authToken != null) {
            if (proxyLookup) {
                if (proxyToken != null) {
                    return getStorageOSUser(_tokenValidator.validateToken(proxyToken), authToken, proxyToken);
                }
            } else {
                return getStorageOSUser(_tokenValidator.validateToken(authToken), authToken, null);
            }
        }

        if (proxyLookup) {
            _log.error("No token found for proxy token lookup.  Returning null.");
            return null;
        }
        if (null != keystoneUserAuthToken) {
            _log.info("The request is for keystone - with token - " + keystoneUserAuthToken);
            return createStorageOSUserUsingKeystone(keystoneUserAuthToken);
        }

        // in cookies
        if (req.getCookies() != null) {
            for (Cookie cookie : req.getCookies()) {
                if (cookie.getName().equalsIgnoreCase(RequestProcessingUtils.AUTH_TOKEN_HEADER)) {
                    authToken = cookie.getValue();
                    StorageOSUser user = getStorageOSUser(_tokenValidator.validateToken(authToken), authToken, null);
                    if (user != null) {
                        return user;
                    } else {
                        // To Do - cache invalid tokens in memory,
                        // just so we don't try to use them next time ?
                    }
                }
            }
        }
        return null;
    }

    private StorageOSUser createStorageOSUserUsingKeystone(String keystoneUserAuthToken)
    {
        _log.debug("START - createStorageOSUserUsingKeystone ");
        StorageOSUser osUser = null;
        // Get the required AuthenticationProvider
        List<URI> authProvidersUri = _dbClient.queryByType(AuthnProvider.class, true);
        List<AuthnProvider> allProviders = _dbClient.queryObject(AuthnProvider.class, authProvidersUri);
        AuthnProvider keystoneAuthProvider = null;
        for (AuthnProvider provider : allProviders) {
            if (AuthnProvider.ProvidersType.keystone.toString().equalsIgnoreCase(provider.getMode())) {
                keystoneAuthProvider = provider;
                break; // We are interested in keystone provider only
            }

        }

        if (null != keystoneAuthProvider) {
            // From the AuthProvider, get the, managedDn, password, server URL and the admin token
            Set<String> serverUris = keystoneAuthProvider.getServerUrls();
            URI baseUri = null;
            for (String uri : serverUris) {
                baseUri = URI.create(uri);
                // Single URI will be present
                break;
            }

            String managerDn = keystoneAuthProvider.getManagerDN();
            String password = keystoneAuthProvider.getManagerPassword();
            Set<String> domains = keystoneAuthProvider.getDomains();
            String adminToken = keystoneAuthProvider.getKeys().get(KeystoneConstants.AUTH_TOKEN);
            String userName = managerDn.split(",")[0].split("=")[1];
            String tenantName = managerDn.split(",")[1].split("=")[1];

            // Invoke keystone API to validate the token
            KeystoneApiClient apiClient = (KeystoneApiClient) _keystoneFactory.getRESTClient(baseUri, userName, password);
            apiClient.setTenantName(tenantName);
            apiClient.setAuthToken(adminToken);

            // From the validation result, read the user role and tenantId
            AuthTokenResponse validToken = apiClient.validateUserToken(keystoneUserAuthToken);
            String openstackTenantId = validToken.getAccess().getToken().getTenant().getId();

            String tempDomain = "";
            for (String domain : domains) {
                tempDomain = domain;
                userName = userName + "@" + domain;
                break;// There will be a single domain
            }

            // convert the openstack tenant id to vipr tenant id
            String viprTenantId = getViPRTenantId(openstackTenantId, tempDomain);
            if (null == viprTenantId) {
                _log.warn("There is no mapping for the OpenStack Tenant in ViPR");
                throw APIException.notFound.openstackTenantNotFound(openstackTenantId);
            }

            _log.debug("Creating OSuser with userName:" + userName + " tenantId:" + viprTenantId);

            osUser = new StorageOSUser(userName, viprTenantId);
            // TODO - remove this once the keystone api is fixed to is_admin=1|0 based on the roles in OpenStack
            osUser.addRole(Role.TENANT_ADMIN.toString());

            // Map the role to ViPR role
            int role_num = validToken.getAccess().getMetadata().getIs_admin();
            if (role_num == 1) {
                osUser.addRole(Role.TENANT_ADMIN.toString());
            }
        }

        _log.debug("END - createStorageOSUserUsingKeystone ");

        return osUser;
    }

    /**
     * Convert openstack tenant id to ViPR tenant id
     * 
     * @param openstackTenantId
     * @return
     */
    private String getViPRTenantId(String openstackTenantId, String domain) {
        String tenantId = null;
        List<URI> tenantOrgUris = _dbClient.queryByType(TenantOrg.class, true);
        List<TenantOrg> tenantOrgList = _dbClient.queryObject(TenantOrg.class, tenantOrgUris);

        boolean found = false;

        for (TenantOrg singleTenant : tenantOrgList) {
            StringSetMap userMappings = singleTenant.getUserMappings();
            if (null != userMappings) {
                StringSet mappingSet = userMappings.get(domain);
                if (null != mappingSet) {
                    for (String str : mappingSet) {
                        if (str.contains(openstackTenantId)) {
                            tenantId = singleTenant.getId().toString();
                            found = true;
                            break;// found the required tenant

                        }
                    }
                }

            }

            if (found)
            {
                break;// exit outer for loop
            }

        }
        return tenantId;
    }

    /*
     * Convenience function to return if the request has a proxy token header
     * specified or not
     * 
     * @param request
     * 
     * @return true if the proxy token header is present
     */
    private boolean containsProxyToken(ServletRequest request) {
        final HttpServletRequest req = (HttpServletRequest) request;
        return req.getHeader(RequestProcessingUtils.AUTH_PROXY_TOKEN_HEADER) == null ? false : true;
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException, ServletException {
        final HttpServletResponse response = (HttpServletResponse) servletResponse;
        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        try {
            final AbstractRequestWrapper reqWrapper = authenticate(servletRequest);
            filterChain.doFilter(reqWrapper, servletResponse);
        } catch (APIException e) {
            _log.debug("unauthorized request: serviceUrl = " + request.getRequestURI(), e);
            response.sendError(toHTTPStatus(e), toServiceErrorXml(e));
            return;
        } catch (final InternalException e) {
            response.sendError(toHTTPStatus(e), toServiceErrorXml(e));
            return;
        }
    }

    /**
     * Method to be overloaded to extract Principal information from request context
     * 
     * @param servletRequest
     * @return AbstractRequestWrapper
     * @throws InternalException
     */
    protected abstract AbstractRequestWrapper authenticate(
            final ServletRequest servletRequest);

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        // nothing to do
    }

    protected String toServiceErrorXml(final Exception e) {
        return toXml(toServiceError(e, getPreferedLocale(headers)));
    }

    protected int toHTTPStatus(final ServiceCoded e) {
        return getHTTPStatus(e).getStatusCode();
    }

    protected int toHTTPStatus(final WebApplicationException e) {
        return getHTTPStatus(e).getStatusCode();
    }

    final public class AbstractRequestWrapper extends HttpServletRequestWrapper {
        private final Principal principal;

        public AbstractRequestWrapper(final HttpServletRequest request,
                final Principal principal) {
            super(request);
            this.principal = principal;
        }

        @Override
        public Principal getUserPrincipal() {
            return this.principal;
        }

        @Override
        public String getRemoteUser() {
            return principal != null ? this.principal.getName() : null;
        }

        @Override
        public boolean isUserInRole(final String role) {
            return false;
        }
    }
}
