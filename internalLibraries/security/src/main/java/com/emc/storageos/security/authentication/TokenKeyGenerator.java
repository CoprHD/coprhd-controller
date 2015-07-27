/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.security.SerializerUtils;
import com.emc.storageos.security.SignatureHelper;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.springframework.util.CollectionUtils;

/**
 *  Class responsible for generating and providing access to signature keys for Tokens and ProxyTokens, during
 *  creation/encoding and validation/decoding.  It is also responsible for rotating keys when older than a default of 24 hours.
 *  Rotation will happen on a "as needed" basis, when it is detected that the key about to be used for a new token request
 *  is old.
 *  
 *  This class also manages a cached object TokenKeysBundle which is used for persisting into coordinator and
 *  also hold the cached SecretKeys for the current and previous keys for regular tokens, and the fixed proxy token key.   
 */
public class TokenKeyGenerator  {
    
    public static final String TOKEN_SIGNING_ALGO = "HmacSha1";
    
    // utils
    @Autowired
    protected TokenMaxLifeValuesHolder _maxLifeValuesHolder;
    protected CoordinatorClient _coordinator;
    protected static SignatureHelper _signatureHelper = new SignatureHelper();
    private static Logger _log = LoggerFactory.getLogger(TokenKeyGenerator.class);  
    
    // coordinator persistence path artifacts
    private static final String DISTRIBUTED_KEY_TOKEN_LOCK = "tokenKeyGeneratorLock";
    private static final String SIGNATURE_KEY_CONFIG = "tokenKeysConfig"; 
    private static final String SIGNATURE_KEY_ID = "tokenKeyId";
    // Inside the config, this property-key gets the "bundle" that has the actual keys inside
    private static final String SIGNATURE_KEY = "tokenKeysBundleEntry";
    // Fixed proxy token key id.
    private static final String SIGNATURE_PROXY_KEY_ENTRY = "proxyTokenSignatureKeyEntry";
    
    // Rotation related
    private static final int DEFAULT_KEY_ROTATION_INTERVAL = 1000 * 60 * 60 * 24; 
    private long _keyRotationIntervalInMsecs = DEFAULT_KEY_ROTATION_INTERVAL;

    // Misc. cached items.
    private final String _proxyTokenKeyEntry = SIGNATURE_PROXY_KEY_ENTRY;
    private TokenKeysBundle _cachedTokenKeysBundle = new TokenKeysBundle();

