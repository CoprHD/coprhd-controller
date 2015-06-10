/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class IpProtocols {
    public static final String IPV4 = "IPV4";
    public static final String IPV6 = "IPV6";

    public static boolean isIPV4(String type) {
        return IPV4.equals(type);
    }

    public static boolean isIPV6(String type) {
        return IPV6.equals(type);
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
        return StringOption.getDisplayValue(type, "IpProtocol");
    }
}
