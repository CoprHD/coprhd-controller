/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class SupportedResourceTypes {
    public static final String THICK_ONLY = "THICK_ONLY";
    public static final String THIN_ONLY = "THIN_ONLY";
    public static final String THIN_AND_THICK = "THIN_AND_THICK";

    public static boolean isThickOnly(String type) {
        return THICK_ONLY.equals(type);
    }

    public static boolean isThinOnly(String type) {
        return THIN_ONLY.equals(type);
    }

    public static boolean isThinAndThick(String type) {
        return THIN_AND_THICK.equals(type);
    }

    public static boolean supportsThin(String type) {
        return isThinOnly(type) || isThinAndThick(type);
    }

    public static boolean supportsThick(String type) {
        return isThickOnly(type) || isThinAndThick(type);
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
        return StringOption.getDisplayValue(type, "SupportedResourceTypes");
    }

}
