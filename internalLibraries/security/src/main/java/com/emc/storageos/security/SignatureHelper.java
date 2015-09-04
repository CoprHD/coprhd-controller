/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security;

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;

import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 *
 */
public class SignatureHelper {
    public static Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * sign the contents of String. with the specified key and algorithm
     * 
     * @param buf
     * @param key
     * @param algorithm
     * @return
     */
    public static String sign2(String buf, SecretKey key, String algorithm) {
        return sign2(buf.getBytes(), key, algorithm);
    }

    /**
     * Deprecated. Use static method sign2 intead.
     * sign the contents of String. with the specified key and algorithm
     * 
     * @param buf
     * @param key
     * @param algorithm
     * @return
     */
    @Deprecated
    public String sign(String buf, SecretKey key, String algorithm) {
        return sign2(buf.getBytes(), key, algorithm);
    }

    /**
     * sign the contents of String. with the specified key and algorithm
     * 
     * @param buf
     * @param key
     * @param algorithm
     * @return
     */
    public static String sign2(byte[] buf, SecretKey key, String algorithm) {
        Mac mac;
        try {
            mac = Mac.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw SecurityException.fatals.noSuchAlgorithmException(algorithm, e);
        }
        try {
            mac.init(key);
        } catch (InvalidKeyException e) {
            throw APIException.badRequests.theParametersAreNotValid(
                    SecretKey.class.getName(), e);
        }
        return new String(Base64.encodeBase64(mac.doFinal(buf)), UTF_8);
    }

    /**
     * Deprecated. Use static method sign2 intead.
     * sign the contents of String. with the specified key and algorithm
     * 
     * @param buf
     * @param key
     * @param algorithm
     * @return
     */
    @Deprecated
    public String sign(byte[] buf, SecretKey key, String algorithm) {
        return sign2(buf, key, algorithm);
    }

    /**
     * Creates a SecretKey from encoded string
     * 
     * @param encoded
     * @param algo
     * @return
     */
    public static SecretKey createKey(String encoded, String algo) {
        return new SecretKeySpec(Base64.decodeBase64(encoded.getBytes(SignatureHelper.UTF_8)), algo);
    }

    /**
     * Generate a new secret key, encoded as string
     * 
     * @param algo
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String generateKey(String algo) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(algo);
        SecretKey key = keyGenerator.generateKey();
        return new String(Base64.encodeBase64(key.getEncoded()), UTF_8);
    }
}
