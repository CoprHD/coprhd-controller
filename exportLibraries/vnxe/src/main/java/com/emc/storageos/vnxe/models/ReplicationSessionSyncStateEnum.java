/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

public enum ReplicationSessionSyncStateEnum {
    MANUAL_SYNCING,
    AUTO_SYNCING,
    IDLE,
    UNKNOWN,
    OUT_OF_SYNC,
    IN_SYNC,
    CONSISTENT,
    SYNCING,
    INCONSISTENT;
}
