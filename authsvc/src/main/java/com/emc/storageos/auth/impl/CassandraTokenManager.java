/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.auth.TokenManager;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ProxyToken;
import com.emc.storageos.db.client.model.RequestedTokenMap;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.Token;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.geomodel.TokenResponse;
import com.emc.storageos.security.authentication.CassandraTokenValidator;
import com.emc.storageos.security.geo.RequestedTokenHelper;
import com.emc.storageos.security.authentication.TokenKeyGenerator.TokenKeysBundle;
import com.emc.storageos.security.authentication.TokenOnWire;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.geo.TokenResponseBuilder;
import com.emc.storageos.security.geo.TokenResponseBuilder.TokenResponseArtifacts;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.netflix.astyanax.util.TimeUUIDUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * Cassandra based implementation of the token manager interface.
 * 
 */
public class CassandraTokenManager extends CassandraTokenValidator implements TokenManager {

    // warning: setting these two values to higher ones will increase
    // the memory footprint on dbsvc and authsvc
    private static final int DEFAULT_MAX_TOKENS_PER_USERID = 100;
    private static final int DEFAULT_MAX_TOKENS_FOR_PROXYUSER = 1000;

    private int _maxTokensPerUserId = DEFAULT_MAX_TOKENS_PER_USERID;
    private int _maxTokensForProxyUser = DEFAULT_MAX_TOKENS_FOR_PROXYUSER;
    static final String PROXY_USER = "proxyuser";
    private static final Logger _log = LoggerFactory.getLogger(CassandraTokenManager.class);
    private static final double TOKEN_WARNING_EIGHTY_PERCENT = 0.8;

    @Autowired
    private RequestedTokenHelper tokenMapHelper;

    public void setTokenMapHelper(RequestedTokenHelper tokenMapHelper) {
        this.tokenMapHelper = tokenMapHelper;
    }

    /**
     * Sets the maximum number of auth tokens for any user (except proxyuser)
     * which can exist in the system at any given time
     * 
     * @param max The maxTokensPerUserid to set.
     *            warning: increasing this value from the default will increase the memory
     *            footprint on dbsvc and authsvc
     */
    public void setMaxTokensPerUserId(int max) {
        _maxTokensPerUserId = max;
    }

    /**
     * Sets the maximum number of auth tokens for the proxyuser
     * which can exist in the system at any given time
     * 
     * @param max The maxTokensForProxyuser to set.
     *            warning: increasing this value from the default will increase the memory
     *            footprint on dbsvc and authsvc
     */
    public void setMaxTokensForProxyUser(int max) {
        _maxTokensForProxyUser = max;
    }

    /**
     * Executor for our house keeping tasks (deleting old tokens, and updating token signature keys
     */
    private final ScheduledExecutorService _tokenMgmtExecutor = Executors.newScheduledThreadPool(2);

    private class TokenKeysUpdater implements Runnable {
        @Override
        public void run() {
            // get all cached VDCid-TokenKeysBundle pairs (there is one entry for each from which a
            // token has been borrowed at least once
            HashMap<String, TokenKeysBundle> bundles = interVDCTokenCacheHelper.getAllCachedBundles();
            for (Entry<String, TokenKeysBundle> tokenKeyBndlEntry : bundles.entrySet()) {
                _log.info("Cached token key bundle for VDC found: {}.  Calling VDC to update...", tokenKeyBndlEntry.getKey());
                try {
                    // call geo's getToken with no token, and either one or two key ids, depending on what we
                    // have in the cached bundle (if keys have never been rotated, there would be only one key id
                    TokenResponse response = geoClientCacheMgt.getGeoClient(tokenKeyBndlEntry.getKey())
                            .getToken(null,
                                    tokenKeyBndlEntry.getValue().getKeyEntries().get(0),
                                    tokenKeyBndlEntry.getValue().getKeyEntries().size() == 2 ?
                                            tokenKeyBndlEntry.getValue().getKeyEntries().get(1) : null);
                    if (response != null) {
                        TokenResponseArtifacts artifacts = TokenResponseBuilder.parseTokenResponse(response);
                        if (artifacts.getTokenKeysBundle() != null) {
                            _log.info("Remote VDC sent new keys for {}.  Caching them...", tokenKeyBndlEntry.getKey());
                            interVDCTokenCacheHelper.cacheForeignTokenAndKeys(artifacts, tokenKeyBndlEntry.getKey());
                        } else {
                            _log.info("No updated key bundles from remote VDC {}.  Keys are up to date", tokenKeyBndlEntry.getKey());
                        }
                    } else {
                        _log.error("Null response when trying to get updated keys.  It's possible remote vdc is not reachable.");
                    }
                } catch (Exception ex) {
                    _log.error("Could not update token keys", ex);
                }
            }
            _log.info("Done running the key refresh thread.  There were {} cached VDC keys bundles", bundles.size());
        }
    }

