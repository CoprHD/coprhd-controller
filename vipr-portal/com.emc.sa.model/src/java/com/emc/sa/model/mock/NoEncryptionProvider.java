/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.model.mock;

import java.io.UnsupportedEncodingException;

import com.emc.storageos.db.client.model.EncryptionProvider;

public class NoEncryptionProvider implements EncryptionProvider {

    @Override
    public String decrypt(byte[] value) {
        try {
            return new String(value, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    @Override
    public byte[] encrypt(String value) {
        try {
            return value.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    @Override
    public String getEncryptedString(String s) {
        return s;
    }

    @Override
    public void start() {
    }
}
