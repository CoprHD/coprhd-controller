/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.helpers.proprietary;

import com.emc.storageos.security.helpers.SecurityService;
import com.emc.storageos.security.keystore.impl.KeyCertificateAlgorithmValuesHolder;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.security.ssh.PEMUtil;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.rsa.jsafe.JSAFE_PrivateKey;
import com.rsa.jsafe.provider.JsafeJCE;
import com.rsa.jsafe.provider.SensitiveData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.util.Arrays;

public class SecurityServiceJSafe implements SecurityService {

    private static Logger log = LoggerFactory.getLogger(SecurityServiceJSafe.class);

    private String[] ciphers;

    /**
     * @param pemKey
     * @return
     * @throws Exception
     */
    @Override
    public byte[] loadPrivateKeyFromPEMString(String pemKey) {
        
        try {
            if (PEMUtil.isPKCS8Key(pemKey)) {
                return PEMUtil.decodePKCS8PrivateKey(pemKey);
            }

            return decodePKCS1PrivateKey(pemKey);
        } catch (Exception e) {
            throw APIException.badRequests.failedToLoadKeyFromString(e);
        }
    }

    private byte[] decodePKCS1PrivateKey(String pemKey) throws Exception {

        JSAFE_PrivateKey privKey = null;

        try {
            privKey = JSAFE_PrivateKey.getInstance(
                    KeyCertificateAlgorithmValuesHolder.DEFAULT_KEY_ALGORITHM,
                    KeyCertificatePairGenerator.RSA_JAVA_DEVICE_NAME);

            byte[][] pemHolder = { pemKey.getBytes() };
            privKey.setKeyData(KeyCertificatePairGenerator.PRIVATE_RSA_KEY_PEM_FORMAT_NAME, pemHolder);

            // we need to clear the whole JSAFE_PrivateKey. so return the copied the key bytes.
            byte[] buf = Arrays.copyOf(privKey.getKeyData(KeyCertificatePairGenerator.PRIVATE_RSA_KEY_BER_FORMAT_NAME)[0],
                    privKey.getKeyData(KeyCertificatePairGenerator.PRIVATE_RSA_KEY_BER_FORMAT_NAME)[0].length);

            return buf;
        } finally {
            privKey.clearSensitiveData();
        }
    }

    /**
     * clear Sensitive data
     * 
     * @param key
     */
    @Override
    public void clearSensitiveData(byte[] key) {
        SensitiveData.clear(key);
    }

    /**
     * clear Sensitive data
     * 
     * @param key
     */
    @Override
    public void clearSensitiveData(Key key) {
        SensitiveData.clear(key);
    }

    @Override
    public void clearSensitiveData(KeyPair keyPair) {
        SensitiveData.clear(keyPair);
    }

    @Override
    public void clearSensitiveData(Signature signatureFactory) {
        SensitiveData.clear(signatureFactory);
    }

    @Override
    public void clearSensitiveData(KeyPairGenerator keyGen) {
        SensitiveData.clear(keyGen);
    }

    @Override
    public void clearSensitiveData(SecureRandom random) {
        SensitiveData.clear(random);
    }

    @Override
    public void initSecurityProvider() {
        Security.insertProviderAt(new JsafeJCE(), 1);
        log.info("Set RSA JsafeJCE as the Java cypto provider.");
    }

    @Override
    public String[] getCipherSuite() {
        // Not a real issue as no write outside
        return ciphers; // NOSONAR ("Suppressing: Returning 'ciphers' may expose an internal array")
    }

    // Not a real issue as no write in class
    public void setCiphers(String[] ciphers) { // NOSONAR ("Suppressing: The user-supplied array is stored directly.")
        this.ciphers = ciphers;
    }
}
