/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
