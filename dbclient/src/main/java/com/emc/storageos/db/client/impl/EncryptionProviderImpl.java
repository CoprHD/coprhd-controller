/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.impl;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.model.EncryptionProvider;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * Default encryption provider.   Uses AES encryption.
 */
public class EncryptionProviderImpl implements EncryptionProvider {
    private static final Logger _logger = LoggerFactory.getLogger(EncryptionProviderImpl.class);
    private static final String CONFIG_KIND = "encryption";
    private static final String CONFIG_ID = "id";
    private static final String ALGO = "AES";
    private static Charset UTF_8 = Charset.forName("UTF-8");
    private static final byte ENC_PROVIDER_VERSION = 0x01;

    private CoordinatorClient _coordinator;
    private SecretKey _key;
    private Cipher _cipher;
    private Cipher _decipher;
    
    private String _encryptId = CONFIG_ID;

    /**
     * @param configId the configId to set
     */
    public void setEncryptId(String encryptId) {
        this._encryptId = encryptId;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public SecretKey getKey(){
        return this._key;
    }

    @Override
    public void start() {
        try {
            cacheKey();
            _cipher = Cipher.getInstance(ALGO);
            _cipher.init(Cipher.ENCRYPT_MODE, _key);
            _decipher = Cipher.getInstance(ALGO);
            _decipher.init(Cipher.DECRYPT_MODE, _key);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads existing encryption key from coordinator and caches it
     *
     * @throws Exception
     */
    private synchronized void cacheKey() throws Exception {
        Configuration config = _coordinator.queryConfiguration(CONFIG_KIND, _encryptId);
        if (config != null) {
            readKey(config);
        } else {
            // key has not been generated
            InterProcessLock lock = null;
            try {
                lock = _coordinator.getLock(CONFIG_KIND);
                lock.acquire();
                config = _coordinator.queryConfiguration(CONFIG_KIND, _encryptId);
                if (config == null) {
                    _logger.warn("Encryption key not found, initializing it");
                    generateKey();
                } else {
                    readKey(config);
                }
            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        }
    }

    /**
     * Generates a new key and persists it to coordinator
     *
     * @throws Exception
     */
    private void generateKey() throws Exception {
        KeyGenerator keygen = KeyGenerator.getInstance(ALGO);
        SecretKey key = keygen.generateKey();
        persistKey(key);
    }

    /**
     * Persists key to coordinator
     *
     * @throws Exception
     */
    private void persistKey(SecretKey key) throws Exception {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(CONFIG_KIND);
        config.setId(_encryptId);
        config.setConfig(CONFIG_KIND, new String(Base64.encodeBase64(key.getEncoded()), UTF_8));
        _coordinator.persistServiceConfiguration(config);
        _key = key;
    }

    /**
     * Deserialize secret key
     *
     * @param config
     */
    private void readKey(Configuration config) {
        String base64Encoded = config.getConfig(CONFIG_KIND);
        SecretKey key = new SecretKeySpec(Base64.decodeBase64(
                base64Encoded.getBytes(UTF_8)), ALGO);
        _key = key;
    }
    
    // Add a version number
    private byte[] encode(byte[] input) {
        byte[] out = new byte[input.length + 1];
        out[0] = ENC_PROVIDER_VERSION;
        System.arraycopy(input, 0, out, 1, input.length);
        return out;
    }

    // remove the version number
    private byte[] decode(byte[] input) {
        if (input.length < 2) {
            throw new IllegalStateException("decrypt decode failed from db, invalid input (length < 2)");
        }
        else if (input[0] != ENC_PROVIDER_VERSION) {
            throw new IllegalStateException("decrypt decode failed from db: "
                + "version found: " + input[0]
                + "version expected: " + ENC_PROVIDER_VERSION);
        }
        byte[] out = new byte[input.length - 1];
        System.arraycopy(input, 1, out, 0, input.length - 1);
        return out;
    }        

    @Override
    public byte[] encrypt(String input) {
        try {
            return encode(_cipher.doFinal(input.getBytes(UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String decrypt(byte[] input) {
        try {
            byte[] enc = decode(input);
            return new String(_decipher.doFinal(enc), UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getEncryptedString(String input) {
       byte[] data = encrypt(input);
       try {
          return new String(Base64.encodeBase64(data), "UTF-8");
       } catch (UnsupportedEncodingException e) {
          // All JVMs must support UTF-8, this really can never happen
          throw new RuntimeException(e);
       }
    }

    public void restoreKey(SecretKey key) throws Exception { 
        InterProcessLock lock = null;
        try {
            lock = _coordinator.getLock(CONFIG_KIND);
            lock.acquire();
            persistKey(key);
        }finally {
            if (lock != null) {
                lock.release();
            }
        }
    }
}
