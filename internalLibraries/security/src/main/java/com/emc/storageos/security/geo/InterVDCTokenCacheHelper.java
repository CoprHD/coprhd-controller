/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 *  software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.geo;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.Token;
import com.emc.storageos.security.SerializerUtils;
import com.emc.storageos.security.authentication.CassandraTokenValidator;
import com.emc.storageos.security.authentication.TokenKeyGenerator.TokenKeysBundle;
import com.emc.storageos.security.authentication.TokenMaxLifeValuesHolder;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.geo.TokenResponseBuilder.TokenResponseArtifacts;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * This class provides utility methods to cache tokens, user records and token signature keys
 */
public class InterVDCTokenCacheHelper {

    private static final Logger log = LoggerFactory.getLogger(InterVDCTokenCacheHelper.class);
    private static final String FOREIGN_TOKEN_KEYS_BUNDLE_CONFIG = "ForeignTokenKeysBundleConfig";
    private static final String FOREIGN_TOKEN_KEYS_BUNDLE_KEYID = "ForeignTokenKeysBundleKeyId";
    private static final long MIN_TO_MSECS = 60 * 1000;
    private static final String FOREIGN_TOKEN_BUNDLE_CONFIG_LOCK = "ForeignTokenConfigLock";
  
    @Autowired
    private CoordinatorClient coordinator;
    
    @Autowired
    private DbClient dbClient;
    
    @Autowired
    protected TokenMaxLifeValuesHolder maxLifeValuesHolder;
    
    public void setCoordinator(CoordinatorClient c) {
        this.coordinator = c;
    }

    public void setDbClient(DbClient client) {
        this.dbClient = client;
    }

    public void setMaxLifeValuesHolder(TokenMaxLifeValuesHolder valueHolder) {
        this.maxLifeValuesHolder = valueHolder;
    }

    
    /**
     * saves token artifacts to the cache.  The artifacts can be the token & user record, and token key ids.
     * Token key ids (TokenKeyBundle) goes to zk.  Token and user record goes to cassandra.
     * @param artifacts
     * @param vdcID
     */
    public void cacheForeignTokenAndKeys(TokenResponseArtifacts artifacts, String vdcID) {
        Token token = artifacts.getToken();
        StorageOSUserDAO user = artifacts.getUser();
        TokenKeysBundle bundle = artifacts.getTokenKeysBundle();
        if (token != null && user != null) {
            cacheForeignTokenArtifacts(token, user);
        }
        if (bundle != null) {
            saveTokenKeysBundle(vdcID, bundle);
        }
    }
    
    
    /**
     * Saves the token and user dao records to the db.  Set the cache expiration time
     * to 10 minutes or time left on the token, whichever is sooner.
     * Note: this method assumes validity of the token (expiration) has been checked
     * @param t
     * @param user
     * @param now current time in minutes
     */
    private synchronized void cacheForeignTokenArtifacts(final Token token, final StorageOSUserDAO user) {
        long now =  System.currentTimeMillis() / (MIN_TO_MSECS);
        InterProcessLock tokenLock = null;
        try {
            tokenLock = coordinator.getLock(token.getId().toString());
            if(tokenLock == null) {
                log.error("Could not acquire lock for token caching");
                throw SecurityException.fatals.couldNotAcquireLockTokenCaching();
            }
            tokenLock.acquire();
            StorageOSUserDAO userToPersist = dbClient.queryObject(StorageOSUserDAO.class, user.getId());
            userToPersist = (userToPersist == null) ? new StorageOSUserDAO() :  userToPersist;
            userToPersist.setAttributes(user.getAttributes());
            userToPersist.setCreationTime(user.getCreationTime());
            userToPersist.setDistinguishedName(user.getDistinguishedName());
            userToPersist.setGroups(user.getGroups());
            userToPersist.setId(user.getId());
            userToPersist.setIsLocal(user.getIsLocal());
            userToPersist.setTenantId(user.getTenantId());
            userToPersist.setUserName(user.getUserName());
            dbClient.persistObject(userToPersist);

            Token tokenToPersist = dbClient.queryObject(Token.class, token.getId());
            tokenToPersist = (tokenToPersist == null) ? new Token() :  tokenToPersist;
            if ((token.getExpirationTime() - now) > maxLifeValuesHolder.getForeignTokenCacheExpirationInMins() ) { 
                tokenToPersist.setCacheExpirationTime(now +maxLifeValuesHolder.getForeignTokenCacheExpirationInMins());          
            }   else {
                tokenToPersist.setCacheExpirationTime(token.getExpirationTime());
            }            
            tokenToPersist.setId(token.getId());
            tokenToPersist.setUserId(user.getId()); // relative index, Id of the userDAO record
            tokenToPersist.setIssuedTime(token.getIssuedTime());
            tokenToPersist.setLastAccessTime(now);
            tokenToPersist.setExpirationTime(token.getExpirationTime());
            tokenToPersist.setIndexed(true); 
            tokenToPersist.setZoneId(token.getZoneId());
            dbClient.persistObject(tokenToPersist);
            log.info("Cached user {} and token", user.getUserName());
        } catch (Exception ex) {
            log.error("Could not acquire lock while trying to get a proxy token.", ex);
        } 
        finally {
            try {
                if(tokenLock != null) {
                    tokenLock.release();
                }
            } catch(Exception ex) {
                log.error("Unable to release token caching lock", ex);
            }
        }
    }
    
    
    private static ConcurrentHashMap<String, TokenKeysBundle> foreignTokenKeysMap = 
            new ConcurrentHashMap<String, TokenKeysBundle>();
    
