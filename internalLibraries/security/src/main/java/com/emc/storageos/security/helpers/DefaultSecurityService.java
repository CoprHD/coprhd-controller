/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.helpers;

import com.emc.storageos.security.helpers.SecurityService;
import com.emc.storageos.security.ssh.PEMUtil;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import java.security.*;

public class DefaultSecurityService implements SecurityService {

    private String[] ciphers;

    @Override
    public byte[] loadPrivateKeyFromPEMString(String pemKey) {

        if (!PEMUtil.isPKCS8Key(pemKey)) {
            throw APIException.badRequests.failedToLoadKeyFromString();
        }

        try {
            return PEMUtil.decodePKCS8PrivateKey(pemKey);
        } catch (Exception e) {
            throw APIException.badRequests.failedToLoadKeyFromString(e);
        }
    }

    @Override
    public void clearSensitiveData(byte[] key) {

    }

    @Override
    public void clearSensitiveData(Key rsaPrivateKey) {

    }

    @Override
    public void clearSensitiveData(KeyPair keyPair) {

    }

    @Override
    public void clearSensitiveData(Signature signatureFactory) {

    }

    @Override
    public void clearSensitiveData(KeyPairGenerator keyGen) {

    }

    @Override
    public void clearSensitiveData(SecureRandom random) {

    }

    @Override
    public void initSecurityProvider() {

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
