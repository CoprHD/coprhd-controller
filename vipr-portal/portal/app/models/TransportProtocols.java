/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class TransportProtocols {
    public static final String FC = "FC";
    public static final String IP = "IP";
    public static final String ETHERNET = "Ethernet";
    public static final String SCALEIO = "ScaleIO";

    public static final String[] VALUES = { FC, IP, ETHERNET, SCALEIO };
    public static StringOption[] OPTIONS = StringOption.options(VALUES, "TransportProtocol");

    public static boolean isFc(String type) {
        return FC.equals(type);
    }

    public static boolean isIp(String type) {
        return IP.equals(type);
    }

    public static boolean isEthernet(String type) {
        return ETHERNET.equals(type);
    }

    public static boolean isScaleIO(String type) {
        return SCALEIO.equals(type);
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
        return StringOption.getDisplayValue(type, "TransportProtocol");
    }
}
