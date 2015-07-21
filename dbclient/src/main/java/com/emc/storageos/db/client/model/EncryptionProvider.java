/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Provide custom implementation of this interface
 * for non default encryption scheme
 */
public interface EncryptionProvider {
    /**
     * Starts encryption provider
     */
    public void start();

    /**
     * Encrypt input string
     * @param input
     * @return
     */
    public byte[] encrypt(String input);

    /**
     * Decrypt input string
     * @param input
     * @return
     */
    public String decrypt(byte[] input);

    /**
      * Encrypts a string. The returned value is a Base64 encoded string representing the encrypted data.
      * @param s the string to encrypt.
      * @return the encrypted (and Base64 encoded) string.
      */
    public String getEncryptedString(String s);

}
