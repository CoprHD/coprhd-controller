/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest;

public class EndPoint {
    // System
    public static final String SYSTEM_VERSION = "/system/version";
    public static final String SYSTEM_SYMMETRIX = "/system/symmetrix";

    // SLO Provisioning
    public static final String SLOPROVISIONING_SYMMETRIX__STORAGEGROUP = "/sloprovisioning/symmetrix/%s/storagegroup";
    public static final String SLOPROVISIONING_SYMMETRIX__VOLUME_QUERY = "/sloprovisioning/symmetrix/%s/volume?%s=%s";
    public static final String SLOPROVISIONING_SYMMETRIX__VOLUME_ID = "/sloprovisioning/symmetrix/%s/volume/%s";
}
