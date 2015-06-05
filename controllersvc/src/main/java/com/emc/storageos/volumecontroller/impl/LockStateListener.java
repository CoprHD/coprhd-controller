/**
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

package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.coordinator.client.service.ConnectionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens to coordinator connection state changes and cancels in flight operations
 * that rely on distributed locks
 */
public class LockStateListener implements ConnectionStateListener {
    private static final Logger _log = LoggerFactory.getLogger(LockStateListener.class);

    @Override
    public void connectionStateChanged(State state) {
        switch (state) {
            case DISCONNECTED: {
                // when this block is reached, assume all locks have been released
                _log.info("Disconnected from cluster");
                break;
            }
            case CONNECTED: {
                // when this block is reached, assume you can start issuing operations again / take locks
                _log.info("Connected to cluster");
            }
        }
    }
}
