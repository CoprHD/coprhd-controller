/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.locking;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPDeviceController;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * Provides lock timeout values from the configuration data.
 */
public class LockTimeoutValue {
    private static final Logger _log = LoggerFactory.getLogger(LockTimeoutValue.class);
    private static CoordinatorClient coordinator;
    private static final Integer ULTIMATE_DEFAULT = 3600;		// 3600 seconds == 1 hour
    private static Set<String> reported = new HashSet<String>();

    /**
     * Returns the lock timeout value in seconds.
     * 
     * @param type LockType
     * @return Integer number of seconds for timeout value
     */
    public static Integer get(LockType type) {
        try {
            Integer value = Integer.valueOf(ControllerUtils.getPropertyValueFromCoordinator(coordinator, type.getKey()));
            if (!reported.contains(type.name())) {
                _log.info(String.format("Timeout value for LockType %s is %d", type.name(), value));
                reported.add(type.name());
            }
            return value;
        } catch (Exception ex) {
            _log.info(String.format("Could not determine lock timeout for type %s, returning hard-wired default of %d", type.name(),
                    ULTIMATE_DEFAULT), ex);
            return ULTIMATE_DEFAULT;
        }
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

}
