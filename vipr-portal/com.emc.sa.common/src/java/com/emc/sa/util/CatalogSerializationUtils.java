/*
 * Copyright (c) 2017  DELL EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

import org.apache.commons.lang.StringUtils;

public class CatalogSerializationUtils {

    public static final String SERIAL_PREFIX = "serial";

    public static boolean isSerializedObject(String resource) {
        return resource.contains(CatalogSerializationUtils.SERIAL_PREFIX);
    }

    /** Write the object to a Base64 string. */
    public static String serializeToString(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return SERIAL_PREFIX + (Base64.getEncoder().encodeToString(baos.toByteArray()));
    }

    /** Read the object from Base64 string. */
    public static Object serializeFromString(String s) throws IOException, ClassNotFoundException {
        String str = StringUtils.substring(s, SERIAL_PREFIX.length());
        byte [] data = Base64.getDecoder().decode(str);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data) );
        Object o  = ois.readObject();
        ois.close();
        return o;
    }
}
