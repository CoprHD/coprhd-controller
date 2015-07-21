/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
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
