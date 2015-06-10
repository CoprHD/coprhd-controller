/*
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

import com.emc.storageos.security.helpers.SecurityService;
import com.emc.storageos.security.ssh.PEMUtil;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;

public class DefaultSecurityService implements SecurityService {

    @Override
    public byte[] loadPrivateKeyFromPEMString(String pemKey) throws Exception {

        if (! PEMUtil.isPKCS8Key(pemKey)) {
            throw new Exception("Only PKCS8 is supported");
        }

        return PEMUtil.decodePKCS8PrivateKey(pemKey);
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
}
