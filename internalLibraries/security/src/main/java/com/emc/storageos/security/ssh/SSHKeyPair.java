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
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }

    public static SSHKeyPair toKeyPair(KeyPair keyPair) {
        SSHKeyPair kp = new SSHKeyPair();
        kp.setPrivateKey(keyPair.getPrivate().getEncoded());
        kp.setPublicKey(keyPair.getPublic().getEncoded());
        return kp;
    }
}