    /**
     * Reads local cache to find the key for the passed in vdcid.  If not found in cache,
     * causes a re-read from ZK and updates the cache.
     * @param vdcID
     * @param keyId
     * @return
     */
    public SecretKey getForeignSecretKey(String vdcID, String keyId) {
        SecretKey toReturn = null;
        // try to get the bundle from cached map
        TokenKeysBundle bundle = foreignTokenKeysMap.get(vdcID);
        if (bundle != null) {
            toReturn = bundle.getKey(keyId);                    
        }
        // still nothing, try to reread from zk.
        if (toReturn == null) {
            try {
                bundle = readTokenKeysBundle(vdcID);
            } catch (Exception e) {
                log.error("Exception while reading foreign key bundle for vdcid {}", vdcID);
            }
            if (bundle != null) {
                toReturn = bundle.getKey(keyId);                   
            }
        }
        return toReturn;
    }
 
    /**
     * Retrieves all the cached TokenKeysBundles for VDCs from which tokens were borrowed
     * @return VDCid->bundle map
     */
    
    public HashMap<String, TokenKeysBundle> getAllCachedBundles() {
        HashMap<String, TokenKeysBundle> bundleToReturn = new HashMap<String, TokenKeysBundle>(); 
        Configuration config = coordinator.queryConfiguration(FOREIGN_TOKEN_KEYS_BUNDLE_CONFIG, 
                FOREIGN_TOKEN_KEYS_BUNDLE_KEYID);
        if (config == null) {
            log.info("No cached foreign token keys");
            return bundleToReturn;
        }
        Map<String, String> bundles = config.getAllConfigs(true);
        log.info("Key bundles retrieved: {}", bundles.size());

        for (Entry<String, String> e : bundles.entrySet()) {
            TokenKeysBundle bundle;
            try {
                bundle = (TokenKeysBundle) SerializerUtils.deserialize(e.getValue());
            } catch (Exception ex) {
                log.error("Could not deserialize token keys bundle", ex);
                return null;
            }
            bundleToReturn.put(e.getKey(), bundle);
        }
        return bundleToReturn;
    }
    
