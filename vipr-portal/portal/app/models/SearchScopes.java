/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class SearchScopes {
    public static final String ONELEVEL = "ONELEVEL";
    public static final String SUBTREE = "SUBTREE";
    public static final String UNKNOWN = "UNKNOWN";

    public static boolean isOneLevel(String type) {
        return ONELEVEL.equals(type);
    }

    public static boolean isSubTree(String type) {
        return SUBTREE.equals(type);
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
        return StringOption.getDisplayValue(type, "SearchScope");
    }

}
