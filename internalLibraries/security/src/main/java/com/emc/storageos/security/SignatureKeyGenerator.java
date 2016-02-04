/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security;

import javax.crypto.SecretKey;

import org.slf4j.Logger;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

public abstract class SignatureKeyGenerator {

    protected CoordinatorClient _coordinator;

    public abstract String getDistributedSignatureKeyLock();

    public abstract String getSignatureKeyId();

    @Deprecated
    public abstract String getSignatureKey();

    public abstract String getSignatureKeyConfig();

    public abstract Logger getLogger();

    /**
     * getSignatureKey2 (keeping the original getSignature() as deprecated because portal uses this method from
     * our jar still).
     * 
     * @param relKeyLocation "leaf" node under \getSignatureKeyConfig()\getSignatureKeyId()\
     * @param algo algorithm to use for that key
     * @return the key
     * @throws Exception
     */
    protected synchronized SecretKey getSignatureKey2(String relKeyLocation, String algo) throws Exception {
        Configuration config = _coordinator.queryConfiguration(getSignatureKeyConfig(), getSignatureKeyId());
        if (config != null && config.getConfig(relKeyLocation) != null) {
            final String encodedKey = config.getConfig(relKeyLocation);
            return SignatureHelper.createKey(encodedKey, algo);
        } else {
            InterProcessLock lock = null;
            try {
                lock = _coordinator.getLock(getDistributedSignatureKeyLock());
                lock.acquire();
                config = _coordinator.queryConfiguration(getSignatureKeyConfig(), getSignatureKeyId());
                ConfigurationImpl cfg = (ConfigurationImpl) config;
                if (cfg == null) {
                    cfg = new ConfigurationImpl();
                    cfg.setId(getSignatureKeyId());
                    cfg.setKind(getSignatureKeyConfig());
                }
                String keyEncoded = SignatureHelper.generateKey(algo);
                cfg.setConfig(relKeyLocation, keyEncoded);
                _coordinator.persistServiceConfiguration(cfg);
                return SignatureHelper.createKey(keyEncoded, algo);
            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        }
    }

    /**
     * Delete a particular signature key
     * 
     * @param relKeyLocation "leaf" node under \getSignatureKeyConfig()\getSignatureKeyId()\
     * @param relKeyLocation
     * @throws Exception
     */
    protected synchronized void deleteSignatureKey(String relKeyLocation) throws Exception {
        Configuration config = _coordinator.queryConfiguration(getSignatureKeyConfig(), getSignatureKeyId());
        if (config != null && config.getConfig(relKeyLocation) != null) {
            InterProcessLock lock = null;
            try {
                lock = _coordinator.getLock(getDistributedSignatureKeyLock());
                lock.acquire();
                config = _coordinator.queryConfiguration(getSignatureKeyConfig(), getSignatureKeyId());
                config.removeConfig(relKeyLocation);
                _coordinator.persistServiceConfiguration(config);

            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        }
    }

    /**
     * TODO: DELETE in phase 2 when UI has switched to LoginSignatureKeyGenerator api.
     * Used by the UI to get a key.
     * 
     * @param algo
     * @return
     * @throws Exception
     */
    @Deprecated
    public synchronized SecretKey getSignatureKey(String algo) throws Exception {
        Configuration config = _coordinator.queryConfiguration(getSignatureKeyConfig(), getSignatureKeyId());
        if (config != null && config.getConfig(getSignatureKey()) != null) {
            final String encodedKey = config.getConfig(getSignatureKey());
            return new SignatureHelper().createKey(encodedKey, algo);
        } else {
            InterProcessLock lock = null;
            try {
                lock = _coordinator.getLock(getDistributedSignatureKeyLock());
                lock.acquire();
                config = _coordinator.queryConfiguration(getSignatureKeyConfig(), getSignatureKeyId());
                if (config == null || config.getConfig(getSignatureKey()) == null) {
                    ConfigurationImpl cfg = new ConfigurationImpl();
                    cfg.setId(getSignatureKeyId());
                    cfg.setKind(getSignatureKeyConfig());
                    String keyEncoded = SignatureHelper.generateKey(algo);
                    cfg.setConfig(getSignatureKey(), keyEncoded);
                    _coordinator.persistServiceConfiguration(cfg);
                }
                config = _coordinator.queryConfiguration(getSignatureKeyConfig(), getSignatureKeyId());
                final String encodedKey = config.getConfig(getSignatureKey());
                return SignatureHelper.createKey(encodedKey, algo);
            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        }
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }
}
