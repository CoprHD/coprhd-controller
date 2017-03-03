/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.authentication;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BaseToken;
import com.emc.storageos.db.client.model.ProxyToken;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.Token;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.geomodel.TokenResponse;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.InterVDCTokenCacheHelper;
import com.emc.storageos.security.geo.TokenResponseBuilder;
import com.emc.storageos.security.geo.TokenResponseBuilder.TokenResponseArtifacts;

import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.springframework.util.CollectionUtils;

/**
 * Cassandra based implementation of the TokenValidator interface
 * Token record has: rowid || fields | alternateId ( rowid of a StorageOSUserDAO record)
 * StorageOSUserDAO record has: rowid || fields | alternatId (username)
 */
public class CassandraTokenValidator implements TokenValidator {
    public static final int FOREIGN_TOKEN_KEYS_BUNDLE_REFRESH_RATE_IN_MINS = 20;

    private static final Logger _log = LoggerFactory.getLogger(CassandraTokenValidator.class);

    @Autowired
    protected TokenMaxLifeValuesHolder _maxLifeValuesHolder;

    protected static final long MIN_TO_MSECS = 60 * 1000;

    @Autowired
    protected DbClient _dbClient;

    @Autowired
    protected TokenEncoder _tokenEncoder;

    @Autowired
    protected CoordinatorClient _coordinator;

    @Autowired
    protected InterVDCTokenCacheHelper interVDCTokenCacheHelper;

    @Autowired
    protected GeoClientCacheManager geoClientCacheMgt;

    /**
     * Setter for coordinator client. Needed for testing. Otherwise
     * gets autowired.
     * 
     * @param c
     */
    public void setCoordinator(CoordinatorClient c) {
        _coordinator = c;
    }

    public void setTokenEncoder(TokenEncoder e) {
        _tokenEncoder = e;
    }

    public void setDbClient(DbClient dbclient) {
        _dbClient = dbclient;
    }

    public void setTokenMaxLifeValuesHolder(TokenMaxLifeValuesHolder holder) {
        _maxLifeValuesHolder = holder;
    }

    public void setInterVDCTokenCacheHelper(InterVDCTokenCacheHelper helper) {
        interVDCTokenCacheHelper = helper;
    }

    /**
     * get current time in minutes
     * 
     * @return
     */
    public static long getCurrentTimeInMins() {
        return System.currentTimeMillis() / (MIN_TO_MSECS);
    }

