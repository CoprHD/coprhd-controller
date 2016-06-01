/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import com.emc.storageos.auth.*;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.resource.UserInfoPage.UserDetails;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default Authentication manager implementation
 */
public class CustomAuthenticationManager implements AuthenticationManager {
    private final Logger _log = LoggerFactory.getLogger(CustomAuthenticationManager.class);
    private static final int _DEFAULT_UPDATE_CHECK_MINUTES = 10;
    private static final int _DEFAULT_UPDATE_RETRY_SECONDS = 2;
    private int _providerUpdateCheckMinutes = _DEFAULT_UPDATE_CHECK_MINUTES;
    private final int _providerUpdateRetrySeconds = _DEFAULT_UPDATE_RETRY_SECONDS;
    private ImmutableAuthenticationProviders _authNProviders;
    private DbClient _dbClient;
    private CoordinatorClient _coordinator;
    private AuthenticationProvider _localAuthenticationProvider;
    private final ProviderConfigUpdater _updaterRunnable = new ProviderConfigUpdater();
    private LdapProviderMonitor _ldapProviderMonitor;

    @Autowired
    protected TokenManager _tokenManager;

    public CustomAuthenticationManager() {
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public void setProviderUpdateCheckMinutes(int providerUpdateCheckMinutes) {
        _providerUpdateCheckMinutes = providerUpdateCheckMinutes;
    }

    public void setTokenManager(TokenManager tokenManager) {
        _tokenManager = tokenManager;
    }

    @Override
    public StorageOSUserDAO authenticate(final Credentials credentials) {
        boolean found = false;
        String handlerName;

        for (AuthenticationProvider provider : getAuthenticationProviders()) {
            StorageOSAuthenticationHandler authenticationHandler = provider.getHandler();
            StorageOSPersonAttributeDao attributeRepository = provider.getAttributeRepository();
            if (!authenticationHandler.supports(credentials)) {
                continue;
            }

            found = true;
            handlerName = authenticationHandler.getClass().getName();

            if (authenticationHandler.authenticate(credentials)) {
                _log.info("{} successfully authenticated {}", handlerName, logFormat(credentials));

                final StorageOSUserDAO user = attributeRepository.getStorageOSUser(credentials);
                _log.info("Authenticated {}.", user);
                _log.debug("Attribute map for {}: {}", user, user.getAttributes());
                return user;
            }

            _log.info("{} failed to authenticate {}", handlerName, logFormat(credentials));
        }

        // failed authn
        if (found) {
            _log.error("Failed to authenticate {}", logFormat(credentials));
            return null;
        }

        // we don't have a handler that supports the credentials given
        _log.error("Unsupported credentials {}", logFormat(credentials));
        return null;
    }

    @Override
    public boolean isGroupValid(final String groupId,
            ValidationFailureReason[] failureReason) {
        boolean providerFound = false;
        if (isUserGroup(groupId)) {
            // If the domain component is empty consider this group
            // as a local userGroup. So, avoid the validation
            // of the group from the authnProvider.
            return isValidUserGroup(failureReason, groupId);
        } else {
            UsernamePasswordCredentials groupCreds = new UsernamePasswordCredentials(groupId, "");
            for (AuthenticationProvider provider : getAuthenticationProviders()) {
                if (!provider.getHandler().supports(groupCreds)) {
                    continue;
                }
                providerFound = true;
                _log.debug("Found auth handler: {}", provider.getHandler().getClass());
                try {
                    if (provider.getAttributeRepository().isGroupValid(groupId, failureReason)) {
                        return true;
                    }
                } catch (Exception e) {
                    failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
                    _log.error("Exception validating group {}", groupId, e);
                }
            }
        }
        if (!providerFound) {
            _log.error(
                    "Could not find an authentication provider that supports the group {}",
                    groupId);
        }
        return false;
    }

    @Override
    public void validateUser(final String userId, final String tenantId, final String altTenantId) {
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(userId, "");
        boolean providerFound = false;
        for (AuthenticationProvider provider : getAuthenticationProviders()) {
            if (!provider.getHandler().supports(creds)) {
                continue;
            }
            providerFound = true;
            provider.getAttributeRepository().validateUser(userId, tenantId, altTenantId);
        }
        if (!providerFound) {
            _log.error(
                    "Could not find an authentication provider that supports the user {}",
                    userId);
            throw APIException.badRequests.noAuthnProviderFound(userId);
        }
    }

    @Override
    public UserDetails getUserDetails(final String username) {
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, "");
        for (AuthenticationProvider provider : getAuthenticationProviders()) {
            if (!provider.getHandler().supports(creds)) {
                continue;
            }
            ValidationFailureReason[] reason =
                    new ValidationFailureReason[] { ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT };
            StorageOSUserDAO user =
                    provider.getAttributeRepository().getStorageOSUser(creds, reason);

            if (user != null) {
                UserDetails userDetails = new UserDetails();
                userDetails.setUsername(username);
                userDetails.getUserGroupList().addAll(user.getGroups());
                userDetails.setTenant(user.getTenantId());
                return userDetails;
            } else {
                switch (reason[0]) {
                    case LDAP_CONNECTION_FAILED:
                        throw SecurityException.fatals
                                .communicationToLDAPResourceFailed();
                    case LDAP_MANAGER_AUTH_FAILED:
                        throw SecurityException.fatals.ldapManagerAuthenticationFailed();
                    default:
                    case USER_OR_GROUP_NOT_FOUND_FOR_TENANT:
                        throw APIException.badRequests.principalSearchFailed(username);
                        // LDAP_CANNOT_SEARCH_GROUP_IN_LDAP_MODE case is not needed here as
                        // this is only user search.
                }
            }
        }
        throw APIException.badRequests.principalSearchFailed(username);
    }

