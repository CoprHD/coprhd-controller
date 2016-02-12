/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.directory.SearchControls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.PartialResultException;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.auth.SystemPropertyUtil;
import com.emc.storageos.auth.ldap.ActiveDirectoryVersionMap;
import com.emc.storageos.auth.ldap.GroupWhiteList;
import com.emc.storageos.auth.ldap.LdapFilterUtil;
import com.emc.storageos.auth.ldap.OpenLDAPVersionChecker;
import com.emc.storageos.auth.ldap.RootDSE;
import com.emc.storageos.auth.ldap.RootDSEContextMapper;
import com.emc.storageos.auth.ldap.RootDSELDAPContextMapper;
import com.emc.storageos.auth.ldap.StorageOSLdapAuthenticationHandler;
import com.emc.storageos.auth.ldap.StorageOSLdapPersonAttributeDao;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.AuthnProvider.ProvidersType;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.keystone.restapi.KeystoneApiClient;
import com.emc.storageos.keystone.restapi.KeystoneRestClientFactory;
import com.emc.storageos.model.auth.AuthnProviderParamsToValidate;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.ssl.ViPRSSLSocketFactory;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Utility class to encapsulate an immutable list of authentication providers.
 * The class is responsible for reading authn provider configurations from the database
 * 
 */
public class ImmutableAuthenticationProviders {

    private static final String MICROSOFT_ACTIVE_DIRECTORY = "Microsoft Active Directory";
    private static final String LDAP_SERVER = "LDAP Server";
    private static final String CN = "CN";
    private static final String OBJECT_VERSION = "objectVersion";
    private static final int LDAP_VERSION_LEVEL = 3;
    private static final int SEARCH_CTL_COUNT_LIMIT = 1;
    private static final int DEFAULT_SEARCH_CTL_SCOPE = SearchControls.ONELEVEL_SCOPE;
    private static final HashMap<String, Integer> SEARCH_CTL_SCOPES = new HashMap<String, Integer>();
    private static final String LDAPS_PROTOCOL = "ldaps";
    private static final String[] LDAP_ROOT_DSE_RETURN_ATTRIBUTES = { "namingContexts", "subschemaSubentry", "supportedLDAPVersion",
            "supportedControl", "supportedExtension", "objectClass",
            "configContext", "supportedFeatures" };
    private static final String[] LDAP_SCHEMA_ATTRIBUTE_TYPE_ATTRIBUTE = { "attributeTypes" };
    private static final String[] LDAP_SCHEMA_OJBECT_CLASS_ATTRIBUTE = { "objectClasses" };

    static {
        /* TODO: add more search cope mappings here if needed. For example "OBJECT" SCOPE */
        SEARCH_CTL_SCOPES.put(AuthnProvider.SearchScope.ONELEVEL.toString(), SearchControls.ONELEVEL_SCOPE);
        SEARCH_CTL_SCOPES.put(AuthnProvider.SearchScope.SUBTREE.toString(), SearchControls.SUBTREE_SCOPE);
    }

    private final List<AuthenticationProvider> _authenticationProviders;
    private static Logger _log = LoggerFactory.getLogger(ImmutableAuthenticationProviders.class);

    private ImmutableAuthenticationProviders(final List<AuthenticationProvider> authenticationProviders) {
        _authenticationProviders = authenticationProviders;
    }

    public List<AuthenticationProvider> getAuthenticationProviders() {
        return _authenticationProviders;
    }

    /**
     * Factory method to retrieve an instance of this class
     * 
     * @param dbclient: db client to access the configurations
     * @param _localAuthenticationProvider: the local auth provider, which must always be passed in and will
     *            be added in the list first.
     * @param providerConfigs: provider configurations from db
     * @return
     */
    public static ImmutableAuthenticationProviders
            getInstance(DbClient dbclient,
                    CoordinatorClient coordinator,
                    AuthenticationProvider _localAuthenticationProvider,
                    List<AuthnProvider> providerConfigs) {

        List<AuthenticationProvider> authenticationProviders = new ArrayList<AuthenticationProvider>();
        authenticationProviders.add(_localAuthenticationProvider);
        if (providerConfigs == null) {
            // bail here
            _log.info("Skipping load authentication providers from the database");
            return new ImmutableAuthenticationProviders(authenticationProviders);
        }
        _log.info("Loading authentication providers from the database");
        for (AuthnProvider authenticationConfiguration : providerConfigs) {
            _log.debug("Adding auth provider with ID {}", authenticationConfiguration.getId());
            if (authenticationConfiguration.getInactive() || authenticationConfiguration.getDisable()) {
                _log.info("Skipping authentication provider {} because it is inactive", authenticationConfiguration.getId());
                continue;
            }
            try {
                AuthenticationProvider provider =
                        getAuthenticationProvider(coordinator,
                                authenticationConfiguration, dbclient);
                if (null != provider) {
                    authenticationProviders.add(provider);
                }
            } catch (Exception e) {
                _log.error("Failed to create authentication configuration {} with exception {}",
                        authenticationConfiguration.getId(), e);
            }
        }
        _log.info("Loaded {} authentication handlers", authenticationProviders.size());
        return new ImmutableAuthenticationProviders(authenticationProviders);
    }

