/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

/**
 * Info for a VPlex extent.
 */
public class VPlexExtentInfo extends VPlexResourceInfo {

    // Info about the storage volume for this extent.
    private VPlexStorageVolumeInfo storageVolumeInfo = null;

    // The cluster id.
    private String clusterId = null;

    /**
     * Getter for the storage volume info for the extent.
     * 
     * @return The storage volume info for the extent.
     */
    public VPlexStorageVolumeInfo getStorageVolumeInfo() {
        return storageVolumeInfo;
    }

    /**
     * Setter for the storage volume info for the extent.
     * 
     * @param volumeInfo The storage volume info for the extent.
     */
    public void setStorageVolumeInfo(VPlexStorageVolumeInfo volumeInfo) {
        storageVolumeInfo = volumeInfo;
    }

    /**
     * Getter for the extent cluster id.
     * 
     * @return The extent cluster id.
     */
    public String getClusterId() {
        return clusterId;
    }

    /**
     * Setter for the extent cluster id.
     * 
     * @param id The extent cluster id.
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
        str.append("ExtentInfo ( ");
        str.append(super.toString());
        str.append(", clusterId: " + clusterId);
        str.append(", storageVolumeInfo: " + (storageVolumeInfo == null ? "null" : storageVolumeInfo.toString()));
        str.append(" )");
        return str.toString();
    }
}
