/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import com.emc.storageos.db.client.model.EncryptionProvider;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * Created by brian on 14-11-13.
 */
public class DummyEncryptionProvider implements EncryptionProvider {

    private static final String ALGO = "AES";
    private Charset UTF_8 = Charset.forName("UTF-8");
    private static final byte ENC_PROVIDER_VERSION = 0x01;
    private SecretKey _key;
    private Cipher _cipher;
    private Cipher _decipher;

    @Override
    public void start() {
        try {
            generateKey();
            _cipher = Cipher.getInstance(ALGO);
            _cipher.init(Cipher.ENCRYPT_MODE, _key);
            _decipher = Cipher.getInstance(ALGO);
            _decipher.init(Cipher.DECRYPT_MODE, _key);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void generateKey() throws Exception {
        KeyGenerator keygen = KeyGenerator.getInstance(ALGO);
        SecretKey key = keygen.generateKey();
        _key = key;
    }

    private byte[] encode(byte[] input) {
        byte[] out = new byte[input.length + 1];
        out[0] = ENC_PROVIDER_VERSION;
        System.arraycopy(input, 0, out, 1, input.length);
        return out;
    }

    @Override
    public byte[] encrypt(String input) {
        try {
            return encode(_cipher.doFinal(input.getBytes(UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getEncryptedString(String input) {
        byte[] data = encrypt(input);
        try {
            return new String(Base64.encodeBase64(data), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // All JVMs must support UTF-8, this really can never happen
            throw new RuntimeException(e);
        }
    }

    @Override
    public String decrypt(byte[] input) {
        try {
            byte[] enc = decode(input);
            return new String(_decipher.doFinal(enc), UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] decode(byte[] input) {
        if (input.length < 2 || input[0] != ENC_PROVIDER_VERSION) {

            throw new IllegalStateException("decrypt decode failed from db: "
                    + "version found: " + input[0]
                    + "version expected: " + ENC_PROVIDER_VERSION);
        }
        byte[] out = new byte[input.length - 1];
        System.arraycopy(input, 1, out, 0, input.length - 1);
        return out;
    }

}