    /**
     * Return an authentication provider based on the mode
     * 
     * @param authenticationConfiguration authentication provider from the database
     * @param dbclient
     * @return An authentication provider
     */
    public static AuthenticationProvider getAuthenticationProvider(
            CoordinatorClient coordinator,
            final AuthnProvider authenticationConfiguration, DbClient dbclient) {
        if (authenticationConfiguration.getMode()
                .equalsIgnoreCase("ad")) {
            _log.debug("Auth handler is in AD mode");
            return getActiveDirectoryProvider(coordinator, authenticationConfiguration,
                    dbclient);

        } else if (authenticationConfiguration.getMode()
                .equalsIgnoreCase("ldap")) {
            _log.debug("Auth handler is in LDAP mode");
            return getLDAPProvider(coordinator, authenticationConfiguration, dbclient);

        } else if (AuthnProvider.ProvidersType.keystone.toString()
                .equalsIgnoreCase(authenticationConfiguration.getMode())) {
            _log.debug("Auth handler is in keystone mode");
            return getKeystoneProvider(coordinator, authenticationConfiguration, dbclient);
        } else {
            _log.error(
                    "Mode {} not known skipping this authN configuration",
                    authenticationConfiguration.getMode());
        }
        return null;
    }

    /**
     * Add keystone authentication configuration
     * 
     * @param coordinator
     * @param authenticationConfiguration
     * @param dbclient
     * @return
     */
    private static AuthenticationProvider getKeystoneProvider(CoordinatorClient coordinator,
            AuthnProvider authenticationConfiguration, DbClient dbclient) {
        // TODO - Construct the keystone authprovider and return
        return null;
    }

    /**
     * Add an LDAP authentication configuration
     * 
     * @param authenticationConfiguration authentication provider config object
     */
    private static AuthenticationProvider getLDAPProvider(CoordinatorClient coordinator,
            final AuthnProvider authenticationConfiguration, final DbClient dbclient) {
        LdapContextSource contextSource =
                createConfiguredLDAPContextSource(coordinator,
                        authenticationConfiguration,
                        SystemPropertyUtil.getLdapConnectionTimeout(coordinator));

        StorageOSLdapAuthenticationHandler authHandler = createLdapAuthenticationHandler(
                authenticationConfiguration, contextSource);

        String[] returningAttributes = new String[] { StorageOSLdapPersonAttributeDao.COMMON_NAME,
                StorageOSLdapPersonAttributeDao.LDAP_DISTINGUISHED_NAME };
        StorageOSLdapPersonAttributeDao attributeRepository = createLDAPAttributeRepository(
                dbclient, coordinator, authenticationConfiguration, contextSource,
                returningAttributes);

        attributeRepository.setProviderType(ProvidersType.ldap);

        // This is done here to differentiate with ActiveDirectory authn provider.
        // If we do it in the common createLDAPAttributeRepository(), there is no way
        // differentiate the AD and LDAP auth providers.
        setGroupObjectClassesAndMemberAttributes(
                authenticationConfiguration, attributeRepository);

        _log.debug("Adding LDAP mode auth handler to map");

        return new AuthenticationProvider(authHandler, attributeRepository);
    }

    /**
     * Add an active directory authentication configuration
     * 
     * @param authenticationConfiguration provider configuration object
     * @param dbclient
     */
    private static AuthenticationProvider getActiveDirectoryProvider(
            CoordinatorClient coordinator,
            final AuthnProvider authenticationConfiguration, DbClient dbclient) {

        LdapContextSource contextSource =
                createConfiguredLDAPContextSource(coordinator,
                        authenticationConfiguration,
                        SystemPropertyUtil.getLdapConnectionTimeout(coordinator));

        StorageOSLdapAuthenticationHandler authHandler = createLdapAuthenticationHandler(
                authenticationConfiguration, contextSource);

        String[] returningAttributes = new String[] { StorageOSLdapPersonAttributeDao.COMMON_NAME,
                StorageOSLdapPersonAttributeDao.AD_DISTINGUISHED_NAME };
        StorageOSLdapPersonAttributeDao attributeRepository = createLDAPAttributeRepository(dbclient,
                coordinator, authenticationConfiguration, contextSource, returningAttributes);

        attributeRepository.setProviderType(ProvidersType.ad);
        _log.debug("Adding AD mode auth handler to map");

        return new AuthenticationProvider(authHandler, attributeRepository);
    }

