/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class HighAvailability {
    public static final String VPLEX_LOCAL = "vplex_local";
    public static final String VPLEX_DISTRIBUTED = "vplex_distributed";
    public static final String VPLEX_SOURCE = "source";
    public static final String VPLEX_HA = "ha";

    public static boolean isVplexLocal(String type) {
        return VPLEX_LOCAL.equals(type);
    }

    public static boolean isVplexDistributed(String type) {
        return VPLEX_DISTRIBUTED.equals(type);
    }

    public static boolean isHighAvailability(String type) {
        return isVplexLocal(type) || isVplexDistributed(type);
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
        return StringOption.getDisplayValue(type, "HighAvailability");
    }
}
