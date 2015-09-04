/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

/**
 * The status of DB migration
 * which is triggered in upgrading
 */
public enum MigrationStatus {
    RUNNING, // migration is running
    FAILED,  // migration failed
    DONE   // migration success
}
