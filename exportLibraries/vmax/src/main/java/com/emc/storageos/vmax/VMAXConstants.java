/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax;

public interface VMAXConstants {

    String AUTH_TOKEN = "X-XIO-AUTH-TOKEN";
    String AUTH_TOKEN_HEADER = "X-XIO-AUTH-TOKEN-HEADER";
    String HTTPS_URL = "https";
    String HTTP_URL = "http";

    String UNIVMAX_BASE_URI = "/univmax/restapi/84/migration/symmetrix";
    String VALIDATE_ENVIRONMENT_URI = UNIVMAX_BASE_URI + "/%1$s/environment/%2$s";
    String GET_MIGRATION_ENVIRONMENT_URI = UNIVMAX_BASE_URI + "/%1$s/environment";

    public static String getValidateEnvironmentURI(String sourceSymmetrixId, String targetSymmetrixId) {
        return String.format(VALIDATE_ENVIRONMENT_URI, sourceSymmetrixId, targetSymmetrixId);
    }

    public static String getBaseURI(String ipAddress, int port, boolean isSSL) {
        return String.format("%1$s://%2$s:%3$d", isSSL ? HTTPS_URL : HTTP_URL, ipAddress, port);
    }

    public static String getMigrationEnvironmentURI(String sourceSymmetrixId) {
        return String.format(GET_MIGRATION_ENVIRONMENT_URI, sourceSymmetrixId);
    }
}
