/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

/**
 * The state of site, used to track state transaction during disaster recovery.
 * */
public enum SiteState {
    PRIMARY {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    }, // Primary site. Eligible for all provisioning operations
    PRIMARY_SWITCHING_OVER {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    }, //Primary site is doing planned failover
    STANDBY_ADDING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    }, // Standby site. Adding site
    STANDBY_SYNCING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    }, // Standby site. Syncing from an active one
    STANDBY_SYNCED {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    },  // Standby site. Synced with active one
    STANDBY_PAUSED {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    },  // Standby site. Replication is paused
    STANDBY_TESTING {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    }, // Standby site. Run DR testing
    STANDBY_SWITCHING_OVER {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    }, // Standby site is doing planned failover
    STANDBY_REMOVING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    }, // Standby site. Removing
    STANDBY_RESUMING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    }, // Standby site. Resuming
    STANDBY_ERROR {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    };    // Unrecoverable error for this standby site

    /**
     * Check if this SiteState indicates that a DR Operation is ongoing
     *
     * @return True if there is a DR Operation ongoing, false otherwise
     */
    public abstract boolean isDROperationOngoing();
}
