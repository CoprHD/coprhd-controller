/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.ssh;

import java.io.Serializable;
import java.security.*;

/**
 * DSA/ECDSA Key pair holder for SSH service
 */
public class SSHKeyPair implements Serializable {

    private byte[] privateKey;
    private byte[] publicKey;

    public byte[] getPublicKey() {
        // Not a real issue as no write outside
        return publicKey; // NOSONAR ("Suppressing: Returning may expose an internal array")
    }

    // Not a real issue as no write in class
    public void setPublicKey(byte[] publicKey) { // NOSONAR ("Suppressing: The user-supplied array is stored directly.")
        this.publicKey = publicKey;
    }

    public byte[] getPrivateKey() {
        // Not a real issue as no write outside
        return privateKey; // NOSONAR ("Suppressing: Returning may expose an internal array")
    }

    // Not a real issue as no write in class
    public void setPrivateKey(byte[] privateKey) { // NOSONAR ("Suppressing: The user-supplied array is stored directly.")
        this.privateKey = privateKey;
    }

    public static SSHKeyPair toKeyPair(KeyPair keyPair) {
        SSHKeyPair kp = new SSHKeyPair();
        kp.setPrivateKey(keyPair.getPrivate().getEncoded());
        kp.setPublicKey(keyPair.getPublic().getEncoded());
        return kp;
    }
}
