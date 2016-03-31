/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.SecretKey;

import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.security.SignatureHelper;
import com.emc.storageos.security.SignatureKeyGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * key generator for apisvc internal api HMAC key --> this class will also be used for inter vdc
 * HMAC key handling. So we should probably rename it to something more generic or maybe simply combine
 * this class and its base.
 */
public class InternalApiSignatureKeyGenerator extends SignatureKeyGenerator {

    public enum SignatureKeyType {
        INTERNAL_API, INTERVDC_API
    };

    private static final String SYSTEM_PROPERTY_INTERNAL_API_ALGO = "internal_api_key_algo";
    private static final String SYSTEM_PROPERTY_INTERNAL_API_ALGO_VALUE_NEW = "sha-256";

    private static final String DISTRIBUTED_SIGNATURE_KEY_LOCK = "InternalApiKeyLock";
    private static final String SIGNATURE_KEY_CONFIG = "InternalApiKeyConfig";
    private static final String SIGNATURE_KEY_ID = "global";
    private static final String CURRENT_SIGNATURE_INTERNALAPI_KEY = "InternalApiKey"; // current
    private static final String NEW_SIGNATURE_INTERNALAPI_KEY = "InternalApiKeyNew"; // new overriding key
    private static final String SIGNATURE_INTERVDC_KEY = "InterVDCApiKey";
    private static final Logger _log = LoggerFactory.getLogger(InternalApiSignatureKeyGenerator.class);

    // keeping internal api algo private and vdc algo public as a hint
    // note: internal api keys and algorithm should not be dealt with directly.
    // inter vdc keys do get exposed.
    public static final String CURRENT_INTERNAL_API_SIGN_ALGO = "HmacSHA1";  // currently used in zk
    public static final String NEW_INTERNAL_API_SIGN_ALGO = "HmacSHA256";    // new key. If available, takes precedence over current. (current
                                                                          // becomes obsolete)
    public static final String CURRENT_INTERVDC_API_SIGN_ALGO = "HmacSHA256";

    private SecretKey _internalApiCurrentKey = null;
    private SecretKey _interVDCCurrentKey = null;
    boolean _initialized = false;

    private static AtomicLong _lastUpdated = new AtomicLong();
    private static int DEFAULT_MAX_KEY_STALE_TIME_IN_HRS = 6;
    private static int _maxKeyStaleTimeInHrs = DEFAULT_MAX_KEY_STALE_TIME_IN_HRS;

    public void setMaxKeyStaleTimeInHrs(int hrs) {
        _maxKeyStaleTimeInHrs = hrs;
    }

    @Override
    public String getDistributedSignatureKeyLock() {
        return DISTRIBUTED_SIGNATURE_KEY_LOCK;
    }

    @Override
    public String getSignatureKeyId() {
        return SIGNATURE_KEY_ID;
    }

    @Deprecated
    @Override
    public String getSignatureKey() {
        return CURRENT_SIGNATURE_INTERNALAPI_KEY;
    }

    @Override
    public String getSignatureKeyConfig() {
        return SIGNATURE_KEY_CONFIG;
    }

    @Override
    public Logger getLogger() {
        return _log;
    }

    /**
     * Reads keys from coordinator.
     * 
     * @throws Exception if current key can't be found
     * @return the previous key if available or current key
     */
    public synchronized void loadKeys() {
        try {
            PropertyInfo props = _coordinator.getPropertyInfo();
            String internalApiAlgoOverride = null;
            if (props != null) {
                internalApiAlgoOverride = props.getProperty(SYSTEM_PROPERTY_INTERNAL_API_ALGO);
            }
            _log.debug("Internal Api algo override property: " + internalApiAlgoOverride);
            if (internalApiAlgoOverride != null && internalApiAlgoOverride.equals(SYSTEM_PROPERTY_INTERNAL_API_ALGO_VALUE_NEW)) {
                _internalApiCurrentKey = getSignatureKey2(NEW_SIGNATURE_INTERNALAPI_KEY, NEW_INTERNAL_API_SIGN_ALGO);
                deleteSignatureKey(CURRENT_SIGNATURE_INTERNALAPI_KEY);
            } else {
                _internalApiCurrentKey = getSignatureKey2(CURRENT_SIGNATURE_INTERNALAPI_KEY,
                        CURRENT_INTERNAL_API_SIGN_ALGO);
            }
            // get inter vdc key
            _interVDCCurrentKey = getSignatureKey2(SIGNATURE_INTERVDC_KEY,
                    CURRENT_INTERVDC_API_SIGN_ALGO);
        } catch (Exception e) {
            throw new IllegalStateException("Exception while retrieving key", e);
        }
        if (_internalApiCurrentKey == null) {
            throw new IllegalStateException("Key was null / Unable to get current internal api key.");
        }
        if (_interVDCCurrentKey == null) {
            throw new IllegalStateException("Key was null / Unable to get current inter vdc api key.");
        }
        _initialized = true;
        _lastUpdated.set(System.currentTimeMillis());
        return;
    }

    public SecretKey getSignatureKey(SignatureKeyType type) {
        reloadKeysIfNeeded();
        switch (type) {
            case INTERNAL_API:
                _log.debug("Api Key uses {}", _internalApiCurrentKey.getAlgorithm().toString());
                return _internalApiCurrentKey;
            case INTERVDC_API:
                return _interVDCCurrentKey;
        }
        return null;
    }

    /**
     * Indicates if the bean was initialized at least once. For optimization where one does not
     * want to call loadKeys() if a key is already loaded.
     * 
     * @return
     */
    public boolean isInitialized() {
        return _initialized;
    }

    /**
     * Reloads keys from coordinator if it's been maxKeyStaleTimeInHrs
     */
    private void reloadKeysIfNeeded() {
        long now = System.currentTimeMillis();
        if (!_initialized || ((now - _lastUpdated.get()) / (1000 * 60 * 60) > _maxKeyStaleTimeInHrs)) {
            try {
                _log.info("Stale time reached, reloading keys");
                loadKeys();
                _log.info("Reloaded keys successfully");
            } catch (Exception e) {
                _log.error("Could not reload the keys", e);
            }
        } else {
            _log.debug("Reload not needed");
        }
        return;
    }

    /**
     * signs the contents of String. Signs with the internal api key
     * 
     * @param buf content to sign
     * @return signature empty string if signature could not be computed
     */
    public String sign(String buf) {
        return sign(buf, SignatureKeyType.INTERNAL_API);
    }

    public String sign(String buf, SignatureKeyType type) {
        reloadKeysIfNeeded();
        SecretKey key = getSignatureKey(type);
        _log.info("====== internal key in sign is {}", Arrays.toString(key.getEncoded()));
        if (key == null) {
            _log.error("Null key while signing");
            return "";
        }
        return SignatureHelper.sign2(buf.getBytes(), key, key.getAlgorithm());
    }
}