    private class ExpiredTokenCleaner implements Runnable {
        final static String TOKEN_CLEANER_LOCK = "token_cleaner";
        final static long MIN_TO_MICROSECS = 60 * 1000 * 1000;

        // get tokens older than idle time from index
        private URIQueryResultList getOldTokens() {
            URIQueryResultList list = new URIQueryResultList();
            long timeStartMarker = TimeUUIDUtils.getMicrosTimeFromUUID(TimeUUIDUtils.getUniqueTimeUUIDinMicros())
                    - (_maxLifeValuesHolder.getMaxTokenIdleTimeInMins() * MIN_TO_MICROSECS);
            _dbClient.queryByConstraint(
                    DecommissionedConstraint.Factory.getDecommissionedObjectsConstraint(
                            Token.class, "indexed", timeStartMarker), list);
            return list;
        }

        @Override
        public void run() {
            InterProcessLock lock = null;
            try {
                _log.info("Starting token cleanup executor ...");
                lock = _coordinator.getLock(TOKEN_CLEANER_LOCK);
                lock.acquire();
                _log.info("Got token cleaner lock ...");
                int deletedCount = 0;
                int nLongLiveTokens = 0;  // Come from TokenIndex;
                try {
                    List<URI> tokens = getOldTokens();
                    Iterator<Token> tokenIterator = _dbClient.queryIterativeObjects(Token.class, tokens);
                    while (tokenIterator.hasNext()) {
                        nLongLiveTokens++;
                        Token tokenObj = tokenIterator.next();
                        if (tokenObj == null) {
                            _log.warn("Inconsistency found between Token and TokenIndex.");
                            continue;
                        }
                        if (!checkExpiration(tokenObj, false)) {
                            deletedCount++;
                            cleanUpRequestedTokenMap(tokenObj);
                            // TODO:
                            // Streamline the following code paths: Right now:
                            // 1 - AuthenticationResource.logout() calls deleteToken() which calls deleteTokenInternal(), then
                            // notifyExternalVDCs which
                            // still needs the map.
                            // 2 - checkExpiration calls deleteTokenInternal(). So if we put cleanUpRequestedTokenMap() in
                            // deleteTokenInternal(),
                            // it would make notifyExternalVDCs miss notifications in the #1 case above.
                            // * For this reason, cleanUpRequestedTokenMap is being called separately in the various paths.
                            // * look to see if this can be done more cleanly (moving cleanupRTkMap to somewhere common between the various
                            // places
                            // where token(s) are getting deleted)
                        }
                    }
                } catch (DatabaseException ex) {
                    _log.error("DatabaseException in token cleanup executor: ", ex);
                }
                _log.info("Done token cleanup executor, long live tokens {}, deleted {} tokens", nLongLiveTokens, deletedCount);
            } catch (Exception e) {
                _log.warn("Unexpected exception during db maintenance", e);
            } finally {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (Exception e) {
                        _log.warn("Unexpected exception unlocking repair lock", e);
                    }
                }
            }
        }
    }

    /**
     * Removes the RequestedTokenMap associated with the passed in token if it exists.
     * 
     * @param tokenObj
     */
    private void cleanUpRequestedTokenMap(Token tokenObj) {
        RequestedTokenMap map = tokenMapHelper.getTokenMap(tokenObj.getId().toString());
        if (map != null) {
            _dbClient.removeObject(map);
            _log.info("A token had a stale RequestedTokenMap.  Deleting.");
        } else {
            _log.info("No RequestedTokenMap for token to be deleted.");
        }
    }

    /**
     * initializer, startup the background expired token deletion thread
     * and key updater thread (no op unless multi vdc)
     */
    public void init() {
        // Token cleaner thread
        _tokenMgmtExecutor.scheduleWithFixedDelay(
                new ExpiredTokenCleaner(), 1, _maxLifeValuesHolder.getMaxTokenIdleTimeInMins(), TimeUnit.MINUTES);
        // Token keys updater thread
        _tokenMgmtExecutor.scheduleWithFixedDelay( // update every 20 minutes
                new TokenKeysUpdater(), 1, FOREIGN_TOKEN_KEYS_BUNDLE_REFRESH_RATE_IN_MINS, TimeUnit.MINUTES);
    }

    /**
     * run the cleanup now
     */
    public void runCleanupNow() {
        new ExpiredTokenCleaner().run();
    }

    /**
     * create a new token with the user info
     * 
     * @param user
     * @return
     */
    private Token createNewToken(StorageOSUserDAO user) {
        Token token = new Token();
        token.setId(URIUtil.createId(Token.class));
        token.setUserId(user.getId()); // relative index, Id of the userDAO record
        long timeNow = getCurrentTimeInMins();
        token.setIssuedTime(timeNow);
        token.setLastAccessTime(timeNow);
        token.setExpirationTime(timeNow + (_maxLifeValuesHolder.getMaxTokenLifeTimeInMins()));
        token.setIndexed(true);
        _dbClient.persistObject(token);
        return token;
    }

    /**
     * Persist/Update the StorageOSUserDAO record
     * generates a new token or reuses an existing token.
     * 
     * @return token as a String
     */
    @Override
    public String getToken(StorageOSUserDAO userDAO) {
        try {
            // always use lower case username for comparing/saving to db
            userDAO.setUserName(userDAO.getUserName().toLowerCase());
            // find an active user record, if there is one with an active token
            List<StorageOSUserDAO> userRecords = getUserRecords(userDAO.getUserName());
            StorageOSUserDAO user = updateDBWithUser(userDAO, userRecords);
            // do we have a user account to use?
            if (user == null) {
                // No, create one
                userDAO.setId(URIUtil.createId(StorageOSUserDAO.class));
                _dbClient.persistObject(userDAO);
                user = userDAO;
            } else {
                // check count
                List<Token> tokensForUserId = getTokensForUserId(user.getId());
                int maxTokens = user.getUserName().equalsIgnoreCase(PROXY_USER) ?
                        _maxTokensForProxyUser : _maxTokensPerUserId;
                double alertTokensSize = (maxTokens * TOKEN_WARNING_EIGHTY_PERCENT);
                if (tokensForUserId.size() >= maxTokens) {
                    throw APIException.unauthorized.maxNumberOfTokenExceededForUser();
                } else if (tokensForUserId.size() == (int) alertTokensSize) {
                    _log.warn("Prior to creating new token, user {} had {} tokens.",
                            user.getUserName(), tokensForUserId.size());
                }

            }
            return _tokenEncoder.encode(TokenOnWire.createTokenOnWire(createNewToken(user)));
        } catch (DatabaseException ex) {
            _log.error("Exception while persisting user information {}", userDAO.getUserName(), ex);
        } catch (SecurityException e) {
            _log.error("Token encoding exception. ", e);
        }
        return null;
    }

    /**
     * Gets a proxy token for the given user
     * If a proxy token for the given user already exists, it will be reused
     * 
     * @return proxy-token
     */
    @Override
    public String getProxyToken(StorageOSUserDAO userDAO) {
        InterProcessLock userLock = null;
        try {
            userLock = _coordinator.getLock(userDAO.getUserName());
            if (userLock == null) {
                _log.error("Could not acquire lock for user: {}", userDAO.getUserName());
                throw SecurityException.fatals.couldNotAcquireLockForUser(userDAO.getUserName());
            }
            userLock.acquire();

            // Look for proxy tokens based on that username.
            // If any is found, use that. Else, create a new one.
            ProxyToken proxyToken = getProxyTokenForUserName(userDAO.getUserName());
            if (proxyToken != null) {
                _log.debug("Found proxy token {} for user {}.  Reusing...", proxyToken.getId(), userDAO.getUserName());
                return _tokenEncoder.encode(TokenOnWire.createTokenOnWire(proxyToken));
            }
            // No proxy token found for this user. Create a new one.

            // Create the actual proxy token
            ProxyToken pToken = new ProxyToken();
            pToken.setId(URIUtil.createId(ProxyToken.class));
            pToken.addKnownId(userDAO.getId());
            pToken.setUserName(userDAO.getUserName());
            pToken.setZoneId("zone1"); // for now
            pToken.setIssuedTime(getCurrentTimeInMins());
            pToken.setLastValidatedTime(getCurrentTimeInMins());
            _dbClient.persistObject(pToken);
            return _tokenEncoder.encode(TokenOnWire.createTokenOnWire(pToken));
        } catch (DatabaseException ex) {
            _log.error("DatabaseException while persisting proxy token", ex);
        } catch (SecurityException ex) {
            _log.error("Proxy Token encoding exception. ", ex);
        } catch (Exception ex) {
            _log.error("Could not acquire lock while trying to get a proxy token.", ex);
        } finally {
            try {
                if (userLock != null) {
                    userLock.release();
                }
            } catch (Exception ex) {
                _log.error("Unable to release proxytoken creation lock", ex);
            }
        }
        return null;
    }

    /**
     * Remove token from database if valid.
     */
    @Override
    public void deleteToken(String tokenIn) {
        try {
            if (tokenIn == null) {
                _log.error("Null token passed for deletion");
                return;
            }
            URI tkId = _tokenEncoder.decode(tokenIn).getTokenId();
            Token verificationToken = _dbClient.queryObject(Token.class, tkId);
            if (verificationToken == null) {
                _log.error("Could not fetch token from the database: {}", tkId);
                return;
            }
            deleteTokenInternal(verificationToken);
        } catch (DatabaseException ex) {
            throw SecurityException.fatals.databseExceptionDuringTokenDeletion(tokenIn,
                    ex);
        } catch (SecurityException e) {
            _log.error("Token decoding exception during deleteToken.", e);
        }
    }

    /**
     * Delete all tokens belonging to the user and mark all the user records for this user for deletion.
     */
    @Override
    public void deleteAllTokensForUser(String userName, boolean includeProxyTokens) {
        try {
            List<StorageOSUserDAO> userRecords = getUserRecords(userName.toLowerCase());
            for (StorageOSUserDAO userRecord : userRecords) {
                List<Token> tokensToDelete = getTokensForUserId(userRecord.getId());
                for (Token token : tokensToDelete) {
                    _log.info("Removing token {} using userDAO {} for username {}",
                            new String[] { token.getId().toString(), userRecord.getId().toString(), userName });
                    _dbClient.removeObject(token);
                    cleanUpRequestedTokenMap(token);
                }
                // making proxy token deletion optional
                List<ProxyToken> pTokensToDelete = getProxyTokensForUserId(userRecord.getId());
                if (includeProxyTokens) {
                    for (ProxyToken token : pTokensToDelete) {
                        _log.info("Removing proxy token {} using userDAO {} for username {}",
                                new String[] { token.getId().toString(), userRecord.getId().toString(), userName });
                        _dbClient.removeObject(token);
                    }
                    _log.info("Marking for deletion: user record {} for username {}",
                            userRecord.getId().toString(), userName);
                    _dbClient.markForDeletion(userRecord);
                } else if (pTokensToDelete.isEmpty()) {
                    _log.info("No proxy tokens found. Marking for deletion: user record {} for username {}",
                            userRecord.getId().toString(), userName);
                    _dbClient.markForDeletion(userRecord);
                }
            }
        } catch (DatabaseException ex) {
            throw SecurityException.fatals.exceptionDuringTokenDeletionForUser(userName,
                    ex);
        }
    }

    @Override
    public StorageOSUserDAO updateDBWithUser(final StorageOSUserDAO userDAO,
            final List<StorageOSUserDAO> userRecords) {
        StorageOSUserDAO user = null;
        for (StorageOSUserDAO record : userRecords) {
            if (!record.getInactive()) {
                // update the record, most of the cases this is a NO-OP
                // because user info does not change much
                record.updateFrom(userDAO);
                user = record;
                _dbClient.persistObject(record);
            }
        }
        return user;
    }

    @Override
    public int getMaxTokenLifeTimeInSecs() {
        return _maxLifeValuesHolder.getMaxTokenLifeTimeInMins() * 60;
    }
}
