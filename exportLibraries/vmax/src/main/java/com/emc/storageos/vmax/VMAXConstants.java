/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax;

import java.net.URI;

public interface VMAXConstants {

    static final String AUTH_TOKEN = "X-XIO-AUTH-TOKEN";
    static final String AUTH_TOKEN_HEADER = "X-XIO-AUTH-TOKEN-HEADER";
    static final String HTTPS_URL = "https";
    static final String HTTP_URL = "http";
    static final String ASYNCHRONOUS_API_CALL = "ASYNCHRONOUS";
    static final String UNIVMAX_VERSION = "90";
    String APPLICATION_TYPE = "Application-Type";
    String VIPR_APPLICATION_TYPE = "viprc";

    public static enum MigrationActionTypes {
        Recover, Cutover, Sync, Commit, ReadyTgt
    }

    static final String UNIVMAX_BASE_URI = "/univmax/restapi";
    static final String UNIVMAX_SYSTEM_BASE_URI = UNIVMAX_BASE_URI + "/system";
    static final String UNIVMAX_SYSTEM_VERSION_URI = UNIVMAX_SYSTEM_BASE_URI + "/version";
    static final String UNIVMAX_SYSTEM_SYMM_LIST_URI = UNIVMAX_SYSTEM_BASE_URI + "/symmetrix";
    static final String UNIVMAX_SYSTEM_SYMM_GET_URI = UNIVMAX_SYSTEM_SYMM_LIST_URI + "/%1$s";

    static final String UNIVMAX_MIGRATION_BASE_URI = UNIVMAX_BASE_URI + "/" + UNIVMAX_VERSION + "/migration/symmetrix";
    static final String VALIDATE_ENVIRONMENT_URI = UNIVMAX_MIGRATION_BASE_URI + "/%1$s/environment/%2$s";
    static final String GET_MIGRATION_ENVIRONMENT_URI = UNIVMAX_MIGRATION_BASE_URI + "/%1$s/environment";

    static final String CREATE_MIGRATION_ENVIRONMENT_URI = UNIVMAX_MIGRATION_BASE_URI + "/%1$s";

    static final String GET_MIGRATION_STORAGEGROUPS_URI = UNIVMAX_MIGRATION_BASE_URI + "/%1$s/storagegroup";
    static final String MIGRATION_STORAGEGROUP_URI = UNIVMAX_MIGRATION_BASE_URI + "/%1$s/storagegroup/%2$s";
    static final String CANCEL_MIGRATION_WITH_REVERT_URI = MIGRATION_STORAGEGROUP_URI + "?revert=true";
    static final String GET_ASYNC_JOB = UNIVMAX_BASE_URI + "/" + UNIVMAX_VERSION +"/system/job/%1$s";

    static final String UNIVMAX_PROVISIONING_BASE_URI = UNIVMAX_BASE_URI + "/" + UNIVMAX_VERSION + "/provisioning/symmetrix";
    static final String STORAGEGROUP_VOLUMES_URI = UNIVMAX_PROVISIONING_BASE_URI + "/%1$s/volume?storageGroupId=%2$s";

    static final String UNIVMAX_SLOPROVISIONING_BASE_URI = UNIVMAX_BASE_URI + "/" + UNIVMAX_VERSION + "/sloprovisioning/symmetrix";
    static final String SLO_STORAGEGROUP_VOLUMES_URI = UNIVMAX_SLOPROVISIONING_BASE_URI + "/%1$s/volume?storageGroupId=%2$s";

    public static URI getValidateEnvironmentURI(String sourceArraySerialNumber, String targetArraySerialNumber) {
        return URI.create(String.format(VALIDATE_ENVIRONMENT_URI, sourceArraySerialNumber, targetArraySerialNumber));
    }

    public static URI getBaseURI(String ipAddress, int port, boolean isSSL) {
        return URI.create(String.format("%1$s://%2$s:%3$d", isSSL ? HTTPS_URL : HTTP_URL, ipAddress, port));
    }

    public static URI getMigrationEnvironmentURI(String sourceArraySerialNumber) {
        return URI.create(String.format(GET_MIGRATION_ENVIRONMENT_URI, sourceArraySerialNumber));
    }

    public static URI createMigrationEnvironmentURI(String sourceArraySerialNumber) {
        return URI.create(String.format(CREATE_MIGRATION_ENVIRONMENT_URI, sourceArraySerialNumber));
    }

    public static URI getMigrationStorageGroupsURI(String sourceArraySerialNumber) {
        return URI.create(String.format(GET_MIGRATION_STORAGEGROUPS_URI, sourceArraySerialNumber));
    }

    public static URI migrationStorageGroupURI(String sourceArraySerialNumber, String storageGroupName) {
        return URI.create(String.format(MIGRATION_STORAGEGROUP_URI, sourceArraySerialNumber, storageGroupName));
    }

    public static URI getVersionURI() {
        return URI.create(UNIVMAX_SYSTEM_VERSION_URI);
    }

    public static URI getSystemListURI() {
        return URI.create(UNIVMAX_SYSTEM_SYMM_LIST_URI);
    }

    public static URI getSystemGetURI(String symmId) {
        return URI.create(String.format(UNIVMAX_SYSTEM_SYMM_GET_URI, symmId));
    }

    public static URI cancelMigrationURI(String sourceArraySerialNumber, String storageGroupName) {
        return URI.create(String.format(MIGRATION_STORAGEGROUP_URI, sourceArraySerialNumber, storageGroupName));
    }

    public static URI cancelMigrationWithRevertURI(String sourceArraySerialNumber, String storageGroupName) {
        return URI.create(String.format(CANCEL_MIGRATION_WITH_REVERT_URI, sourceArraySerialNumber, storageGroupName));
    }

    public static URI getAsyncJobURI(String jobId) {
        return URI.create(String.format(GET_ASYNC_JOB, jobId));
    }

    public static URI storageGroupVolumesURI(String sourceArraySerialNumber, String storageGroupName, Boolean isVMAX3) {
        String storageGroupVolumesUri = isVMAX3 ? SLO_STORAGEGROUP_VOLUMES_URI : STORAGEGROUP_VOLUMES_URI;
        return URI.create(String.format(storageGroupVolumesUri, sourceArraySerialNumber, storageGroupName));
    }

}
