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
public class VPlexStorageVolumeInfo extends  VPlexResourceInfo {
    
    // The id of the VPlex cluster to which the storage volume belongs.
    private String clusterId;
    
    // The system-id of the storage volume 
    // which contains the backend volume WWN
    private String systemId;

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
     * Getter for the storage volume system id
     * containing the backend volume wwn.
     * 
     * @return the storage volume system id.
     */
    public String getSystemId() {
        return systemId;
    }

    /**
     * Setter for the storage volume system id.
     * 
     * @param systemId the storage volume system id.
     */
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }
    
    /**
     * Returns the WWN of this storage volume as
     * extracted from the storage volume system id.
     * The expected format is the WWN prefixed by
     * "VPD83T3", for example:
     *    VPD83T3:600601608d203700db57d68b5d2ae511
     * 
     * @return the WWN of this storage volume or null.
     */
    public String getWwn() {
        if (systemId != null) {
            if (systemId.startsWith(VPlexApiConstants.VOLUME_SYSTEM_ID_PREFIX)) {
                return systemId.substring(VPlexApiConstants.VOLUME_SYSTEM_ID_PREFIX.length());
            }
        }
        
        return null;
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
        str.append(", systemId: " + systemId);
        str.append(" )");
        return str.toString();
    }
}
