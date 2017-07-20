/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax;

public interface VMAXConstants {

    String HTTPS_URL = "https";
    String HTTP_URL = "http";
    String COLON = ":";
    String COMMA = ",";
    String AT_THE_RATE_SYMBOL = "@";
    String PLUS_OPERATOR = "+";
    String HYPHEN_OPERATOR = "-";
    String SLASH_OPERATOR = "/";
    String UNDERSCORE_OPERATOR = "_";
    String DOT_OPERATOR = ".";

    public static String getBaseURI(String ipAddress, int port, boolean isSSL) {
        return String.format("%1$s://%2$s:%3$d", isSSL ? HTTPS_URL : HTTP_URL, ipAddress, port);
    }
}
