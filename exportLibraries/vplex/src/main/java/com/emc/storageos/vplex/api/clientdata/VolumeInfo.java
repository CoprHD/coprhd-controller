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
package com.emc.storageos.vplex.api.clientdata;

import java.io.Serializable;
import com.emc.storageos.vplex.api.VPlexApiConstants;

/**
 * Bean specifying native volume information. Is passed from the client to
 * identify native backend volumes.
 */
public class VolumeInfo implements Serializable {
    private static final long serialVersionUID = 8156741332010435117L;
    
    private static final String DOT_OPERATOR = ".";
    
    private static final String UNDERSCORE_OPERATOR = "_";

    // The native Guid for the storage system that owns the volume.
    private String _storageSystemNativeGuid;
    
    // The WWN for the volume. 
    private String _volumeWWN;
    
    // The native volume id.
    private String _volumeNativeId;
    
    // The name for the claimed storage volume.
    private String _volumeName;
    
    // Whether or not the volume is thin provisioned.
    private boolean _isThin = false;
    
    /**
     * Constructor.
     * 
     * @param storageSystemNativeGuid The native guid for the storage system.
     * @param volumeWWN The WWN of the volume in all caps and no colons.
     * @param volumeNativeId The naive id of the backend volume.
     * @param true if the volume is thin provisioned, false otherwise
     */
    public VolumeInfo(String storageSystemNativeGuid, String volumeWWN,
        String volumeNativeId, boolean isThin) {
        _storageSystemNativeGuid = storageSystemNativeGuid;
        _volumeWWN = volumeWWN;
        _volumeNativeId = volumeNativeId;
        _isThin = isThin;
    }
    
    /**
     * Getter for the storage system native guid.
     * 
     * @return The storage system native guid.
     */
    public String getStorageSystemNativeGuid() {
        return _storageSystemNativeGuid;
    }
    
    /**
     * Setter for the storage system native guid.
     * 
     * @param storageSystemNativeGuid The storage system native guid.
     */
    public void setStorageSystemNativeGuid(String storageSystemNativeGuid) {
        _storageSystemNativeGuid = storageSystemNativeGuid;
    }
    
    /**
     * Getter for the volume WWN.
     * 
     * @return The volume WWN.
     */
    public String getVolumeWWN() {
        return _volumeWWN;
    }
    
    /**
     * Setter for the volume WWN.
     * 
     * @param volumeWWN The volume WWN in all caps and no colons.
     */
    public void setVolumeWWN(String volumeWWN) {
        _volumeWWN = volumeWWN;
    }
    
    /**
     * Getter for the volume native id.
     * 
     * @return The volume native id.
     */
    public String getVolumeNativeId() {
        return _volumeNativeId;
    }
    
    /**
     * Setter for the volume native id.
     * 
     * @param volumeName The volume native id.
     */    
    public void setVolumeNativeId(String volumeNativeId) {
        _volumeNativeId = volumeNativeId;
    }
    
    /**
     * Getter for the volume name. When not set specifically, we generate the
     * name by appending the serial number of the array, extracted from the 
     * array native guid, to the native volume id. This is the name given the
     * volume when it is claimed.
     * 
     * @return The volume name.
     */
    public String getVolumeName() {
        // Note that we need to prepend the prefix because the VPlex does not 
        // like the claimed storage volume name to start with a number, which 
        // can  be the case for Symmetrix volumes, whose serial numbers start 
        // with a number.
    	if ((_volumeName == null) || (_volumeName.length() == 0)) {
    		StringBuilder nameBuilder = new StringBuilder();
    		nameBuilder.append(VPlexApiConstants.VOLUME_NAME_PREFIX);
    		if (this._storageSystemNativeGuid.contains(DOT_OPERATOR)) {
    			this._storageSystemNativeGuid = this._storageSystemNativeGuid.replace(
    					DOT_OPERATOR, UNDERSCORE_OPERATOR);
    		}
    		nameBuilder.append(_storageSystemNativeGuid.substring(_storageSystemNativeGuid.indexOf("+") + 1));
    		nameBuilder.append("-");
    		nameBuilder.append(_volumeNativeId);    		   		
    		return nameBuilder.toString();
    	} else {
    		return _volumeName;
    	}
    }
    
    /**
     * Setter for the volume name.
     * 
     * @param volumeName The name to give the volume when claimed.
     */    
    public void setVolumeName(String volumeName) {
        _volumeName = volumeName;
    }
    
    /**
     * Getter returns whether or not the volume is thin provisioned.
     * 
     * @return true if the volume is thin provisioned, false otherwise.
     */
    public boolean getIsThinProvisioned() {
        return _isThin;
    }
    
    /**
     * Setter for whether or not the volume is thin provisioned.
     * 
     * @param isThin true if the volume is thin provisioned, false otherwise.
     */    
    public void setIsThinProvisioned(boolean isThin) {
        _isThin = isThin;
    }
	
}
