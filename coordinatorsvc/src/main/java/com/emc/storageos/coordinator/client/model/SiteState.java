/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

/**
 * The state of site, used to track state transaction during disaster recovery.
 **/
public enum SiteState {
    /**
     * Active site. Eligible for all provisioning operations
     */
    ACTIVE {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    },

    /**
     * Active site is doing planned failover
     */
    ACTIVE_SWITCHING_OVER {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },

    /**
     * Active site is doing failover
     */
    ACTIVE_FAILING_OVER {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },
    
    /**
     * Active site is back after failover, site has been down graded.
     */
    ACTIVE_DEGRADED {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    },

    /**
     *  Standby site. Adding site
     */
    STANDBY_ADDING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },

    /**
     *  Standby site. Syncing from an active one
     */
    STANDBY_SYNCING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },

    /**
     *  Standby site. Synced with active one
     */
    STANDBY_SYNCED {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    },

    /**
     *  Standby site. Replication is being paused
     */
    STANDBY_PAUSING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },

    /**
     *  Standby site. Replication is paused
     */
    STANDBY_PAUSED {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    },

    /**
     *  Standby site. Db is being excluded from strategy options
     */
    STANDBY_DEGRADING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },

    /**
     *  Standby site. Db is excluded from strategy options
     */
    STANDBY_DEGRADED {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    },

    /**
     *  Standby site. Run DR testing
     */
    STANDBY_TESTING {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    },

    /**
     *  Standby site is doing planned failover
     */
    STANDBY_SWITCHING_OVER {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },

    /**
     * Standby site is doing failover
     */
    STANDBY_FAILING_OVER {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },

    /**
     *  Standby site. Removing
     */
    STANDBY_REMOVING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },

    /**
     *  Standby site. Resuming
     */
    STANDBY_RESUMING {
        @Override
        public boolean isDROperationOngoing() {
            return true;
        }
    },

    /**
     *  Unrecoverable error for this standby site
     */
    STANDBY_ERROR {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    },
    
    /**
     *  None state for dummy active site
     */
    NONE {
        @Override
        public boolean isDROperationOngoing() {
            return false;
        }
    };

    /**
     * Check if this SiteState indicates that a DR Operation is ongoing
     *
     * @return True if there is a DR Operation ongoing, false otherwise
     */
    public abstract boolean isDROperationOngoing();
}
