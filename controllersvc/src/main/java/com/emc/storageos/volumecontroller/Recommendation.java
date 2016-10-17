/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
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
    // Underlying Recommendation this Recommendation is built on
    private Recommendation recommendation;
	
	// The virtual array for the recommendation.
    private URI virtualArray;
    
    // The virtual pool used to get the recommendation
    private VirtualPool virtualPool;
    
    private URI sourceStorageSystem;
    private URI sourceStoragePool;
    private String deviceType;
    private int resourceCount;
    
    /**
     * Getter for recommended Virtual Array 
     * @return Recommended Virtual Array
     */
    public URI getVirtualArray() {
		return virtualArray;
	}

	/**
	 * Setter for recommended Virtual Array 
	 * @param virtualArray Recommended Virtual Array 
	 */
	public void setVirtualArray(URI virtualArray) {
		this.virtualArray = virtualArray;
	}

	/** Getter for recommended Virtual Pool
	 * @return Recommended Virtual Pool
	 */
	public VirtualPool getVirtualPool() {
		return virtualPool;
	}

	/**
	 *  Setter for recommended Virtual Pool
	 * @param virtualPool Recommended Virtual Pool
	 */
	public void setVirtualPool(VirtualPool virtualPool) {
		this.virtualPool = virtualPool;
	}
    
    /**
     * Getter for Resource count
     * @return Resource count 
     */
    public int getResourceCount() {
        return resourceCount;
    }

    /**
     * Setter for Resource count
     * @param resourceCount Resource Count
     */
    public void setResourceCount(int resourceCount) {
        this.resourceCount = resourceCount;
    }

    /**
     * Getter for recommended Storage Pool
     * @return Recommended Storage Pool
     */
    public URI getSourceStoragePool() {
        return sourceStoragePool;
    }

    /**
     * Setter for recommended storage pool
     * @param sourceStoragePool
     */
    public void setSourceStoragePool(URI sourceStoragePool) {
        this.sourceStoragePool = sourceStoragePool;
    }

    /**
     * Getter for recommended storage system
     * @return Recommended storage system
     */
    public URI getSourceStorageSystem() {
        return sourceStorageSystem;
    }

    /**
     * Setter for recommended storage system
     * @param sourceStorageSystem Recommended Storage System
     */
    public void setSourceStorageSystem(URI sourceStorageSystem) {
        this.sourceStorageSystem = sourceStorageSystem;
    }

	/**
	 * Getter for recommended device type
	 * @return Recommended device type
	 */
	public String getDeviceType() {
		return deviceType;
	}

	/**
	 * Setter for recommended device type
	 * @param deviceType Recommended device type
	 */
	public void setDeviceType(String deviceType) {
		this.deviceType = deviceType;
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

    public Recommendation getRecommendation() {
                return recommendation;
            }

    public void setRecommendation(Recommendation recommendation) {
                this.recommendation = recommendation;
            }
}
