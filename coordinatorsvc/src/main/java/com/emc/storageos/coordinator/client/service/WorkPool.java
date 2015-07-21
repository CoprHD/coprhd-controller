/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;


import java.util.Set;

/**
 * Main API for coordinator backed distributed work assignment service
 */
public interface WorkPool {
    /**
     * Main API for work item
     */
    public interface Work {
        /**
         * Work item ID
         * @return
         */
        public String getId();

        /**
         * Attempts to work ownership.  If work is not owned by current client,
         * this method is a noop.
         *
         * @throws Exception
         */
        public void release() throws Exception;
    }
    
    /**
     * Listener interface for task assignments
     */
    public interface WorkAssignmentListener {
        /**
         * WorkPool notification will contain all currently assigned work items. There
         * may be redundant notification. It's client's responsibility to check its
         * current work set against assigned work set.
         *
         * @param work currently assigned work set
         */
        public void assigned(Set<Work> work) throws Exception;
    }

    /**
     * Add work item
     *
     * @param workId
     */
    public void addWork(String workId) throws Exception;

    /**
     * Remove work item
     *
     * @param workId
     */
    public void removeWork(String workId) throws Exception;

    /**
     * Starts work group.  If coordinator cluster is unavailable, start will retry
     * until it can establish connection with cluster.  Attempting to read or modify reservation
     * group while disconnected will result in error.
     */
    public void start() throws Exception;

    /**
     * Stops work group and releases all reservations from this client.
     */
    public void stop();
}
