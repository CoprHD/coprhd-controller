/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class CompatibilityStatus {
    public static final String COMPATIBLE = "COMPATIBLE";
    public static final String INCOMPATIBLE = "INCOMPATIBLE";
    public static final String UNKNOWN = "UNKNOWN";

    public static boolean isCompatible(String type) {
        return COMPATIBLE.equals(type);
    }

    public static boolean isIncompatible(String type) {
        return INCOMPATIBLE.equals(type);
    }

    public static boolean isUnknown(String type) {
        return UNKNOWN.equals(type);
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
        return StringOption.getDisplayValue(type, "CompatibilityStatus");
    }
}
