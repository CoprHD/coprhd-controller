/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore.impl;

import java.io.Serializable;
import java.security.Key;
import java.security.cert.Certificate;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;

/**
 * a class representing a key and it's matching certificate's entry in persistence
 */
public class KeyCertificateEntry implements Serializable {

    private static final long serialVersionUID = 9217286234984340287L;
    private byte[] key;
    private Certificate[] certificateChain;
    private Date creationDate;

    // Not a real issue as no write in class
    public KeyCertificateEntry(byte[] key, Certificate[] certificateChain) { // NOSONAR
                                                                             // ("Suppressing: The user-supplied array is stored directly.")
        this.key = Base64.encodeBase64(key);
        this.certificateChain = certificateChain;
    }

    public KeyCertificateEntry(Key key, Certificate[] certificateChain) {
        this(key.getEncoded(), certificateChain);
    }

    public KeyCertificateEntry(Key key, Certificate[] certificateChain, Date creationDate) {
        this(key, certificateChain);
        this.creationDate = creationDate;
    }

    public KeyCertificateEntry(byte[] key, Certificate[] certificateChain,
            Date creationDate) {
        this(key, certificateChain);
        this.creationDate = creationDate;
    }

    /**
     * @return the key
     */
    public byte[] getKey() {
        return Base64.decodeBase64(key);
    }

    /**
     * @param key
     *            the key to set
     */
    public void setKey(Key key) {
        this.key = Base64.encodeBase64(key.getEncoded());
    }

    /**
     * @param key the key to set
     */
    public void setKey(byte[] key) {
        this.key = Base64.encodeBase64(key);
    }

    /**
     * @return the certificateChain
     */
    public Certificate[] getCertificateChain() {
        // Not a real issue as no write outside
        return certificateChain; // NOSONAR ("Suppressing: Returning 'ciphers' may expose an internal array")
    }

    /**
     * @param certificateChain
     *            the certificateChain to set
     */
    // Not a real issue as no write in class
    public void setCertificateChain(Certificate[] certificateChain) {  // NOSONAR
                                                                      // ("Suppressing: The user-supplied array is stored directly.")
        this.certificateChain = certificateChain;
    }

    /**
     * @return the creationDate
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * @param creationDate
     *            the creationDate to set
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

}