    // setters/getters
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }
    
    public void setTokenMaxLifeValuesHolder(TokenMaxLifeValuesHolder holder) {
        _maxLifeValuesHolder = holder;
    }
    
   
    public long getKeyRotationIntervalInMSecs() {
        return _keyRotationIntervalInMsecs;
    }
    
    /**
     * Pair of key id (time stamp) to SecretKey
     */
    public static class KeyIdKeyPair implements Serializable {
        private static final long serialVersionUID = 1L;
        private String _entry;
        private SecretKey _key;
        
        public SecretKey getKey() {
            return _key;
        }
            
        public String getEntry() {
            return _entry;
        }
        
        public void setKey(SecretKey key) {
            _key = key;         
        }
        
        public void setKeyEntry(String keyEntry) {
            _entry = keyEntry;
        }
        
        public KeyIdKeyPair() {}
        
        public KeyIdKeyPair(String entry, SecretKey key) {
            _entry = entry;
            _key = key;
        }
        @Override
        public boolean equals(Object pair) {
            KeyIdKeyPair p = (KeyIdKeyPair) pair;
            return (p._entry.equals(_entry));
        }

        @Override
        public int hashCode() {
            return _entry.hashCode();
        }
    }
    // --- end of embedded KeyIdKeyPair class
    
    /**
     * This nested class represents the objects that will be presisted in zookeeper.
     * It holds the current keyid-secretkey pair, the previous one, and the proxy token secret key.
     */
    public static class TokenKeysBundle implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * Factory method to create a bundle from scratch with a new proxytoken key and a new current token key
         * @return
         */
        public static TokenKeysBundle createNewTokenKeysBundle() 
                throws NoSuchAlgorithmException {
            SecretKey proxyTokenKey = generateNewKey(TOKEN_SIGNING_ALGO);      
            return new TokenKeysBundle(getNewKeyPair(), proxyTokenKey);
        }
        
        /**
         * Factory method to create a new bundle based on an existing bundle.  The new bundle starts off
         * with copying the keys from the original, then adds a new one.  If the total number of keys is more 
         * than two, delete the oldest one.
         * @param currentBundle
         * @return
         */
        public static TokenKeysBundle createNewTokenKeysBundleWithRotatedKeys(TokenKeysBundle currentBundle)
                throws NoSuchAlgorithmException {
            TokenKeysBundle newBundle = new TokenKeysBundle();
            newBundle._cachedProxyKey = currentBundle._cachedProxyKey;
            newBundle._cachedKeyPairs.addAll(currentBundle._cachedKeyPairs); 
            newBundle._cachedKeyPairs.add(getNewKeyPair());
            if (newBundle._cachedKeyPairs.size() > 2) {
                newBundle._cachedKeyPairs.remove(0);
            }
            return newBundle;
        }

        private static KeyIdKeyPair getNewKeyPair() throws NoSuchAlgorithmException {
            Long now = System.currentTimeMillis();
            String entry = now.toString();              
            _log.debug("New key entry generated: {}", entry);
            return new KeyIdKeyPair(entry, generateNewKey(TOKEN_SIGNING_ALGO));
        }
               
        private TokenKeysBundle(final List<KeyIdKeyPair> cachedKeysIdKeypairs, final SecretKey proxyTokenKey) {
            _cachedKeyPairs = cachedKeysIdKeypairs;
            _cachedProxyKey = proxyTokenKey;
        }
        
        private TokenKeysBundle(KeyIdKeyPair cachedKeysIdKeypair, final SecretKey proxyTokenKey) {
            ArrayList<KeyIdKeyPair> keys = new ArrayList<KeyIdKeyPair>();
            keys.add(cachedKeysIdKeypair);
            _cachedKeyPairs = keys;
            _cachedProxyKey = proxyTokenKey;
        }
        
        // the list of cached keys.  There will only be 2 at any time.
        // Ordered from oldest to newest. (order of insertion)
        private List<KeyIdKeyPair> _cachedKeyPairs = new ArrayList<KeyIdKeyPair>();
        
        private SecretKey _cachedProxyKey = null;
        
        public TokenKeysBundle () {
        }
        
        /**
         * returns a list of the key ids currently cached.
         * @return list of ids
         */
        public List<String> getKeyEntries() {
            List<String> ids = new ArrayList<String>();
            for (KeyIdKeyPair pair : _cachedKeyPairs) {
                ids.add(pair.getEntry());
            }
            return ids;
        }
        
        /**
         * returns the most recent key id in the cache
         * @return keyid
         */
        public String getCurrentKeyEntry() {
            if ( !CollectionUtils.isEmpty(_cachedKeyPairs) ) {
                return _cachedKeyPairs.get(_cachedKeyPairs.size()-1).getEntry();
            }
            return null;
        }
        
        /**
         * Returns the cached SecretKey for a given keyid, if it exists
         * @param keyEntry
         * @return
         */
        public SecretKey getKey(String keyEntry) {
            int i = _cachedKeyPairs.indexOf(new KeyIdKeyPair(keyEntry, null));
            if (i >= 0) {
                return _cachedKeyPairs.get(i).getKey();
            }
            return null;
        }
                
        public SecretKey getProxyKey() {
            return _cachedProxyKey;
        }
    }
    
    // --- end of TokensKeysBundle class.

    /**
     * initializes the rotation time based on the max token life value.
     * @return
     */
    private void computeRotationTime() {
        _keyRotationIntervalInMsecs = _maxLifeValuesHolder.computeRotationTimeInMSecs();
        _log.info("Key rotation time in msecs: {}", _keyRotationIntervalInMsecs);
    }
    
    private final ScheduledExecutorService keyRotationExecutor = Executors.newScheduledThreadPool(1);
    
    /**
     * Initialization method to be called by authsvc.  It will create the key configuration if it doesn't exist
     * on first startup.  Else it will just load the cache.
     * @throws Exception
     */
    public void globalInit() throws Exception {
        computeRotationTime();
        InterProcessLock lock = null;
        try{     
            lock = _coordinator.getLock(DISTRIBUTED_KEY_TOKEN_LOCK);
            lock.acquire();
            if (!doesConfigExist()) {
                TokenKeysBundle bundle = TokenKeysBundle.createNewTokenKeysBundle();             
                createOrUpdateBundle(bundle);
                updateCachedTokenKeys(bundle);
            } else {
                updateCachedKeys();
                _log.debug("Token keys configuration exists, loaded keys");
                _log.debug("Current token key {}", _cachedTokenKeysBundle.getCurrentKeyEntry());        
            }       
            keyRotationExecutor.scheduleWithFixedDelay(
                    new KeyRotationThread(), 0, _keyRotationIntervalInMsecs, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            _log.error("Exception during initialization of TokenKeyGenerator", ex);
        }
        finally {
            try {
                if (lock != null) {
                    lock.release();
                }
            } catch (Exception ex) {
                _log.error("Could not release the lock during TokenKeyGenerator.init()");
                throw ex;
            }
        }
    }
    
    /**
     * Initialization method to be called by all token validating apis.  apisvc and syssvc.
     * It will load the cache.
     * @throws Exception
     */
    public void cacheInit() throws Exception {
        computeRotationTime();
        updateCachedKeys();
    }
    
    /**
     * terminates scheduled threads executor
     */
    public void destroy() {
        if (keyRotationExecutor != null) {
            keyRotationExecutor.shutdown();
            _log.debug("Shutting down key rotation thread");
        }
    }
    
    
    /**
     * Reloads the keys from coordinator into cache.
     * @throws Exception
     */
    public void updateCachedKeys() throws Exception {
        TokenKeysBundle bundle = readBundle();
        if (bundle != null) {
            updateCachedTokenKeys(bundle);
        } else {
            _log.debug("Did not update cache.  Keys bundle was null.  Bundle may not be available yet");
            // That is ok.  When validating tokens, a reload of the cache will be invoked if the cache
            // is empty.
        }
    }
        
    
    /**
     * Thread responsible for rotating keys
     * 
     * */
    public class KeyRotationThread implements Runnable {
        @Override
        public void run() {
            InterProcessLock lock = null;
            try {
                if (!checkCurrentKeyAge()) {          
                    _log.info("Current key in cache is old.  Reloading cache.");
                    // We are reloading the catch and performing the check again, because
                    // it's possible another node already did a rotation and we are not aware of it
                    try {
                        lock = _coordinator.getLock(DISTRIBUTED_KEY_TOKEN_LOCK);
                        lock.acquire();
                        updateCachedKeys();
                        if (!checkCurrentKeyAge()) {
                            _log.info("Current key in cache is still old after reload.  Rotating keys now.");
                            rotateKeys(); 
                        }
                    } finally {
                        if (lock != null) {
                            try {
                                lock.release();
                            } catch (Exception ex) {
                                _log.error("Could not release lock during key rotation attempt");
                            }
                        }
                    }
                }
            }
            catch(NumberFormatException ex) {
                _log.error("Could not convert key to timestamp: {}", _cachedTokenKeysBundle.getCurrentKeyEntry(), ex);
            } catch(Exception ex) {
                _log.error("Exception when trying to retrieve current token key entry", ex);
            } 
        }        
    }
    
    
    
    /**
     * Helper synchronized method to overwrite the cache.
     * @param bundle
     */
    private synchronized void updateCachedTokenKeys(TokenKeysBundle bundle) {
        _cachedTokenKeysBundle = bundle;  
    }
       
    
  
    /**
     * Returns the current token key to be used in regular auth token during signing.
     * @return current token key id
     */
    public String getCurrentTokenKeyEntry() {    
        return _cachedTokenKeysBundle.getCurrentKeyEntry();
    }

    /**
     * Looks at the current key timestamp.  If it is older than rotation interval, return false.
     * @return false if key is old.  True if young (younger than rotation interval)
     * @throws NumberFormatException
     */
    private boolean checkCurrentKeyAge() throws NumberFormatException {
        long currentTokenKeyTS = Long.parseLong(_cachedTokenKeysBundle.getCurrentKeyEntry());
        long now = System.currentTimeMillis();
        long diff = now - currentTokenKeyTS;
        if (diff >= _keyRotationIntervalInMsecs) {
            return false;
        }
        return true;
    }

    /**
     * 
     * Returns the proxy token key id, to used in proxytoken signing
     * @return
     */
    public String getProxyTokenKeyEntry() {
        return _proxyTokenKeyEntry;
    }
    
    /**
     * Returns the token signature key, used to sign the auth token.
     * @return
     */
    public KeyIdKeyPair getCurrentTokenSignatureKeyPair() {
        String id = getCurrentTokenKeyEntry();
        SecretKey key = getTokenSignatureKey(id);
        return new KeyIdKeyPair(id, key);
    }
    
    /**
     * Returns the proxytoken signature key.  Used to sign the proxytoken.
     *
     * @return
     */
    public KeyIdKeyPair getProxyTokenSignatureKeyPair() {
        String id = getProxyTokenKeyEntry();
        SecretKey key = _cachedTokenKeysBundle.getProxyKey();
        return new KeyIdKeyPair(id, key);
    }
    
    /**
     * Requests a signature key for the given key id.  
     * Due to the caches on the cluster being potentially out of sync because of key rotations,
     * we look at the most current key id in our cache.  If it is older than the key id we are
     * requesting, we update our cache.  For example, a rotation happened on node 1, a token got signed
     * on node 1 with the newest key.  Node 2 does not know about the rotation yet.  When node 2 is presented
     * with a key id that is more recent than what it knows, it reloads the cache.
     * @param keyEntry: the key id for which we are querying
     * @return
     */
    public SecretKey getTokenSignatureKey(String keyEntry) {       
        if (keyEntry.equals(_proxyTokenKeyEntry)) {
            return _cachedTokenKeysBundle.getProxyKey();
        }
        try {
            if (CollectionUtils.isEmpty(_cachedTokenKeysBundle.getKeyEntries())) {
                _log.info("Cache was empty at initialization time.  Perhaps authsvc hasn't created the initial signature keys yet.");
                updateCachedKeys();
            } else {
                // before we look in our cache, let's verify a couple things about this key
                if (!checkRequestedKeyAge(keyEntry)) {
                    updateCachedKeys();
                }
            }
        } catch(NumberFormatException ex) {
            _log.error("Could not convert key to timestamp: {}", keyEntry);
            return null;
        } catch(Exception ex) {
            _log.error("Could not update cached keys", ex);
        }
        return _cachedTokenKeysBundle.getKey(keyEntry);
    }

    /**
     * - If a key we are being requested is really old (2 * rotation time), we should not even
     * look it up in our cache.  Because it could be there if our cache is old and
     * we don't want to honor that key.
     * - If a key is newer than our current key, we should udpate our cache
     * @return true if the key is ok (no need to update cache), false otherwise.
     */
    private boolean checkRequestedKeyAge(String keyEntry) throws NumberFormatException {
        // is this key older than twice the rotation intervale ?
        long requestedTokenKeyTS = Long.parseLong(keyEntry);
        long now = System.currentTimeMillis();
        long diff = now - requestedTokenKeyTS;        
        if (diff > (2 * _keyRotationIntervalInMsecs)) {
            _log.debug("Requested key is older than twice the rotation intervale: {}", keyEntry);
            return false;
        }
        // is this key newer than the most recent key in cache?
        long youngestKey = Long.parseLong(_cachedTokenKeysBundle.getCurrentKeyEntry()); 
        if (youngestKey < Long.parseLong(keyEntry)) {
            _log.debug("Requested key is newer than the most recent cached key: {}", keyEntry);
            return false;
        }       
        return true;
    }


    /**
     * Rotates keys.  Creates a new key, and deletes the oldest if there are more than 2.
     * Updates the cache.
     * MUST BE CALLED BY A CODE OWNING INTERPROCESS LOCK
     */
    public void rotateKeys() throws Exception {        
        try {
            _log.info("Rotating keys...");
            TokenKeysBundle newBundle = TokenKeysBundle.createNewTokenKeysBundleWithRotatedKeys(_cachedTokenKeysBundle);
            createOrUpdateBundle(newBundle);
            _log.info("Done rotating keys...");
        } catch (NumberFormatException ex) {
            _log.error("NumberFormatException while trying to rotate token keys, could not convert timestamp", ex);
        } catch (Exception ex) {
            _log.error("Exception while trying to rotate token keys", ex);
        } finally {
            updateCachedKeys();
        }
    }    

    
    
    // Coordinator client interraction for persistence
    
    /**
     * 
     * Creates or updates a TokenKeysBundle in coordinator.  
     * @param bundleIn: bundle to persist
     * MUST BE CALLED BY A CODE OWNING INTERPROCESS LOCK
     * @throws Exception
     */
    private synchronized void createOrUpdateBundle(TokenKeysBundle bundleIn) throws Exception {
        Configuration config = _coordinator.queryConfiguration(SIGNATURE_KEY_CONFIG, SIGNATURE_KEY_ID);
        ConfigurationImpl configImpl = null;
        if (config == null) {   
            configImpl = new ConfigurationImpl();
            configImpl.setId(SIGNATURE_KEY_ID);
            configImpl.setKind(SIGNATURE_KEY_CONFIG);
            _log.debug("Creating new config");
        } else {
            configImpl = (ConfigurationImpl) config;
            _log.debug("Updating existing config");

        } 
        configImpl.setConfig(SIGNATURE_KEY, SerializerUtils.serializeAsBase64EncodedString(bundleIn));
        _coordinator.persistServiceConfiguration(configImpl);
        _log.debug("Updated keys bundle successfully");
        return;
    }
    
    /**
     * Reads the TokenKeysBundle from coordinator and deserializes it.
     * @return the retrieved bundle or null if not found
     * @throws Exception
     */
    public TokenKeysBundle readBundle() throws Exception {
        Configuration config = _coordinator.queryConfiguration(SIGNATURE_KEY_CONFIG, SIGNATURE_KEY_ID);
        if (config == null || config.getConfig(SIGNATURE_KEY) == null) {
            _log.warn("Token keys bundle not found");
            return null;
        }
        String serializedBundle = config.getConfig(SIGNATURE_KEY);
        _log.debug("Read bundle from coordinator: {}", serializedBundle);
        return (TokenKeysBundle) SerializerUtils.deserialize(serializedBundle);
    }

    /**
     * Generate a new SecretKey.
     * @param algo
     * @return new key
     * @throws NoSuchAlgorithmException
     */
    private static SecretKey generateNewKey(String algo) throws NoSuchAlgorithmException {
        String encodedKey = _signatureHelper.generateKey(algo);
        return _signatureHelper.createKey(encodedKey, algo);
    }
     
    /**
     * Checks if our config for storing token keys exists already.
     * @return true if exists, false otherwise.
     * @throws Exception
     */
    public boolean doesConfigExist() throws Exception {
        List<Configuration> configs = _coordinator.queryAllConfiguration(SIGNATURE_KEY_CONFIG);
        if (CollectionUtils.isEmpty(configs)) {
            return false;
        }
        return true;
    }
    
    // --- End of Coordinator client interaction for persistence
    
   
}
