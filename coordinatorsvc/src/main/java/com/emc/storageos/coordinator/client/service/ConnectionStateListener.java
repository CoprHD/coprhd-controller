/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.service;

/**
 * Implement this interface and register with CoordinatorClient to
 * listen to connection state changes
 */
public interface ConnectionStateListener {
    public static enum State {
        // when connection is lost, all runtime state (including locks) created from
        // coordinator client should be considered no longer valid. for example, this means
        // that distributed locks are no longer held
        DISCONNECTED,

        // called when connection is (re)established
        CONNECTED
    }

    /**
     * Called when connection state changes. Don't do any long running / block IO in
     * here. There may be other modules waiting for a notification.
     * 
     * @param state
     */
    public void connectionStateChanged(State state);
}