    /**
     * Create the AD/LDAP attribute repository
     * 
     * @param authenticationConfiguration AD/LDAP provider configuration
     * @param contextSource AD/LDAP context source
     * @param returningAttributes list of attributes to return
     * @return StorageOSLdapPersonAttributeDao attribute repository for this configuration
     * @throws Exception
     */
    private static StorageOSLdapPersonAttributeDao createLDAPAttributeRepository(DbClient dbclient,
            CoordinatorClient coordinator,
            final AuthnProvider authenticationConfiguration,
            LdapContextSource contextSource,
            String[] returningAttributes) {
        GroupWhiteList groupWhiteList = createGroupWhiteList(authenticationConfiguration);
        StorageOSLdapPersonAttributeDao attributeRepository = new StorageOSLdapPersonAttributeDao();
        attributeRepository.setContextSource(contextSource);
        attributeRepository.setDbClient(dbclient);
        attributeRepository.setGroupWhiteList(groupWhiteList);
        if (null != authenticationConfiguration.getMaxPageSize()) {
            attributeRepository.setMaxPageSize(authenticationConfiguration.getMaxPageSize());
        }
        SearchControls searchControls = new SearchControls();
        searchControls.setCountLimit(SEARCH_CTL_COUNT_LIMIT);
        searchControls.setTimeLimit(SystemPropertyUtil.getLdapConnectionTimeout(coordinator));
        searchControls.setSearchScope(convertSearchScope(authenticationConfiguration
                .getSearchScope()));
        searchControls.setReturningAttributes(returningAttributes);
        attributeRepository.setSearchControls(searchControls);

        if (null == authenticationConfiguration
                .getSearchFilter()) {
            throw APIException.badRequests
                    .failedToCreateAuthenticationHandlerSearchFilterCannotBeNull(authenticationConfiguration
                            .getId());
        } else {
            attributeRepository.setFilter(authenticationConfiguration
                    .getSearchFilter());
        }

        if (null == authenticationConfiguration.getSearchBase()) {
            throw APIException.badRequests
                    .failedToCreateAuthenticationHandlerSearchBaseCannotBeNull(authenticationConfiguration
                            .getId());
        } else {
            attributeRepository.setBaseDN(authenticationConfiguration
                    .getSearchBase());
        }

        return attributeRepository;
    }

    /**
     * Converts a search scope string into the int value to be used in the ldap
     * search control. Handles defaulting and bad values.
     * 
     * @param searchScopeStr
     * @return int value to set into the ldap search control.
     */
    private static int convertSearchScope(String searchScopeStr) {
        Integer scopeValueI = DEFAULT_SEARCH_CTL_SCOPE;
        if (searchScopeStr == null) {
            _log.debug("Search scope not provided.  Using default one level");
        } else {
            scopeValueI = SEARCH_CTL_SCOPES.get(searchScopeStr);
            if (scopeValueI == null) {
                _log.debug("Could not convert search scope parameter value {}", searchScopeStr);
            }
            _log.debug("Provided search scope is: {}", searchScopeStr);
        }
        _log.debug("Search scope to be used is: {}", scopeValueI);
        return scopeValueI == null ? DEFAULT_SEARCH_CTL_SCOPE : scopeValueI;
    }

    /**
     * Create group whitelist
     * 
     * @param authenticationConfiguration provider configuration containing the whitelist parameters
     * @return Group whitelist for this configuration
     */
    private static GroupWhiteList createGroupWhiteList(
            final AuthnProvider authenticationConfiguration) {
        GroupWhiteList whiteList = new GroupWhiteList();
        whiteList
                .setType(authenticationConfiguration.getGroupAttribute() == null ? CN
                        : authenticationConfiguration.getGroupAttribute());
        whiteList.setValues(authenticationConfiguration
                .getGroupWhitelistValues() != null ? authenticationConfiguration
                .getGroupWhitelistValues().toArray(
                        new String[authenticationConfiguration
                                .getGroupWhitelistValues().size()]) : new String[0]);
        return whiteList;
    }

    /**
     * Create the authentication handler for this AD/LDAP configuration
     * 
     * @param authenticationConfiguration AD/LDAP provider configuration
     * @param contextSource AD/LDAP context source
     * @return BindLdapAuthenticationHandler generated from configuration
     * @throws Exception
     */
    private static StorageOSLdapAuthenticationHandler createLdapAuthenticationHandler(
            final AuthnProvider authenticationConfiguration,
            LdapContextSource contextSource) {
        StorageOSLdapAuthenticationHandler authHandler = new StorageOSLdapAuthenticationHandler();
        if (null == authenticationConfiguration
                .getSearchFilter()) {
            throw APIException.badRequests
                    .failedToCreateAuthenticationHandlerSearchFilterCannotBeNull(authenticationConfiguration
                            .getId());
        } else {
            authHandler.setFilter(authenticationConfiguration
                    .getSearchFilter());
        }

        if (null == authenticationConfiguration.getSearchBase()) {
            throw APIException.badRequests
                    .failedToCreateAuthenticationHandlerSearchBaseCannotBeNull(authenticationConfiguration
                            .getId());
        } else {
            authHandler.setSearchBase(authenticationConfiguration
                    .getSearchBase());
        }

        if (null == authenticationConfiguration.getDomains()) {
            throw APIException.badRequests
                    .failedToCreateAuthenticationHandlerDomainsCannotBeNull(authenticationConfiguration
                            .getId());
        } else {
            authHandler.setDomains(authenticationConfiguration.getDomains());
        }
        authHandler.setContextSource(contextSource);
        return authHandler;
    }

    /**
     * Return AD/LDAP context source generated from the configuration
     * 
     * @param authenticationConfiguration The AD/LDAP authentication provider configuration
     * @param environmentProperties bas environment properties for the context source
     * @return LdapContextSource the context source generated from the configuration
     */
    private static LdapContextSource createLDAPContextSource(
            CoordinatorClient coordinator,
            final AuthnProvider authenticationConfiguration,
            final Map<String, Object> environmentProperties) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setAnonymousReadOnly(false);
        contextSource.setPooled(false);
        if (null == authenticationConfiguration.getManagerDN()
                || null == authenticationConfiguration
                        .getManagerPassword()) {
            throw APIException.badRequests
                    .failedToCreateAuthenticationHandlerManagerUserDNPasswordAreRequired(authenticationConfiguration
                            .getId());
        } else {
            contextSource.setUserDn(authenticationConfiguration
                    .getManagerDN());
            contextSource.setPassword(authenticationConfiguration
                    .getManagerPassword());
        }

