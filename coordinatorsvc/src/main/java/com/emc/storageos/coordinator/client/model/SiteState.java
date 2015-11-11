/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

/**
 * The state of site, used to track state transaction during disaster recovery.
 * */
public enum SiteState {
    PRIMARY, // Primary site. Eligible for all provisioning operations
    PRIMARY_SWITCHING_OVER, // Primary site is doing planned failover
    PRIMARY_FAILING_OVER, // Primary site is doing unplanned failover
    STANDBY_ADDING, // Standby site. Adding site
    STANDBY_SYNCING, // Standby site. Syncing from an active one
    STANDBY_SYNCED,  // Standby site. Synced with active one 
    STANDBY_PAUSED,  // Standby site. Replication is paused 
    STANDBY_TESTING, // Standby site. Run DR testing
    STANDBY_SWITCHING_OVER, // Standby site is doing planned failover
    STANDBY_FAILING_OVER, // Standby site is doing unplanned failover
    STANDBY_REMOVING, // Standby site. Removing
    STANDBY_RESUMING, // Standby site. Resuming
    STANDBY_ERROR    // Unrecoverable error for this standby site
}
