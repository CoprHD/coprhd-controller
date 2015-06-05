/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.helpers;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;

public interface SecurityService {

    byte[] loadPrivateKeyFromPEMString(String pemKey) throws Exception;

    void clearSensitiveData(byte[] key);

    void clearSensitiveData(Key rsaPrivateKey);

    void clearSensitiveData(KeyPair keyPair);

    void clearSensitiveData(Signature signatureFactory);

    void clearSensitiveData(KeyPairGenerator keyGen);

    void clearSensitiveData(SecureRandom random);

    void initSecurityProvider();
}