    @Override
    public void refreshUser(String username) throws SecurityException, BadRequestException {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, "");

        for (AuthenticationProvider provider : getAuthenticationProviders()) {
            StorageOSAuthenticationHandler authenticationHandler = provider.getHandler();
            if (!authenticationHandler.supports(credentials)) {
                continue;
            }

            List<StorageOSUserDAO> userDAOs =
                    _tokenManager.getUserRecords(username);
            if (CollectionUtils.isEmpty(userDAOs)) {
                _log.error("user " + username + "does not exist in database");
                throw APIException.badRequests.invalidParameter("username", username);
            }

            ValidationFailureReason[] failureReason = new ValidationFailureReason[] { ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT };
            StorageOSPersonAttributeDao attributeRepository = provider.getAttributeRepository();
            final StorageOSUserDAO userDAO = attributeRepository.getStorageOSUser(
                    credentials, failureReason);
            // if we had connection failures, then just throw exceptions and don't update
            // anything...
            if (userDAO == null
                    && failureReason[0] == ValidationFailureReason.LDAP_CONNECTION_FAILED) {
                throw SecurityException.fatals.communicationToLDAPResourceFailed();
            } else if (userDAO == null
                    && failureReason[0] == ValidationFailureReason.LDAP_MANAGER_AUTH_FAILED) {
                throw SecurityException.fatals.ldapManagerAuthenticationFailed();
            } else if (userDAO == null) {
                // we coudln't find the user, which means it's no longer valid, so we need
                // to logout the user
                _tokenManager.deleteAllTokensForUser(username, true);
                throw APIException.badRequests.principalSearchFailed(username);
            }
            // update the user records in the DB
            _tokenManager.updateDBWithUser(userDAO, userDAOs);
            return;
        }

