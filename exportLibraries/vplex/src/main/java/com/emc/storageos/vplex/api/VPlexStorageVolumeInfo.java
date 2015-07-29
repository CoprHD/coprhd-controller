/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
