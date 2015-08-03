/*
 * Copyright 2015 EMC Corporation
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
    private URI sourceStorageSystem;
    private URI sourceStoragePool;
    private String deviceType;
    private int resourceCount;
    
    public int getResourceCount() {
        return resourceCount;
    }

    public void setResourceCount(int resourceCount) {
        this.resourceCount = resourceCount;
    }

    public URI getSourcePool() {
        return sourceStoragePool;
    }

    public void setSourcePool(URI _sourcePool) {
        this.sourceStoragePool = _sourcePool;
    }

    public URI getSourceDevice() {
        return sourceStorageSystem;
    }

    public void setSourceDevice(URI _sourceDevice) {
        this.sourceStorageSystem = _sourceDevice;
    }

	public String getDeviceType() {
		return deviceType;
	}

	public void setDeviceType(String _deviceType) {
		this.deviceType = _deviceType;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Recommendation results: \n");
		buffer.append("Source Storage System : " + getSourceDevice().toString() + "\n");
		buffer.append("Source Storage Pool : " + getSourcePool().toString() + "\n");
		buffer.append("Device Type : " + getDeviceType() + "\n");
		buffer.append("Resource Count : " + getResourceCount() + "\n");
		buffer.append("--------------------------------------------\n");
		return buffer.toString();
	}
}
