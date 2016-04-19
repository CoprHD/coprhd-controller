/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

public enum BackupType {
    db,
    geodb,
    zk,
    // Add special backup type for geodb under multi vdc
    geodbmultivdc,
    info
}
