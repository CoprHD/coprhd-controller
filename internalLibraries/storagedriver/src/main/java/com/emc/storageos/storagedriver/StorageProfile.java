/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

/**
 * A storage system type could support one or multiple storage profiles.
 */
public enum StorageProfile {
    BLOCK,
    REMOTE_REPLICATION_FOR_BLOCK,
    FILE,
    REMOTE_REPLICATION_FOR_FILE
}