        // we don't have a handler that supports the given credentials
        _log.error("Unsupported credentials {}", username);
        _tokenManager.deleteAllTokensForUser(username, true);
        // failed to refresh
        throw APIException.badRequests.principalSearchFailed(username);
    }

    protected List<AuthenticationProvider> getAuthenticationProviders() {
        return _authNProviders.getAuthenticationProviders();
    }

    public void setLocalAuthenticationProvider(AuthenticationProvider localAuthenticationProvider) {
        _localAuthenticationProvider = localAuthenticationProvider;
    }

    /*
     * @see com.emc.storageos.auth.AuthenticationManager#getUserTenants(java.lang.String)
     */
    @Override
    public Map<URI, UserMapping> getUserTenants(String username) {
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, "");
        for (AuthenticationProvider provider : getAuthenticationProviders()) {
            if (!provider.getHandler().supports(creds)) {
                continue;
            }
            return provider.getAttributeRepository().getUserTenants(username);

        }
        return null;
    }

    @Override
    public Map<URI, UserMapping> peekUserTenants(String username,
            URI tenantUri,
            List<UserMapping> userMappings) {
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, "");
        for (AuthenticationProvider provider : getAuthenticationProviders()) {
            if (!provider.getHandler().supports(creds)) {
                continue;
            }
            return provider.getAttributeRepository().peekUserTenants(username, tenantUri, userMappings);
        }
        return null;
    }

    /**
     * Return something useful to log from the credentials object
     * We don't want to just log credentials because it may contain
     * sensisitive information such as a password
     * 
     * @param credentials the credentials that will be logged
     * @return the object to identify the credentials in log
     */
    private Object logFormat(final Credentials credentials) {
        if (UsernamePasswordCredentials.class.isAssignableFrom(credentials.getClass())) {
            return ((UsernamePasswordCredentials) credentials).getUserName();
        } else {
            return credentials.getClass();
        }
    }

    /*
     * @see com.emc.storageos.auth.AuthenticationManager#init()
     */
    @Override
    public void init() {
        // initialized the provider list to local only, called only from the bean init
        _authNProviders =
                ImmutableAuthenticationProviders.getInstance(_dbClient, _coordinator,
                        _localAuthenticationProvider, null);
        // start the async reload thread
        Thread thread = new Thread(_updaterRunnable);
        thread.setName("ProviderConfigUpdater");
        _updaterRunnable.wakeup();
        thread.start();

        _ldapProviderMonitor = new LdapProviderMonitor(_coordinator, _dbClient, _authNProviders);
        _ldapProviderMonitor.start();
    }

    @Override
    public void shutdown() {
        _updaterRunnable.shutdown();
        _updaterRunnable.wakeup();
        _ldapProviderMonitor.stop();
    }

    @Override
    public void reload() {
        _updaterRunnable.wakeup();
    }

    /**
     * Class for reloading provider config from database when needed
     */
    private class ProviderConfigUpdater implements Runnable {
        private final Logger _log = LoggerFactory.getLogger(ProviderConfigUpdater.class);
        private boolean _doRun = true;
        private Long _lastNotificationTime = 0L;
        private Long _lastReloadTime = 0L;
        private HashMap<URI, Long> _lastKnownConfiguration = null;
        private int _lastKnownLdapConnectionTimeout = 0;

        private class Waiter {
            private long _t = 0;

            public synchronized void sleep(long milliSeconds) {
                _t = System.currentTimeMillis() + milliSeconds;
                while (true) {
                    final long dt = _t - System.currentTimeMillis();
                    if (dt <= 0) {
                        return;
                    } else {
                        try {
                            if (_lastNotificationTime <= _lastReloadTime) {
                                wait(dt);
                            } else {
                                return;
                            }
                        } catch (InterruptedException e) {
                            _log.info("ProviderConfigUpdater: waiter interrupted", e);
                        }
                    }
                }
            }

            public synchronized void wakeup() {
                _t = 0;
                notifyAll();
            }
        }

        private final Waiter _waiter = new Waiter();

        private void sleep(final long ms) {
            _waiter.sleep(ms);
        }

        public void wakeup() {
            _lastNotificationTime = System.currentTimeMillis();
            _log.info("received notification to reload {}", _lastNotificationTime);
            _waiter.wakeup();
        }

        public void shutdown() {
            _doRun = false;
        }

        // load provider list from db
        private List<AuthnProvider> getProvidersFromDB() {
            _log.debug("Reading authentication providers from the database");
            List<URI> providerIds = _dbClient.queryByType(AuthnProvider.class, true);
            return _dbClient.queryObject(AuthnProvider.class, providerIds);
        }

        /**
         * checks to see if any of the provider configuration is updated from when we have seen it last
         * 
         * @return true if it is updated, false otherwise
         * @throws DatabaseException
         */
        private boolean checkForUpdates(List<AuthnProvider> providers) {

            if (_lastKnownConfiguration != null) {
                // compare to check if anything changed
                // check count
                if (_lastKnownConfiguration.keySet().size() != providers.size()) {
                    return true;
                }
                // check they all match
                for (AuthnProvider provider : providers) {
                    // we have seen this before
                    if (!_lastKnownConfiguration.containsKey(provider.getId())) {
                        return true;
                    }
                    // we have the same lastmodified timestamp
                    if (!_lastKnownConfiguration.get(provider.getId()).equals(provider.getLastModified())) {
                        return true;
                    }
                }
            } else {
                if (!providers.isEmpty()) {
                    return true;
                }
            }

            if (_lastKnownLdapConnectionTimeout != SystemPropertyUtil.getLdapConnectionTimeout(_coordinator)) {
                return true;
            }

            // nothing is changed
            return false;
        }

        /**
         * update out last known provider config information
         * 
         * @param knownProviders
         */
        private void updateLastKnown(List<AuthnProvider> knownProviders) {
            _lastKnownConfiguration = new HashMap<URI, Long>();
            for (AuthnProvider provider : knownProviders) {
                _lastKnownConfiguration.put(provider.getId(), provider.getLastModified());
            }
            _lastKnownLdapConnectionTimeout = SystemPropertyUtil.getLdapConnectionTimeout(_coordinator);
        }

        @Override
        public void run() {
            while (_doRun) {
                boolean bForceReload = (_lastNotificationTime > _lastReloadTime);
                _log.info("Starting authn provider config reload, lastNotificationTime = {}, lastReloadTime = {}",
                        _lastNotificationTime, _lastReloadTime);
                try {
                    long timeNow = System.currentTimeMillis();
                    List<AuthnProvider> providers = getProvidersFromDB();
                    if (!bForceReload) {
                        // Its not a notified reload, check to see if anything changed
                        if (checkForUpdates(providers)) {
                            bForceReload = true;
                            _log.debug("Provider configuration changed, reloading providers");
                        } else {
                            _log.debug("Provider configuration not changed, skipping reload providers");
                        }
                    }
                    if (bForceReload) {
                        _authNProviders =
                                ImmutableAuthenticationProviders.getInstance(_dbClient,
                                        _coordinator, _localAuthenticationProvider,
                                        providers);
                        _lastReloadTime = timeNow;
                        updateLastKnown(providers);
                        _ldapProviderMonitor.setAuthnProviders(_authNProviders);
                        _log.info("Done authn provider config reload. lastReloadTime {}", _lastReloadTime);
                    }
                    // sleep and check for updates
                    _log.debug("Next check will run in {} min", _providerUpdateCheckMinutes);
                    sleep(_providerUpdateCheckMinutes * 60 * 1000);
                    // An update notification came in ... run again immediately
                } catch (Exception e) {
                    _log.error("Exception loading authentication provider configuration from db"
                            + ", will a retry in {} secs", _providerUpdateRetrySeconds, e);
                    // schedule a retry
                    try {
                        Thread.sleep(_providerUpdateRetrySeconds * 1000);
                    } catch (Exception ignore) {
                        _log.error("Got Exception in thread.sleep()", e);
                    }
                }
            }

        }
    }

    /**
     * Check if the given groupId is a local user group or
     * not. This is done by checking if the groupId contains "@domain"
     * suffix or not. If it contains the "@domain" suffix that is considered
     * to be group in the authnprovider.
     * 
     * @param groupId to be checked if it is local user group or not.
     * @return true if it is local user group otherwise false.
     */
    private boolean isUserGroup(String groupId) {
        boolean isUserGroup = false;
        if (StringUtils.isNotBlank(groupId)) {
            String[] groupParts = groupId.split("@");
            isUserGroup = (groupParts.length == 1);
        }
        return isUserGroup;
    }

    /**
     * Check the given group name matches the label of the active
     * user group in the db or not.
     * 
     * @param failureReason to be returned.
     * @param group to be checked if there is a valid user group
     *            available in the system that matches this label or not.
     * @return true if it is local user group found in the system
     *         otherwise false.
     */
    private boolean isValidUserGroup(ValidationFailureReason[] failureReason, String group) {
        List<UserGroup> objectList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, UserGroup.class,
                PrefixConstraint.Factory.getFullMatchConstraint(UserGroup.class, "label", group));
        if (CollectionUtils.isEmpty(objectList)) {
            // null means Exception has been thrown and error logged already, empty means no group found in LDAP/AD
            _log.error("UserGroup {} is not present in DB", group);
            failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
            return false;
        } else {
            _log.debug("UserGroup {} is valid", group);
            return true;
        }
    }
}
