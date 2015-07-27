/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.codec.binary.Base64;

/*
 *  Utils class for object serialization and base64 encoding/decoding
 */
public class SerializerUtils {
    
    /**
     * Serialize an object and base64 encodes it
     * @param the object
     * @return the encoded String
     * @throws IOException
     */
    public static String serializeAsBase64EncodedString(Object b) throws IOException {
        byte [] rawBytes = serializeAsByteArray(b); 
        byte [] encodedBytes = Base64.encodeBase64(rawBytes);  
        return new String(encodedBytes, "UTF-8");
    }
             
    /**
     * Serialize an object as a raw byte array
     * @param the object
     * @return byte array
     * @throws IOException
     */
    public static byte[] serializeAsByteArray(Object b) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);      
        try {
            out.writeObject(b);
        } finally {
            out.close();
        }
        return bos.toByteArray();  
    }

    /**
     * Base64 decodes a String into a decoded byte array, then deserializes the object.
     * @param base64 encoded string
     * @return deserialized object
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Object deserialize(String input) throws IOException,
    ClassNotFoundException { 
        byte[] decoded = Base64.decodeBase64(input.getBytes("UTF-8"));    
        return deserialize(decoded);
    }

    /**
     * Deserializes an object from raw byte array input
     * @param byte array
     * @return deserialized object
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Object deserialize(byte [] input) throws IOException,
    ClassNotFoundException { 
        Object st = null;    
        ByteArrayInputStream bin = new ByteArrayInputStream(input);     
        ObjectInputStream oin = new ObjectInputStream(bin);    
        try {
            st = oin.readObject();
        } finally {
            oin.close();
        }
        return st;
    }      
}