    /**
     * Checks if the requested id falls within reasonnable range of the currently
     * known cached ids.
     * Reasonnable means: not newer than the current key by more than 1 key rotation 
     * Not older than the previous key
     * @param bundle the cached bundle to check against
     * @param reqId the key id that came from the incoming token
     * @return
     */
    public boolean sanitizeRequestedKeyIds(TokenKeysBundle bundle, String reqId) {
        int sz = bundle.getKeyEntries().size();
        if (sz == 0) {
            log.info("There is no cached bundle to compare against.");
            return true;
        }
        String upperBound = sz == 1 ? null : bundle.getKeyEntries().get(1);
        String lowerBound = bundle.getKeyEntries().get(0);
        long lowKey = Long.parseLong(lowerBound);
        long highKey = upperBound == null ? 0 :  Long.parseLong(upperBound);
        long requestedKeyId = Long.parseLong(reqId); 
        long rotationInterval = maxLifeValuesHolder.computeRotationTimeInMSecs();
        // because there is a 20 minutes delay between key updates, we add a 20 minutes
        // grace period
        rotationInterval += CassandraTokenValidator.
                FOREIGN_TOKEN_KEYS_BUNDLE_REFRESH_RATE_IN_MINS * 60 * 1000;
        
        // case where there's no upper bound, we only have one bound to go by.
        // We have to check that the requested key is not in the past and not
        // more than one rotation in the future
        if (upperBound == null) {
            if (requestedKeyId < lowKey || requestedKeyId > lowKey + rotationInterval) {
                log.error("One bound.  Key id {} is not legitimate for query", requestedKeyId);
                return false;
            }
            log.info("One bound. Key id {} is legitimate for query", requestedKeyId);
            return true;
        }
        
        // case where there is a lower bound and upper bound.  Here, we need
        // to check that the requested key is not in the past, not between the bounds
        // (this would be wrong because it should have matched one of the bounds)
        // and no more than 1 rotation + 20 minutes in the future.
        if (requestedKeyId < lowKey || 
                (requestedKeyId > lowKey && requestedKeyId < highKey) ||
                requestedKeyId > highKey + rotationInterval) {
            log.error("Two bounds.  Key id {} is not legitimate for query", requestedKeyId);
            return false;
        }
        log.info("Two bounds.  Key id {} is legitimate for query", requestedKeyId);
        return true;
    }
    
    /**
     * Gets the TokenKeysBundle for the given vdcid
     * @param VDCid
     * @return
     */
    public TokenKeysBundle getTokenKeysBundle(String VDCid) {
        return foreignTokenKeysMap.get(VDCid);
    }
    
    /**
     * Reads from zk to get a TokenKeyBundle for the passed in vdc id.
     * Updates the local cache map if found.
     * @param vdcID
     * @return
     * @throws Exception
     */
    private TokenKeysBundle readTokenKeysBundle(String vdcID) throws Exception {
        Configuration config = coordinator.queryConfiguration(FOREIGN_TOKEN_KEYS_BUNDLE_CONFIG, 
                FOREIGN_TOKEN_KEYS_BUNDLE_KEYID);
        if (config == null || config.getConfig(vdcID) == null) {
            log.info("Foreign token keys bundle not found for vdcid {}", vdcID);
            return null;
        }
        String serializedBundle = config.getConfig(vdcID);
        log.debug("Got foreign token keys bundle from coordinator: {}", vdcID);
        TokenKeysBundle bundle =  (TokenKeysBundle) SerializerUtils.deserialize(serializedBundle);
        foreignTokenKeysMap.put(vdcID, bundle);
        return bundle;
    }
    
    /**
     * Stores the token key bundle in cache (zookeeper path based on vdcid)
     * Locks on vdcid for the write.
     * @param vdcID
     * @param bundle
     */
    private synchronized void saveTokenKeysBundle(String vdcID, TokenKeysBundle bundle) {
        InterProcessLock tokenBundleLock = null;
        try {
            tokenBundleLock = coordinator.getLock(FOREIGN_TOKEN_BUNDLE_CONFIG_LOCK);
            if(tokenBundleLock == null) {
                log.error("Could not acquire lock for tokenkeys bundle caching");
                throw SecurityException.fatals.couldNotAcquireLockTokenCaching(); 
            }
            tokenBundleLock.acquire();            
            Configuration config = coordinator.queryConfiguration(FOREIGN_TOKEN_KEYS_BUNDLE_CONFIG,
                    FOREIGN_TOKEN_KEYS_BUNDLE_KEYID);
            ConfigurationImpl configImpl = null;
            if (config == null) {   
                configImpl = new ConfigurationImpl();
                configImpl.setId(FOREIGN_TOKEN_KEYS_BUNDLE_KEYID);
                configImpl.setKind(FOREIGN_TOKEN_KEYS_BUNDLE_CONFIG);
                log.debug("Creating new foreign tokens config");
            } else {
                configImpl = (ConfigurationImpl) config;
                log.debug("Updating existing foreign token config");
            } 
            configImpl.setConfig(vdcID, SerializerUtils.serializeAsBase64EncodedString(bundle));
            coordinator.persistServiceConfiguration(configImpl);
            foreignTokenKeysMap.put(vdcID, bundle);
        } catch (Exception ex) {
            log.error("Could not acquire lock while trying to cache tokenkeys bundle.", ex);
        } 
        finally {
            try {
                if(tokenBundleLock != null) {
                    tokenBundleLock.release();
                }
            } catch(Exception ex) {
                log.error("Unable to release token keys bundle caching lock", ex);
            }
        }
    }    
    
}
