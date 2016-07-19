/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.locking;

/**
 * Identifies the LockTypes that have configurable timeouts (and other future support).
 */
public enum LockType {

    RP_CG("rp_consistency_group"),
    RP_VPLEX_CG("rp_vplex_consistency_group"),
    ARRAY_CG("array_consistency_group"),
    VPLEX_BACKEND_EXPORT("vplex_backend_export"),
    EXPORT_GROUP_OPS("export_group_ops"),
    RP_EXPORT("rp_export"),
    VPLEX_API_LIB("vplex_api_lib"),
    SRDF_PROVISIONING("srdf_provisioning"),
    FILE_OPERATIONS("file_operations");

    private String PREFIX = "controller_";
    private String SUFFIX = "_lock_timeout";
    private String key;

    LockType(String key) {
        this.key = PREFIX + key + SUFFIX;
        ;
    }

    /**
     * Returns the coordinator key for the property value.
     * 
     * @return String
     */
    public String getKey() {
        return key;
    }

}
