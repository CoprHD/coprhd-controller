/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class ObjectProtocols {
    public static final String SWIFT = "Swift";
    public static final String ATMOS = "Atmos";
    public static final String S3 = "S3";

    public static boolean isSWIFT(String type) {
        return SWIFT.equals(type);
    }

    public static boolean isATMOS(String type) {
        return ATMOS.equals(type);
    }

    public static boolean isS3(String type) {
        return S3.equals(type);
    }

    public static boolean isFileProtocol(String type) {
        return isSWIFT(type) || isATMOS(type) || isS3(type);
    }

    public static StringOption option(String type) {
        return new StringOption(type, getDisplayValue(type));
    }

    public static List<StringOption> options(String... types) {
        List<StringOption> options = Lists.newArrayList();
        for (String type : types) {
            options.add(option(type));
        }
        return options;
    }

    public static String getDisplayValue(String type) {
        return StringOption.getDisplayValue(type, "ObjectStorageProtocol");
    }
}