    /**
     * Get all user DAO records from DB for a given user
     * 
     * @param userName
     * @return List of StorageOSUserDAO
     */
    public List<StorageOSUserDAO> getUserRecords(String userName) {
        URIQueryResultList users = new URIQueryResultList();
        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getUserIdsByUserName(userName), users);
        List<URI> userURIs = new ArrayList<URI>();
        for (Iterator<URI> it = users.iterator(); it.hasNext();) {
            userURIs.add(it.next());
        }
        return _dbClient.queryObject(StorageOSUserDAO.class, userURIs);
    }

    /**
     * Get all tickets referring to a given User DAO record ID
     * 
     * @param userId
     * @param tokenTypeProxy: true if looking for proxy tokens. False for regular tokens
     * @return
     */
    private List<URI> getTokensForUserId(URI userId, boolean tokenTypeProxy) {
        URIQueryResultList tokens = new URIQueryResultList();
        List<URI> tokenURIs = new ArrayList<URI>();
        if (!tokenTypeProxy) {
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getUserIdTokenConstraint(userId), tokens);
        } else {
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getUserIdProxyTokenConstraint(userId), tokens);
        }
        for (Iterator<URI> it = tokens.iterator(); it.hasNext();) {
            tokenURIs.add(it.next());
        }

        return tokenURIs;
    }

    /**
     * Get proxy tokens based on a username
     * 
     * @param username
     * @return the proxy token for that user if it exists.
     */
    protected ProxyToken getProxyTokenForUserName(String username) {
        URIQueryResultList tokens = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint
                .Factory.getProxyTokenUserNameConstraint(username), tokens);
        List<URI> uris = new ArrayList<URI>();
        for (Iterator<URI> it = tokens.iterator(); it.hasNext();) {
            uris.add(it.next());
        }

        List<ProxyToken> toReturn = _dbClient.queryObject(ProxyToken.class, uris);
        if (CollectionUtils.isEmpty(toReturn)) {
            _log.info("No proxy token found for user {}", username);
            return null;
        }

        return toReturn.get(0);
    }

    /**
     * Returns all regular tokens for User id
     * 
     * @param userId
     * @return
     */
    protected List<Token> getTokensForUserId(URI userId) {
        List<URI> tokenURIs = getTokensForUserId(userId, false);
        return _dbClient.queryObject(Token.class, tokenURIs);
    }

    /**
     * Returns all proxy tokens for User id
     * 
     * @param userId
     * @return
     */
    protected List<ProxyToken> getProxyTokensForUserId(URI userId) {
        List<URI> tokenURIs = getTokensForUserId(userId, true);
        return _dbClient.queryObject(ProxyToken.class, tokenURIs);
    }

    /**
     * Delete the given token from db, if this is last token referring the userDAO,
     * and there are no proxy token associated, mark the userDAO for deletion
     * 
     * @param token
     */
    protected void deleteTokenInternal(Token token) {
        URI userId = token.getUserId();
        _dbClient.removeObject(token);
        List<Token> tokens = getTokensForUserId(userId);
        List<ProxyToken> pTokens = getProxyTokensForUserId(userId);
        if (CollectionUtils.isEmpty(tokens) && CollectionUtils.isEmpty(pTokens)) {
            _log.info("There are no more tokens referring to the user id {}, marking it inactive");
            StorageOSUserDAO userDAO = _dbClient.queryObject(StorageOSUserDAO.class, userId);
            _dbClient.markForDeletion(userDAO);
        }
    }

    /**
     * Queries the remote VDC for token and userdao objects
     * 
     * @param tw TokenOnWire object
     * @param rawToken the rawToken to send to the remote vdc
     * @return
     */
    private StorageOSUserDAO getForeignToken(TokenOnWire tw, String rawToken) {
        StorageOSUserDAO userFromCache = this.foreignTokenCacheLookup(tw);
        if (userFromCache != null) {
            return userFromCache;
        }
        try {
            String shortVDCid = URIUtil.parseVdcIdFromURI(tw.getTokenId());
            TokenResponse response = geoClientCacheMgt.getGeoClient(shortVDCid).
                    getToken(rawToken, null, null);
            if (response != null) {
                TokenResponseArtifacts artifacts = TokenResponseBuilder.parseTokenResponse(response);
                _log.info("Got username for foreign token: {}", artifacts.getUser().getUserName());
                _log.debug("Got token object: {}", artifacts.getToken().getId().toString());
                interVDCTokenCacheHelper.cacheForeignTokenAndKeys(artifacts, shortVDCid);
                return artifacts.getUser();
            } else {
                _log.error("Null response from getForeignToken call.  It's possible remote vdc is not reachable.");
            }
        } catch (Exception e) {
            _log.error("Could not validate foreign token ", e);
        }
        return null;
    }

    /**
     * Validate a token. If valid, return the corresponding user record.
     * The passed in token, is an id to the record in the db, is used to query the object from db
     * The userId field in token object is used to create the user information.
     * 
     * If the token is a foreign token, the cache will be queried and a call to the remote VDC
     * will be made if the cache did not produce the desired token.
     */
    @Override
    public StorageOSUserDAO validateToken(String tokenIn) {
        if (tokenIn == null) {
            _log.error("token is null");
            return null;
        }

        TokenOnWire tw = _tokenEncoder.decode(tokenIn);
        String vdcId = URIUtil.parseVdcIdFromURI(tw.getTokenId());
        // If this isn't our token, go get it from the remote vdc
        if (vdcId != null && !tw.isProxyToken() &&
                !VdcUtil.getLocalShortVdcId().equals(vdcId)) {
            return getForeignToken(tw, tokenIn);
        }

        return resolveUser(fetchTokenLocal(tw));
    }

    /**
     * Looks in the cache for token/user record. Returns null if not found or found but cache expired
     * 
     * @param tw
     * @return user record
     */
    private StorageOSUserDAO foreignTokenCacheLookup(TokenOnWire tw) {
        BaseToken bToken = fetchTokenLocal(tw);
        if (bToken == null || !Token.class.isInstance(bToken)) {
            _log.info("Token: no hit from cache");
            return null;
        }
        Token token = (Token) bToken;
        Long expirationTime = token.getCacheExpirationTime();
        if (expirationTime != null && expirationTime > getCurrentTimeInMins()) {
            StorageOSUserDAO user = resolveUser(token);
            _log.info("Got user from cached token: {}", user != null ? user.getUserName() : "no hit from cache");
            return user;
        }
        _log.info("Cache expired for foreign token {}", token.getId());
        return null;
    }

    /**
     * Check to see if the token passed in is still usable, if not, delete it from db and return false
     * if still usable, update the lastAccessTime
     * 
     * @param tokenObj Token object
     * @param updateLastAccess if true, will update the last accessed timestamp if needed
     * @return True if the token is good, False otherwise
     */
    protected boolean checkExpiration(Token tokenObj, boolean updateLastAccess) {
        if (!tokenObj.getInactive()) {
            long timeNow = getCurrentTimeInMins();
            long timeLastAccess = tokenObj.getLastAccessTime();
            long timeIdleTimeExpiry = timeLastAccess
                    + (_maxLifeValuesHolder.getMaxTokenIdleTimeInMins()) + (_maxLifeValuesHolder.getTokenIdleTimeGraceInMins());
            if (tokenObj.getExpirationTime() != null &&
                    timeIdleTimeExpiry > timeNow &&
                    tokenObj.getExpirationTime() > timeNow) {
                // update Last access time, if we haven't in the last TOKEN_IDLE_TIME_GRACE_IN_MINS
                // this will save us some extra db writes
                if (updateLastAccess) {
                    long nextLastAccessUpdate = timeLastAccess + (_maxLifeValuesHolder.getTokenIdleTimeGraceInMins());
                    if (nextLastAccessUpdate <= timeNow) {
                        tokenObj.setLastAccessTime(timeNow);
                        try {
                            _dbClient.persistObject(tokenObj);
                        } catch (DatabaseException ex) {
                            _log.error("failed updating last access time for token {}", tokenObj.getId());
                        }
                    }
                }
                return true;
            }
            _log.debug("token expired: {}, now {}, lastAccess {}, idle expiry {}, expiry {}",
                    new String[] { tokenObj.getId().toString(), "" + timeNow,
                            "" + tokenObj.getLastAccessTime(), "" + timeIdleTimeExpiry,
                            "" + tokenObj.getExpirationTime() });
        }
        // we are here because token is either expired or inactive,
        // remove the token and return false
        try {
            deleteTokenInternal(tokenObj);
        } catch (DatabaseException ex) {
            _log.error("exception deleting token {}", tokenObj.getId(), ex);
        }
        return false;
    }

    /**
     * Fetches a token without consideration for cache expiration
     */
    @Override
    public BaseToken verifyToken(String tokenIn) {
        if (tokenIn == null) {
            _log.error("token is null");
            return null;
        }
        TokenOnWire tw = _tokenEncoder.decode(tokenIn);
        return this.fetchTokenLocal(tw);
    }

    /**
     * Retrieves a token and checks expiration
     * 
     * @param tw
     * @return
     */
    private BaseToken fetchTokenLocal(TokenOnWire tw) {
        BaseToken verificationToken = null;
        URI tkId = tw.getTokenId();
        if (!tw.isProxyToken()) {
            verificationToken = _dbClient.queryObject(Token.class, tkId);
            if (null != verificationToken && !checkExpiration(((Token) verificationToken), true)) {
                _log.warn("Token found in database but is expired: {}", verificationToken.getId());
                return null;
            }
        } else {
            verificationToken = _dbClient.queryObject(ProxyToken.class, tkId);
            if (null != verificationToken && !checkExpiration((ProxyToken) verificationToken)) {
                _log.warn("ProxyToken found in database but is expired: {}", verificationToken.getId());
                return null;
            }
        }
        if (verificationToken == null) {
            _log.error("Could not find token with id {} for validation", tkId);
        }
        return verificationToken;
    }

    /**
     * Gets a userDAO record from a token or proxytoken
     */
    @Override
    public StorageOSUserDAO resolveUser(BaseToken token) {
        if (token == null) {
            return null;
        }
        URI userId = null;
        // Skip expiration verification for proxy tokens.
        // verify it is still valid, if not remove it from db and send back null
        boolean isProxy = token instanceof ProxyToken;
        if (isProxy) {
            userId = ((ProxyToken) token).peekLastKnownId();
        } else {
            userId = ((Token) token).getUserId();
        }
        StorageOSUserDAO userDAO = _dbClient.queryObject(StorageOSUserDAO.class, userId);
        if (userDAO == null) {
            _log.error("No user record found or userId: {}", userId.toString());
            return null;
        }
        return userDAO;
    }

    /*
     * Check to see if the proxy token passed in is still usable, if not, delete it from
     * db and return false.
     * 
     * @param tokenObj
     * ProxyToken object
     * 
     * @return True if the token is good, False otherwise
     */
    protected boolean checkExpiration(ProxyToken tokenObj) {

        if (tokenObj.getInactive()) {
            return false;
        }
        long timeNow = getCurrentTimeInMins();
        Long lastValidatedTime = tokenObj.getLastValidatedTime();
        // if this is a proxy token from before v2, then this might be null. New proxy
        // tokens should have this value set.
        if (lastValidatedTime == null) {
            lastValidatedTime =
                    timeNow - _maxLifeValuesHolder.getMaxTokenLifeTimeInMins();
        }
        long lastValidationTimeExpiry =
                lastValidatedTime + _maxLifeValuesHolder.getMaxTokenLifeTimeInMins();
        if (lastValidationTimeExpiry <= timeNow) {
            try {
                Validator.refreshUser(tokenObj.getUserName());
            } catch (APIException e) { // LDAP could not find the user
                _log.error(e.getMessage(), e);
                return false;
            } catch (Exception e) {
                _log.error(e.getMessage(), e);
                return true;
            }
            tokenObj.setLastValidatedTime(timeNow);
            _dbClient.persistObject(tokenObj);
        }
        return true;
    }
}
