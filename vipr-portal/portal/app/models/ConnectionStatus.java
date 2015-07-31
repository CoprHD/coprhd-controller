/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class ConnectionStatus {
    public static final String CONNECTED = "CONNECTED";
    public static final String DISCONNECTED = "NOTCONNECTED";

    public static boolean isConnected(String type) {
        return CONNECTED.equals(type);
    }

    public static boolean isDisconnected(String type) {
        return DISCONNECTED.equals(type);
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
        return StringOption.getDisplayValue(type, "ConnectionStatus");
    }
}
