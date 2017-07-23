/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax;

public interface VMAXConstants {

    static final String AUTH_TOKEN = "X-XIO-AUTH-TOKEN";
    static final String AUTH_TOKEN_HEADER = "X-XIO-AUTH-TOKEN-HEADER";
    static final String HTTPS_URL = "https";
    static final String HTTP_URL = "http";

    static final  String UNIVMAX_BASE_URI = "/univmax/restapi";
    static final String UNIVMAX_SYSTEM_BASE_URI = UNIVMAX_BASE_URI + "/system";
    static final String UNIVMAX_SYSTEM_VERSION_URI = UNIVMAX_SYSTEM_BASE_URI + "/version";
    static final String UNIVMAX_SYSTEM_SYMM_LIST_URI = UNIVMAX_SYSTEM_BASE_URI + "/symmetrix";
    static final String UNIVMAX_SYSTEM_SYMM_GET_URI = UNIVMAX_SYSTEM_SYMM_LIST_URI + "/%1$s";

    static final String UNIVMAX_MIGRATION_BASE_URI = UNIVMAX_BASE_URI + "/84/migration/symmetrix";
    static final String VALIDATE_ENVIRONMENT_URI = UNIVMAX_MIGRATION_BASE_URI + "/%1$s/environment/%2$s";

    public static String getValidateEnvironmentURI(String sourceSymmetrixId, String targetSymmetrixId) {
        return String.format(VALIDATE_ENVIRONMENT_URI, sourceSymmetrixId, targetSymmetrixId);
    }

    public static String getVersionURI() {
        return UNIVMAX_SYSTEM_VERSION_URI;
    }

    public static String getSystemListURI() {
        return UNIVMAX_SYSTEM_SYMM_LIST_URI;
    }

    public static String getSystemGetURI(String symmId) {
        return String.format(UNIVMAX_SYSTEM_SYMM_GET_URI, symmId);
    }
}
