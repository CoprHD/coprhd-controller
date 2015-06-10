/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class ComputeSessionUtil {

    public static enum Constants {

        COMPUTE_SESSION_BASE_PATH("/computesession"),
        LOCK_PREFIX("compute_lock");

        private final String _path;

        Constants(String path) {
            _path = path;
        }

        @Override
        public String toString() {
            return _path;
        }
    }

    public static String generateHash(String serviceUri, String username, String password){
        StringBuilder hashkey = new StringBuilder();
        String hash = null;
        hashkey.append(serviceUri);
        hashkey.append(username);
        hashkey.append(password);
        try{
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            sha.reset();
            hash = hexEncode(sha.digest(hashkey.toString().getBytes()));
        }
        catch (NoSuchAlgorithmException e){
            return hashkey.toString();
        }
        return hash;
    }

    private static String hexEncode( byte[] aInput){
        StringBuffer result = new StringBuffer();
        char[] digits = {'0', '1', '2', '3', '4','5','6','7','8','9','a','b','c','d','e','f'};
        for (int idx = 0; idx < aInput.length; ++idx) {
            byte b = aInput[idx];
            result.append( digits[ (b&0xf0) >> 4 ] );
            result.append( digits[ b&0x0f] );
        }
        return result.toString();
    }
}
