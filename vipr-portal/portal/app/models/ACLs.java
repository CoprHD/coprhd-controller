/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class ACLs {
    public static final String ALL = "ALL";
    public static final String BACKUP = "BACKUP";
    public static final String OWN = "OWN";
    public static final String USE = "USE";

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
        return StringOption.getDisplayValue(type, "ACL");
    }
}
