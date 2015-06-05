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

package com.emc.sa.model.mock;

import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.common.Service;

/**
 * Dummy beacon implementation for unit tests
 */
public class StubBeaconImpl implements ServiceBeacon {
    private Service _service;

    public StubBeaconImpl(Service service) {
        _service = service;
    }

    @Override
    public Service info() {
        return _service;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
