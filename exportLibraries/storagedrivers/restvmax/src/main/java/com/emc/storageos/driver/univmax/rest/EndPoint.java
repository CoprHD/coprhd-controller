/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest;

public class EndPoint {
    // System
    public static final String SYSTEM_VERSION = "/system/version";
    public static final String SYSTEM_SYMMETRIX = "/system/symmetrix";
    public static final String SYSTEM84_SYMMETRIX_ID_JOB_ID = "/84/system/symmetrix/%s/job/%s";

    // SLO Provisioning
    public static final String SLOPROVISIONING84_SYMMETRIX_ID = "/84/sloprovisioning/symmetrix/%s";
    public static final String SLOPROVISIONING84_SYMMETRIX_ID_STORAGEGROUP =
            "/84/sloprovisioning/symmetrix/%s/storagegroup";
    public static final String SLOPROVISIONING84_SYMMETRIX_ID_STORAGEGROUP_ID =
            "/84/sloprovisioning/symmetrix/%s/storagegroup/%s";
    public static final String SLOPROVISIONING84_SYMMETRIX_ID_VOLUME = "/84/sloprovisioning/symmetrix/%s/volume";
    public static final String SLOPROVISIONING84_SYMMETRIX_ID_VOLUME_ID = "/84/sloprovisioning/symmetrix/%s/volume/%s";
}
