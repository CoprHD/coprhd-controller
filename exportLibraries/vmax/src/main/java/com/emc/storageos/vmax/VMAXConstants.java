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
    
    String UNIVMAX_BASE_URI = "/univmax/restapi/84/migration/symmetrix";
    String VALIDATE_ENVIRONMENT_URI= UNIVMAX_BASE_URI+ "/000195701430/environment/000196701405";
    
    
    
    
    public static String getBaseURI(String ipAddress, int port, boolean isSSL) {
        return String.format("%1$s://%2$s:%3$d", isSSL ? HTTPS_URL : HTTP_URL, ipAddress, port);
    }
}
