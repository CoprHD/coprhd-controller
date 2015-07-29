/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class DriveTypes {
    public static final String NONE = "NONE";
    public static final String SSD = "SSD";
    public static final String FC = "FC";
    public static final String SAS = "SAS";
    public static final String NL_SAS = "NL_SAS";
    public static final String SATA = "SATA";

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
        return StringOption.getDisplayValue(type, "DriveType");
    }
}
