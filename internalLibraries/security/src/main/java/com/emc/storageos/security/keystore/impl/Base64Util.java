/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.keystore.impl;

import org.apache.commons.codec.binary.Base64;

public class Base64Util {

    private static final int PEM_OUTPUT_LINE_SIZE = 64;

    public static byte[] encodeWithNewLine(byte[] bytes) {
        Base64 encoder = new Base64(PEM_OUTPUT_LINE_SIZE);
        return encoder.encode(bytes);
    }
}
