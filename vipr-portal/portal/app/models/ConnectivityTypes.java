/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class ConnectivityTypes {
    public static final String RECOVER_POINT = "rp";
    public static final String SRDF = "srdf";
    public static final String VPLEX = "vplex";
    public static final String RP_VPLEX = "rpvplex";
    public static final String UNSUPPORTED = "unsupported";

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
        return StringOption.getDisplayValue(type, "ConnectivityTypes");
    }
}
