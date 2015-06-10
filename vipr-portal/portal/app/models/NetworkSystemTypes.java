/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class NetworkSystemTypes {
    public static final String MDS = "mds";
    public static final String BROCADE = "brocade";
    public static final String[] VALUES = { MDS, BROCADE };

    public static boolean isMds(String type) {
        return MDS.equals(type);
    }

    public static boolean isBrocade(String type) {
        return BROCADE.equals(type);
    }

    public static boolean isSmisManaged(String type) {
        return isBrocade(type);
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
        return StringOption.getDisplayValue(type, "NetworkSystemType");
    }
}
