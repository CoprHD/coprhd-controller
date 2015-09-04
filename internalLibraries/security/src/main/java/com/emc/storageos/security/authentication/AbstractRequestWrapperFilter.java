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
import java.security.Principal;
import java.util.HashSet;
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

import com.emc.storageos.db.client.model.StorageOSUserDAO;
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
    protected StorageOSUserRepository _userRepo;

    @Context
    protected HttpHeaders headers;

    @Override
    public void destroy() {
        // nothing to do
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

    /**
     * Convenience function to return if the request has a proxy token header
     * specified or not
     * 
     * @param request
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
