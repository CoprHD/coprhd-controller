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

import java.util.StringTokenizer;

/**
 * Info for a storage system accessible by the VPlex
 */
public class VPlexStorageSystemInfo extends VPlexResourceInfo {
    
    // Constants used in forming the native guids for storage systems.
    private static final String VPLEX_NAME_DELIM = "-";
    
    // The unique id for the storage system
    private String uniqueId;
    
    // The id of the VPlex cluster to which the array is attached.
    private String clusterId;
    
    /**
     * Getter for the storage system unique id, which could be the
     * serial number if it could be determined, or the whole nativeId
     * string from the VPLEX API if it could not be determined.
     * 
     * @return a unique id for the storage system.
     */
    public String getUniqueId() {
        return uniqueId;
    }
    
   

    public boolean matches( String storageSystemNativeGuid ) {


        if (storageSystemNativeGuid.endsWith(getUniqueId())) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Creates a unique id for the storage system based on the VPlex system
     * name.  If the serial number can be determined (the information after the
     * last "-" character), it will be used.  Otherwise, the whole nativeId
     * from the VPLEX will be used.
     */
    public void buildUniqueId() throws VPlexApiException {
        String name = getName();

        if (!name.contains(VPLEX_NAME_DELIM)) {
            s_logger.warn("unexpected native guid format: " + name);
            uniqueId = name;
            return;
        }

        int lastDelimIndex = name.lastIndexOf(VPLEX_NAME_DELIM);
        String suffix = name.substring(lastDelimIndex + 1);
        s_logger.info("setting unique id for {} to {}", name, suffix);
        uniqueId = suffix;
    }
    
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
        str.append("StorageSystemInfo ( ");
        str.append(super.toString());
        str.append(", nativeGuid: " + uniqueId);
        str.append(", clusterId: " + clusterId);
        str.append(" )");
        return str.toString();
    }
}
