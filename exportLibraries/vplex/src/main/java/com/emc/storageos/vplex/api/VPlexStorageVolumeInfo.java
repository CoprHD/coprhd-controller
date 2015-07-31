/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;


/**
 * Info for a VPlex storage volume
 */
public class VPlexStorageVolumeInfo extends VPlexResourceInfo {

    // The id of the VPlex cluster to which the storage volume belongs.
    private String clusterId;

	/**
     * Getter for the storage system cluster id.
     * 
     * @return The storage system cluster id.
     */
    public String getClusterId() {
        return clusterId;
    }

    /**
     * Setter for the storage system cluster id.
     * 
     * @param id The storage system cluster id.
     */
    public void setClusterId(String id) {
        clusterId = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("StorageVolumeInfo ( ");
        str.append(super.toString());
        str.append(", clusterId: " + clusterId);        
        str.append(" )");
        return str.toString();
    }
}
