/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class PoolAssignmentTypes {
    public static final String AUTOMATIC = "automatic";
    public static final String MANUAL = "manual";

    public static boolean isAutomatic(String type) {
        return AUTOMATIC.equals(type);
    }

    public static boolean isManual(String type) {
        return MANUAL.equals(type);
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
        return StringOption.getDisplayValue(type, "PoolAssignment");
    }
}
