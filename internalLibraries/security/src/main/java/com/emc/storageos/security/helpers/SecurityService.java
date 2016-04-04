/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.helpers;

import java.security.*;

public interface SecurityService {

    byte[] loadPrivateKeyFromPEMString(String pemKey);

    void clearSensitiveData(byte[] key);

    void clearSensitiveData(Key rsaPrivateKey);

    void clearSensitiveData(KeyPair keyPair);

    void clearSensitiveData(Signature signatureFactory);

    void clearSensitiveData(KeyPairGenerator keyGen);

    void clearSensitiveData(SecureRandom random);

    void initSecurityProvider();

    String[] getCipherSuite();
}