        if (null == authenticationConfiguration.getServerUrls()) {
            throw APIException.badRequests
                    .failedToCreateAuthenticationHandlerServerURLsAreRequired(authenticationConfiguration
                            .getId());
        } else {
            contextSource.setUrls(authenticationConfiguration.getServerUrls().toArray(
                    new String[authenticationConfiguration.getServerUrls().size()]));

            if (contextSource.getUrls()[0].toLowerCase().startsWith(LDAPS_PROTOCOL)) {
                environmentProperties.put("java.naming.ldap.factory.socket",
                        ViPRSSLSocketFactory.class.getName());
            }
        }
        if (null != environmentProperties) {
            contextSource.setBaseEnvironmentProperties(environmentProperties);
        }
        try {
            contextSource.afterPropertiesSet();
        } catch (Exception ex) {
            _log.error("exception from context source initialization for provider {}",
                    authenticationConfiguration.getId(), ex);
            // TODO - is this a transient error or config error?
            throw SecurityException.fatals
                    .exceptionFromContextSourceInitializationForProvider(
                            authenticationConfiguration.getId(), ex);
        }
        return contextSource;
    }

    /**
     * Creates an LDAPContextSource with the given provider object and optional timeout
     * 
     * @param authProvider the authentication provider object
     * @param timeout: pass a value less than 1 if you wish not to use it.
     * @return LdapContextSource
     */
    private static LdapContextSource createConfiguredLDAPContextSource(
            CoordinatorClient coordinator, AuthnProvider authProvider, int timeout) {
        Map<String, Object> environmentProperties = new HashMap<String, Object>();
        environmentProperties.put("java.naming.security.authentication",
                "simple");
        if (authProvider.getMode().equalsIgnoreCase(AuthnProvider.ProvidersType.ad.toString())) {
            environmentProperties.put("java.naming.ldap.attributes.binary",
                    StorageOSLdapPersonAttributeDao.TOKEN_GROUPS + " " + StorageOSLdapPersonAttributeDao.OBJECT_SID);
        }
        if (!authProvider.getServerUrls().iterator().next().toLowerCase().startsWith(LDAPS_PROTOCOL)
                && timeout > 1) {
            // the timeout property cannot be used with SSL because of:
            // http://www-01.ibm.com/support/docview.wss?uid=swg24010108
            environmentProperties.put("com.sun.jndi.ldap.connect.timeout", String.valueOf((timeout * 1000)));
        }

        return createLDAPContextSource(coordinator, authProvider, environmentProperties);
    }

    /**
     * Verifies basic connectivity of the provider by attempting a connection with
     * the manager DN and password to the provided url
     * 
     * @param param contains the connection parameter
     * @param errorString will contain the message from the exception in case an exception is
     * @return true if success, false if failure
     */
    public static boolean checkProviderStatus(CoordinatorClient coordinator,
            final AuthnProviderParamsToValidate param,
            KeystoneRestClientFactory keystoneFactory,
            StringBuilder errorString, DbClient dbClient) {
        AuthnProvider authConfig = new AuthnProvider();
        authConfig.setManagerDN(param.getManagerDN());
        authConfig.setManagerPassword(param.getManagerPwd());
        StringSet urls = new StringSet();
        urls.addAll(param.getUrls());
        authConfig.setServerUrls(urls);
        if (AuthnProvider.ProvidersType.keystone.toString().equalsIgnoreCase(param.getMode())) {
            authConfig.setMode(AuthnProvider.ProvidersType.keystone.toString());
            checkKeystoneProviderConnectivity(authConfig, keystoneFactory);
            return true;
        } else {
            authConfig.setMode(AuthnProvider.ProvidersType.ldap.toString()); // we don't need AD specifics here
        }

        LdapContextSource contextSource =
                createConfiguredLDAPContextSource(coordinator, authConfig,
                        SystemPropertyUtil.getLdapConnectionTimeout(coordinator));
        LdapTemplate template = new LdapTemplate(contextSource);
        template.setIgnorePartialResultException(true);
        if (!checkManagerDNAndSearchBase(template, param, errorString)) {
            return false;
        }

        boolean isLDAPMode = true;
        if (param.getMode().equals(AuthnProvider.ProvidersType.ad.toString())) {
            isLDAPMode = false;
        }

        // record RootDSE server metadata, including directory type/vendor, supported LDAP versions,
        // and make sure mode matches with AD/LDAP server
        RootDSE rootDSE = null;
        if (isLDAPMode == true) {
            rootDSE = getRootDSE(template, LDAP_ROOT_DSE_RETURN_ATTRIBUTES, new RootDSELDAPContextMapper());
        } else {
            rootDSE = getRootDSE(template, null, new RootDSEContextMapper());
        }

        if (rootDSE == null) {
            return false;
        }

        if (!checkDirectoryType(template, rootDSE, param, errorString)) {
            return false;
        }

        if (isLDAPMode == true) {
            if (dbClient.checkGeoCompatible(AuthnProvider.getExpectedGeoVDCVersionForLDAPGroupSupport())) {
                if (!checkLDAPGroupAttribute(template, rootDSE, param, errorString)) {
                    return false;
                }
                if (!checkLDAPGroupObjectClasses(template, rootDSE, param, errorString)) {
                    return false;
                }
                if (!checkLDAPGroupMemberAttributes(template, rootDSE, param, errorString)) {
                    return false;
                }
            }
            return true;
        } else {
            return checkGroupAttribute(template, rootDSE, param, errorString);
        }
    }

    /**
     * Checks the keystone provider status
     * 
     * @param authConfig
     */
    private static void checkKeystoneProviderConnectivity(AuthnProvider authConfig,
            KeystoneRestClientFactory keystoneFactory) {
        String managerDn = authConfig.getManagerDN();
        String password = authConfig.getManagerPassword();
        StringSet uris = authConfig.getServerUrls();

        String userName = "";
        String tenantName = "";

        try {
            String[] managerdnArray = managerDn.split(",");
            String firstEle = managerdnArray[0];
            String secondEle = managerdnArray[1];
            userName = firstEle.split("=")[1];
            tenantName = secondEle.split("=")[1];
        } catch (Exception ex) {
            throw APIException.badRequests.managerDNInvalid();
        }

        URI authUri = null;
        for (String uri : uris) {
            authUri = URI.create(uri);
            break; // There will be single URL only
        }

        KeystoneApiClient keystoneApi = (KeystoneApiClient) keystoneFactory.getRESTClient(
                authUri, userName, password);
        keystoneApi.setTenantName(tenantName);
        keystoneApi.authenticate_keystone();
    }

    /**
     * Validates the connection to LDAP and manager DN credentials, search base
     * 
     * @param template the ldap template to use
     * @param param the param structure containing the parameters to validate
     * @param errorString output parameter to store error string
     * @return true if validation succeeded. false otherwise
     */
    private static boolean checkManagerDNAndSearchBase(LdapTemplate template, final AuthnProviderParamsToValidate param,
            StringBuilder errorString) {
        try {
            // authenticates manager credentials and performs the look up for the search base
            template.lookup(new DistinguishedName(param.getSearchBase()));
            return true;
        } catch (CommunicationException e) {
            errorString
                    .append(MessageFormat
                            .format("Connection to LDAP server {0} failed. Please, check the scheme, accessibility of the LDAP server and port. LDAP error: {1}.",
                                    param.getUrls().toString(), stripNonPrintableCharacters(e.getMessage())));
            _log.debug("Connection to LDAP server " + param.getUrls().toString()
                    + " failed.", e);
            return false;
        } catch (AuthenticationException e) {
            errorString
                    .append(MessageFormat
                            .format("Connection to the LDAP server {0} succeeded but the Manager DN {1} or its password failed to authenticate.  LDAP error: {2}",
                                    param.getUrls().toString(), param.getManagerDN(), stripNonPrintableCharacters(e.getMessage())));
            return false;
        } catch (NameNotFoundException e) {
            errorString
                    .append(MessageFormat
                            .format("Connection to the LDAP server {0} succeeded and the Manager DN authenticated successfully but the search base path {1} could not be found in the LDAP tree. LDAP error: {2}",
                                    param.getUrls().toString(), param.getSearchBase(), stripNonPrintableCharacters(e.getMessage())));
            return false;
        } catch (PartialResultException e) {
            errorString
                    .append(MessageFormat
                            .format("Connection to the LDAP server {0} succeeded and the Manager DN authenticated successfully but a portion of the search base path {1} could not be found in the LDAP tree. LDAP error: {2}",
                                    param.getUrls().toString(), param.getSearchBase(), stripNonPrintableCharacters(e.getMessage())));
            return false;
        } catch (Exception e) {
            errorString.append(MessageFormat.format(
                    "Validation of the Manager DN {0} and search base {1} against the LDAP server {2} failed because of LDAP error: {3}",
                    param.getManagerDN(), param.getSearchBase(), param.getUrls().toString(), stripNonPrintableCharacters(e.getMessage())));
            return false;
        }
    }

    /**
     * Queries the AD schema to check that the group attribute exists
     * 
     * @param template the ldap template to use
     * @param rootDSE the RootDSE object
     * @param param the param structure containing the parameters to validate
     * @param errorString output parameter to store error string
     * @return true if validation succeeded. false otherwise
     */
    @SuppressWarnings("rawtypes")
    private static boolean checkGroupAttribute(LdapTemplate template, final RootDSE rootDSE, final AuthnProviderParamsToValidate param,
            StringBuilder errorString) {
        try {
            // retrieve the rootDSE's schemaNamingContext operational attribute
            String schemaDN = rootDSE.getSchemaNamingContext();

            // query for the attribute
            List list = template.search(schemaDN, LdapFilterUtil.getAttributeFilterWithValues(param.getGroupAttr()),
                    SearchControls.ONELEVEL_SCOPE, new AbstractContextMapper() {
                        @Override
                        protected Object doMapFromContext(DirContextOperations ctx) {
                            return ctx.getStringAttribute("cn");
                        }
                    });

            if (CollectionUtils.isEmpty(list)) {
                errorString.append(MessageFormat.format("The group attribute {0} could not be found in AD schema at server {1}.",
                        param.getGroupAttr(), param.getUrls().toString()));
                return false;
            } else {
                _log.debug("Found attribute: {} {}", list.get(0), param.getGroupAttr());
            }
            return true;
        } catch (CommunicationException e) {
            errorString.append(MessageFormat.format(
                    "Connection to LDAP server {0} failed during search for group attribute {1}. LDAP error: {2}",
                    param.getUrls().toString(), param.getGroupAttr(), stripNonPrintableCharacters(e.getMessage())));
            return false;
        } catch (Exception e) {
            errorString.append(MessageFormat.format(
                    "Validation of group attribute {0} against server {1} failed because of LDAP error: {2}",
                    param.getGroupAttr(), param.getUrls().toString(), stripNonPrintableCharacters(e.getMessage())));
            return false;
        }
    }

    /**
     * Retrieve AD/LDAP's RootDSE.
     * 
     * @param template - A AD/LDAP template to be searched.
     * 
     * @return - RootDSE object
     */
    private static RootDSE getRootDSE(LdapTemplate template, String[] returnAttributes,
            AbstractContextMapper contextMapper) {
        // retrieve the rootDSE
        @SuppressWarnings("rawtypes")
        List list = template.search("", "(objectclass=*)", SearchControls.OBJECT_SCOPE,
                returnAttributes, contextMapper);

        if (CollectionUtils.isEmpty(list)) {
            _log.error("Could not query RootDSE for AD/LDAP");
            return null;
        }

        RootDSE rootDSE = (RootDSE) list.get(0);
        _log.info("RootDSE: {}", rootDSE.toString());

        return rootDSE;
    }

    /**
     * Check directory type/vendor, supported LDAP versions,
     * and make sure configured mode matches with AD/LDAP server type
     * 
     * @param template the ldap template to use
     * @param rootDSE the RootDSE object
     * @param param the param structure containing the parameters to validate
     * @param errorString output parameter to store error string
     * @return true if validation succeeded. false otherwise
     */
    @SuppressWarnings("rawtypes")
    private static boolean checkDirectoryType(LdapTemplate template, RootDSE rootDSE, final AuthnProviderParamsToValidate param,
            StringBuilder errorString) {
        // check LDAP version
        boolean ldapVersionPassed = false;
        if (rootDSE.getSupportedLDAPVersion() != null) {
            for (int i = 0; i < rootDSE.getSupportedLDAPVersion().length; i++) {
                if (rootDSE.getSupportedLDAPVersion()[i] >= LDAP_VERSION_LEVEL) {
                    ldapVersionPassed = true;
                    break;
                }
            }
            if (!ldapVersionPassed) {
                String errorMsg = MessageFormat.format("Supported LDAP version is insufficient at server {0}: must be at least {1}.",
                        param.getUrls().toString(), LDAP_VERSION_LEVEL);
                errorString.append(errorMsg);
                _log.error(errorMsg);
                return false;
            }
        } else {
            _log.warn("Failed to get supported LDAP versions at server {}", param.getUrls().toString());
        }

        String serverType = null;
        // check active directory
        String rootDomainNamingContext = rootDSE.getRootDomainNamingContext();
        if (rootDomainNamingContext == null
                || rootDomainNamingContext.equals("")) {
            serverType = LDAP_SERVER;
            if (!param.getMode().equals("ldap")) {
                String errorMsg = MessageFormat.format("Directory server type LDAP doesn't match with specified mode {1} at server {0}.",
                        param.getUrls().toString(), param.getMode());
                errorString.append(errorMsg);
                _log.error(errorMsg);
                return false;
            }

            serverType = OpenLDAPVersionChecker.getOpenLDAPVersion(rootDSE);
            if (serverType == null) {
                serverType = LDAP_SERVER;
            }

            _log.info("Server type: {} at {}", serverType, param.getUrls().toString());

        } else {
            serverType = MICROSOFT_ACTIVE_DIRECTORY;
            if (!param.getMode().equals("ad")) {
                // using AD as LDAP server only will not take advantage of AD specific features, but it is allowed
                String errorMsg = MessageFormat.format(
                        "Directory server type Active Directory doesn't match with specified mode {1} at server {0}.",
                        param.getUrls().toString(), param.getMode());
                _log.warn(errorMsg);
            }

            // retrieve the rootDSE's schemaNamingContext operational attribute
            String schemaDN = rootDSE.getSchemaNamingContext();
            if (schemaDN == null || schemaDN.equals("")) {
                String errorMsg = MessageFormat.format("Could not find Schema Naming Context for server {0}", param.getUrls().toString());
                errorString.append(errorMsg);
                _log.error(errorMsg);
                return false;
            } else {
                _log.debug("Found Schema DN: {} for server {}", schemaDN, param.getUrls().toString());
            }

            // check and record objectVersion, windows server type
            try {
                List list = template.search(schemaDN, "(objectclass=*)",
                        SearchControls.OBJECT_SCOPE, new AbstractContextMapper() {
                            @Override
                            protected Object doMapFromContext(DirContextOperations ctx) {
                                return ctx.getStringAttribute(OBJECT_VERSION);
                            }
                        });

                if (CollectionUtils.isEmpty(list)) {
                    String errorMsg = MessageFormat.format("The attribute {0} could not be found in AD schema at server {1}.",
                            OBJECT_VERSION, param.getUrls().toString());
                    errorString.append(errorMsg);
                    _log.error(errorMsg);
                    return false;
                }

                String objectVersion = (String) list.get(0);
                String windowsServer = ActiveDirectoryVersionMap.getActiveDirectoryVersion(objectVersion);
                String infoMsg = MessageFormat
                        .format("Active Directory server information {0} - server type: {1}, objectVersion: {2}, Microsoft Windows Server version: {3}",
                                param.getUrls().toString(), serverType, objectVersion, windowsServer);
                _log.info(infoMsg);
                return true;
            } catch (CommunicationException e) {
                String errorMsg = MessageFormat
                        .format("Connection to Active Directory server {0} failed during query of schema DN ({1})'s objectVersion attribute. LDAP error: {2}",
                                param.getUrls().toString(), schemaDN, stripNonPrintableCharacters(e.getMessage()));
                errorString.append(errorMsg);
                _log.error(errorMsg);
                return false;
            } catch (Exception e) {
                String errorMsg = MessageFormat.format("Query {0} against server {1} failed because of LDAP error: {2}",
                        OBJECT_VERSION, param.getUrls().toString(), stripNonPrintableCharacters(e.getMessage()));
                errorString.append(errorMsg);
                _log.error(errorMsg);
                return false;
            }
        }
        return true;
    }

    /**
     * removes unprintable chartacters from input string
     * 
     * @param input
     * @return cleaned string
     */
    private static String stripNonPrintableCharacters(String input) {
        return input.replaceAll("[\\x00-\\x1F]", "");
    }

    /**
     * Method that sets the Group's objectClasses and member attributes
     * that will be used to search the corresponding group in the LDAP.
     * This is called only from the LDAP authn provider creation, just to make
     * sure that, these values are used only for the LDAP authn providers.
     * 
     * @param authenticationConfiguration - Configuration of an authn provider.
     * @param attributeRepository - Authn provider repository.
     * 
     */
    private static void setGroupObjectClassesAndMemberAttributes(
            final AuthnProvider authenticationConfiguration,
            StorageOSLdapPersonAttributeDao attributeRepository) {
        if (null != authenticationConfiguration.getGroupObjectClassNames()) {
            attributeRepository.getGroupObjectClasses().addAll(authenticationConfiguration.getGroupObjectClassNames());
            _log.debug("Adding group object classes {} for LDAP", attributeRepository.getGroupObjectClasses());
        }

        if (null != authenticationConfiguration.getGroupMemberAttributeTypeNames()) {
            attributeRepository.getGroupMemberAttributes().addAll(authenticationConfiguration.getGroupMemberAttributeTypeNames());
            _log.debug("Adding group member attributes {} for LDAP", attributeRepository.getGroupMemberAttributes());
        }
    }

    /**
     * A method that searches LDAP schema to find if the given attribute is
     * a valid attribute of the schema or not. These attributes include
     * attributeTypes, objectClasses, etc.
     * 
     * @param template - A LDAP template to be searched.
     * @param rootDSE - Root DSE of the LDAP.
     * @param errorString - Error string to be returned in case of any errors
     *            during the search.
     * 
     * @return - List of all the attributes of the LDAP schema that matches
     *         search criteria.
     */
    private static List<List<String>> searchInLDAPSchema(LdapTemplate template, String[] returnAttributes,
            final RootDSE rootDSE, final List<String> ldapServerUrls,
            StringBuilder errorString) {
        try {
            // retrieve the rootDSE's schemaNamingContext operational attribute
            String schemaDN = rootDSE.getSchemaNamingContext();

            _log.debug("Searching in LDAP schema DN {} ", schemaDN);

            // query for the attribute
            @SuppressWarnings("unchecked")
            List<List<String>> attributeList = template.search(schemaDN, "(objectclass=*)", SearchControls.OBJECT_SCOPE,
                    returnAttributes, new LDAPSchemaContextMapper());

            if (CollectionUtils.isEmpty(attributeList)) {
                errorString.append(MessageFormat.format("The attributes {0} could not be found in LDAP schema {1} at server {2}",
                        returnAttributes.toString(), schemaDN, ldapServerUrls.toString()));
            }
            return attributeList;
        } catch (CommunicationException e) {
            errorString.append(MessageFormat.format(
                    "Connection to LDAP server {0} failed during search for attribute {1}. LDAP error: {2}",
                    ldapServerUrls.toString(), returnAttributes.toString(), stripNonPrintableCharacters(e.getMessage())));
            return null;
        } catch (Exception e) {
            errorString.append(MessageFormat.format(
                    "Exception during attribute {0} search against server {1} failed because of LDAP error: {2}",
                    returnAttributes.toString(), ldapServerUrls.toString(), stripNonPrintableCharacters(e.getMessage())));
            return null;
        }
    }

    /**
     * Queries the LDAP schema to check that the group attribute exists
     * 
     * @param template - A LDAP template to be searched.
     * @param rootDSE - Root DSE of the LDAP.
     * @param errorString - Error string to be returned in case of any errors
     *            during the search.
     * 
     * @return - List of all the attributes of the LDAP schema that matches
     *         search criteria.
     */
    private static boolean checkLDAPGroupAttribute(LdapTemplate template, final RootDSE rootDSE,
            final AuthnProviderParamsToValidate param, StringBuilder errorString) {
        boolean isValidGroupAttribute = false;
        String schemaDN = rootDSE.getSchemaNamingContext();
        List<List<String>> groupAttributeLists = searchInLDAPSchema(template, LDAP_SCHEMA_ATTRIBUTE_TYPE_ATTRIBUTE,
                rootDSE, param.getUrls(), errorString);

        if (CollectionUtils.isEmpty(groupAttributeLists)) {
            return isValidGroupAttribute;
        }

        String groupAttributeToValidate = param.getGroupAttr();
        for (List<String> groupAttributeList : groupAttributeLists) {
            for (String groupAttribute : groupAttributeList) {
                if (groupAttribute.equalsIgnoreCase(groupAttributeToValidate)) {
                    isValidGroupAttribute = true;
                    _log.debug("Found group attribute {} in LDAP schema {}", groupAttributeToValidate, schemaDN);
                    break;
                }
            }
            if (isValidGroupAttribute) {
                break;
            }
        }

        if (!isValidGroupAttribute) {
            errorString.append(MessageFormat.format("Could not find group attribute {0} in LDAP schema {1} at {2}",
                    param.getGroupAttr(), schemaDN, param.getUrls().toString()));
        }

        return isValidGroupAttribute;
    }

    /**
     * Queries the LDAP schema to check that the group objectClass exists
     * 
     * @param template - A LDAP template to be searched.
     * @param rootDSE - Root DSE of the LDAP.
     * @param errorString - Error string to be returned in case of any errors
     *            during the search.
     * 
     * @return - List of all the attributes of the LDAP schema that matches
     *         search criteria.
     */
    private static boolean checkLDAPGroupObjectClasses(LdapTemplate template, RootDSE rootDSE,
            final AuthnProviderParamsToValidate param, StringBuilder errorString) {
        boolean isValidGroupObjectClasses = true;
        String schemaDN = rootDSE.getSchemaNamingContext();
        Set<String> errorGroupObjectClasses = null;

        List<List<String>> groupObjectClassLists = searchInLDAPSchema(template, LDAP_SCHEMA_OJBECT_CLASS_ATTRIBUTE,
                rootDSE, param.getUrls(), errorString);

        if (CollectionUtils.isEmpty(groupObjectClassLists)) {
            isValidGroupObjectClasses = false;
        } else {
            errorGroupObjectClasses = new HashSet<String>();
            for (String expGroupObjectClass : param.getGroupObjectClasses()) {
                boolean groupObjectClassFound = false;
                for (List<String> groupObjectClassList : groupObjectClassLists) {
                    for (String groupObjectClass : groupObjectClassList) {
                        if (groupObjectClass.equalsIgnoreCase(expGroupObjectClass)) {
                            _log.debug("Found objectClass {} in LDAP schema {}", expGroupObjectClass, schemaDN);
                            groupObjectClassFound = true;
                            break;
                        }
                    }
                    if (groupObjectClassFound) {
                        break;
                    }
                }

                if (!groupObjectClassFound) {
                    errorGroupObjectClasses.add(expGroupObjectClass);
                }
            }
        }

        if (!CollectionUtils.isEmpty(errorGroupObjectClasses)) {
            errorString.append(MessageFormat.format("Could not find objectClasses {0} in LDAP schema {1} at {2}",
                    errorGroupObjectClasses.toString(), schemaDN, param.getUrls().toString()));

            isValidGroupObjectClasses = false;
        }

        return isValidGroupObjectClasses;
    }

    /**
     * Queries the LDAP schema to check that the group attribute type exists
     * 
     * @param template - A LDAP template to be searched.
     * @param rootDSE - Root DSE of the LDAP.
     * @param errorString - Error string to be returned in case of any errors
     *            during the search.
     * 
     * @return - List of all the attributes of the LDAP schema that matches
     *         search criteria.
     */
    private static boolean checkLDAPGroupMemberAttributes(LdapTemplate template, RootDSE rootDSE,
            AuthnProviderParamsToValidate param, StringBuilder errorString) {
        boolean isValidGroupMemberAttributes = true;
        String schemaDN = rootDSE.getSchemaNamingContext();
        Set<String> errorGroupMemberAttributes = null;

        List<List<String>> groupMemberAttributeLists = searchInLDAPSchema(template, LDAP_SCHEMA_ATTRIBUTE_TYPE_ATTRIBUTE,
                rootDSE, param.getUrls(), errorString);

        if (CollectionUtils.isEmpty(groupMemberAttributeLists)) {
            isValidGroupMemberAttributes = false;
        } else {
            errorGroupMemberAttributes = new HashSet<String>();
            for (String expGroupMemberAttribute : param.getGroupMemberAttributes()) {
                boolean groupMemberAttributeFound = false;
                for (List<String> groupMemberAttributeList : groupMemberAttributeLists) {
                    for (String groupMemberAttribute : groupMemberAttributeList) {
                        if (groupMemberAttribute.equalsIgnoreCase(expGroupMemberAttribute)) {
                            _log.debug("Found member attribute {} in LDAP schema {}", expGroupMemberAttribute, schemaDN);
                            groupMemberAttributeFound = true;
                            break;
                        }
                    }
                    if (groupMemberAttributeFound) {
                        break;
                    }
                }

                if (!groupMemberAttributeFound) {
                    errorGroupMemberAttributes.add(expGroupMemberAttribute);
                }
            }
        }

        if (!CollectionUtils.isEmpty(errorGroupMemberAttributes)) {
            errorString.append(MessageFormat.format("Could not find attributes {0} in LDAP schema {1} at {2}",
                    errorGroupMemberAttributes.toString(), schemaDN, param.getUrls().toString()));

            isValidGroupMemberAttributes = false;
        }

        return isValidGroupMemberAttributes;
    }
}
