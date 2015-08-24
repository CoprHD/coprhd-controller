/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;

import com.emc.storageos.db.client.model.VirtualPool;

/**
 * Recommendation for a placement is a storage pool and its storage device.
 */
@SuppressWarnings("serial")
public class Recommendation implements Serializable {
	
	// The virtual array for the recommendation.
    private URI virtualArray;
    // The vpool used to get the recommendation
    private VirtualPool virtualPool;
    
    private URI sourceStorageSystem;
    private URI sourceStoragePool;
    private String deviceType;
    private int resourceCount;
    
    public URI getVirtualArray() {
		return virtualArray;
	}

	public void setVirtualArray(URI virtualArray) {
		this.virtualArray = virtualArray;
	}

	public VirtualPool getVirtualPool() {
		return virtualPool;
	}

	public void setVirtualPool(VirtualPool virtualPool) {
		this.virtualPool = virtualPool;
	}
    
    public int getResourceCount() {
        return resourceCount;
    }

    public void setResourceCount(int resourceCount) {
        this.resourceCount = resourceCount;
    }

    public URI getSourceStoragePool() {
        return sourceStoragePool;
    }

    public void setSourceStoragePool(URI _sourceStoragePool) {
        this.sourceStoragePool = _sourceStoragePool;
    }

    public URI getSourceStorageSystem() {
        return sourceStorageSystem;
    }

    public void setSourceStorageSystem(URI _sourceStorageSystem) {
        this.sourceStorageSystem = _sourceStorageSystem;
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
		buffer.append("Source Storage System : " + getSourceStorageSystem().toString() + "\n");
		buffer.append("Source Storage Pool : " + getSourceStoragePool().toString() + "\n");
		buffer.append("Device Type : " + getDeviceType() + "\n");
		buffer.append("Resource Count : " + getResourceCount() + "\n");
		buffer.append("--------------------------------------------\n");
		return buffer.toString();
	}
}
