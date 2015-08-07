/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;

import util.StringOption;

public class ProtectionSystemTypes {
    public static final String RECOVERPOINT = "rp";
    public static final String SRDF = "srdf";

    private static final String[] VALUES = { RECOVERPOINT };
    public static final StringOption[] OPTIONS = StringOption.options(VALUES, "ProtectionSystemType");

    public static boolean isSRDF(String type) {
        return SRDF.equals(type);
    }

    public static boolean isRecoverPoint(String type) {
        return RECOVERPOINT.equals(type);
    }

    public static boolean isRecoverPointOrNone(String type) {
        return StringUtils.isBlank(type) || isRecoverPoint(type);
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
        return StringOption.getDisplayValue(type, "ProtectionSystemType");
    }
}
