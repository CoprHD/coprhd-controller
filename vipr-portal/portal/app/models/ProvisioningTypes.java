/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class ProvisioningTypes {
    public static final String THICK = "Thick";
    public static final String THIN = "Thin";
    public static final String ALL = "All";

    public static boolean isThick(String type) {
        return THICK.equals(type);
    }

    public static boolean isThin(String type) {
        return THIN.equals(type);
    }

    public static boolean isAll(String type) {
        return ALL.equals(type);
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
        return StringOption.getDisplayValue(type, "ProvisioningType");
    }
}
