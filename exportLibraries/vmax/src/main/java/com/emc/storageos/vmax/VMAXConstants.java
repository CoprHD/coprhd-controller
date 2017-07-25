/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax;

import java.net.URI;

public interface VMAXConstants {

    String AUTH_TOKEN = "X-XIO-AUTH-TOKEN";
    String AUTH_TOKEN_HEADER = "X-XIO-AUTH-TOKEN-HEADER";
    String HTTPS_URL = "https";
    String HTTP_URL = "http";

    String UNIVMAX_BASE_URI = "/univmax/restapi/84/migration/symmetrix";
    String VALIDATE_ENVIRONMENT_URI = UNIVMAX_BASE_URI + "/%1$s/environment/%2$s";

    String GET_MIGRATION_ENVIRONMENT_URI = UNIVMAX_BASE_URI + "/%1$s/environment";
    String CREATE_MIGRATION_ENVIRONMENT_URI = UNIVMAX_BASE_URI + "/%1$s";

    String GET_MIGRATION_STORAGEGROUPS_URI = UNIVMAX_BASE_URI + "/%1$s/storagegroup";
    String GET_MIGRATION_STORAGEGROUP_URI = UNIVMAX_BASE_URI + "/%1$s/storagegroup/%2$s";

    public static URI getValidateEnvironmentURI(String sourceArraySerialNumber, String targetArraySerialNumber) {
        return URI.create(String.format(VALIDATE_ENVIRONMENT_URI, sourceArraySerialNumber, targetArraySerialNumber));
    }

    public static URI getBaseURI(String ipAddress, int port, boolean isSSL) {
        return URI.create(String.format("%1$s://%2$s:%3$d", isSSL ? HTTPS_URL : HTTP_URL, ipAddress, port));
    }

    public static URI getMigrationEnvironmentURI(String sourceArraySerialNumber) {
        return URI.create(String.format(GET_MIGRATION_ENVIRONMENT_URI, sourceArraySerialNumber));
    }

    public static URI createMigrationEnvornmentURI(String sourceArraySerialNumber) {
        return URI.create(String.format(CREATE_MIGRATION_ENVIRONMENT_URI, sourceArraySerialNumber));
    }

    public static URI getMigrationStorageGroupsURI(String sourceArraySerialNumber) {
        return URI.create(String.format(GET_MIGRATION_STORAGEGROUPS_URI, sourceArraySerialNumber));
    }

    public static URI getMigrationStorageGroupURI(String sourceSymmetrixId, String storageGroupName) {
        return URI.create(String.format(GET_MIGRATION_STORAGEGROUP_URI, sourceSymmetrixId, storageGroupName));
    }

}
