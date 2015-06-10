/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class RaidLevel {
    public static final String RAID0 = "RAID0";
    public static final String RAID1 = "RAID1";
    public static final String RAID2 = "RAID2";
    public static final String RAID3 = "RAID3";
    public static final String RAID4 = "RAID4";
    public static final String RAID5 = "RAID5";
    public static final String RAID6 = "RAID6";
    public static final String RAID10 = "RAID10";

    public static final String[] VALUES = { RAID0, RAID1, RAID2, RAID3, RAID4, RAID5, RAID6, RAID10 };

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
        return StringOption.getDisplayValue(type, "RaidLevel");
    }
}
