/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

/**
 * A storage system type could support one or multiple storage profiles.
 * NOTE: any changes to this class should be applied to StorageSystemType.StorageProfile too
 */
public enum StorageProfile {
    BLOCK,
    REMOTE_REPLICATION_FOR_BLOCK,
    FILE,
    REMOTE_REPLICATION_FOR_FILE
}
