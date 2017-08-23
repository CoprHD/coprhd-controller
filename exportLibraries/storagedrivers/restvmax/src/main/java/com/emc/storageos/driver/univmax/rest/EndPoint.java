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

    public static class Export {
        public static final String INITIATOR = "/sloprovisioning/symmetrix/%s/initiator";
        public static final String INITIATOR_ID = "/sloprovisioning/symmetrix/%s/initiator/%s";
        public static final String HOST = "/sloprovisioning/symmetrix/%s/host";
        public static final String HOST_ID = "/sloprovisioning/symmetrix/%s/host/%s";
        public static final String HOSTGROUP = "/sloprovisioning/symmetrix/%s/hostgroup";
        public static final String HOSTGROUP_ID = "/sloprovisioning/symmetrix/%s/hostgroup/%s";
        public static final String PORTGROUP = "/sloprovisioning/symmetrix/%s/portgroup";
        public static final String PORTGROUP_ID = "/sloprovisioning/symmetrix/%s/portgroup/%s";
        public static final String MASKINGVIEW = "/sloprovisioning/symmetrix/%s/maskingview";
        public static final String MASKINGVIEW_ID = "/sloprovisioning/symmetrix/%s/maskingview/%s";
        public static final String MASKINGVIEW_ID_CONNECTIONS = "/sloprovisioning/symmetrix/%s/maskingview/%s/connections";
        public static final String VOLUME = "/sloprovisioning/symmetrix/%s/volume";
        public static final String VOLUME_ID = "/sloprovisioning/symmetrix/%s/volume/%s";
        public final static String STORAGEGROUP = "/sloprovisioning/symmetrix/%s/storagegroup";
        public final static String STORAGEGROUP_ID = "/sloprovisioning/symmetrix/%s/storagegroup/%s";

    }

    public static class Common {
        public final static String LIST_RESOURCE = "/common/Iterator/%s/page";
    }
}
