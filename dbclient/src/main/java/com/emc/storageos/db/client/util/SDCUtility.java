/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.util;

public class SDCUtility {
    private static final String HEXIDECIMAL_16CHARS = "^((0?[x|X])|#)?[0-9,A-F,a-f]{16}$";

    public static boolean isValidSDC(String element) {
        return element.trim().matches(HEXIDECIMAL_16CHARS);
    }
}
