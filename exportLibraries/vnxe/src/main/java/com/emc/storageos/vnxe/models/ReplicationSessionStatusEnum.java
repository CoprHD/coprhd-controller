/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

public enum ReplicationSessionStatusEnum {
    UNKNOWN,
    OTHER,
    OK,
    PAUSED,
    FATAL_REPLICATION_ISSUE,
    LOST_COMMUNICATION,
    FAILED_OVER,
    FAILED_OVER_WITH_SYNC,
    LOST_SYNC_COMMUNICATION,
    DESTINATION_EXTEND_NOT_SYNCING,
    DESTINATION_EXTEND_IN_PROGRESS,
    DESTINATION_POOL_OUT_OF_SPACE;
}
