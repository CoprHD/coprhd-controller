/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

/**
 * The state of site, used to track state transaction during disaster recovery.
 * */
public enum SiteState {
    ACTIVE, // Active site. Eligible for all provisioning operations
    STANDBY_SYNCING, // Standby site. Syncing from an active one
    STANDBY_SYNCED,  // Standby site. Synced with active one 
    STANDBY_PAUSED,  // Standby site. Replication is paused 
    STANDBY_TESTING, // Standby site. Run DR testing
    STANDBY_REMOVING, // Standby site. Removing
    STANDBY_ERROR    // Unrecoverable error for this standby site
}
