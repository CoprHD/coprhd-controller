/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;

/**
 * Recommendation for a placement is a storage pool and its storage device.
 */
@SuppressWarnings("serial")
public class Recommendation implements Serializable {
    private URI _sourceDevice;
    private URI _sourcePool;
    private String _deviceType;
    private int _resourceCount;

    public int getResourceCount() {
        return _resourceCount;
    }

    public void setResourceCount(int resourceCount) {
        this._resourceCount = resourceCount;
    }

    public URI getSourcePool() {
        return _sourcePool;
    }

    public void setSourcePool(URI _sourcePool) {
        this._sourcePool = _sourcePool;
    }

    public URI getSourceDevice() {
        return _sourceDevice;
    }

    public void setSourceDevice(URI _sourceDevice) {
        this._sourceDevice = _sourceDevice;
    }

    public String getDeviceType() {
        return _deviceType;
    }

    public void setDeviceType(String _deviceType) {
        this._deviceType = _deviceType;
    }

}
