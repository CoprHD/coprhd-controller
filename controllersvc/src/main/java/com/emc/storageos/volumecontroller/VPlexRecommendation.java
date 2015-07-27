/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.db.client.model.VirtualPool;

/**
 * Derived Recommendation that adds information about the VPlex storage system
 * associated with the recommendation as well as the VirtualArray for the
 * recommendation.
 */
@SuppressWarnings("serial")
public class VPlexRecommendation extends RPProtectionRecommendation {
    
    // The VPlex storage system.
    private URI _vplexStorageSystem;
    
    // The virtual array for the recommendation.
    private URI _varray;
    
    // The vpool used to get the recommendation
    private VirtualPool _vpool;
    
    @Override
	public String toString() {
    	StringBuffer ret = new StringBuffer();
    	if (super.getSourceInternalSiteName() != null) {
    		ret.append(super.toString());
    	}
    	
    	ret.append("VPlexRecommendation [_vplexStorageSystem=" + _vplexStorageSystem
						+ ", _varray=" + _varray
						+ ", _vpool=" + _vpool
						+ "]");
    	
		return ret.toString();
	}
    
    /**
     * Getter for the VPlex storage system for the recommendation.
     * 
     * @return The VPlex storage system for the recommendation.
     */
    public URI getVPlexStorageSystem() {
        return _vplexStorageSystem;
    }
    
    /**
     * Setter for the VPlex storage system for the recommendation.
     * 
     * @param vplexStorageSystem The VPlex storage system for the recommendation.
     */
    public void setVPlexStorageSystem(URI vplexStorageSystem) {
        _vplexStorageSystem = vplexStorageSystem;
    }
    
    /**
     * Getter for the varray for this recommendation.
     * 
     * @return The varray for this recommendation.
     */
    public URI getVirtualArray() {
        return _varray;
    }
    
    /**
     * Setter for the varray for this recommendation.
     * 
     * @param varray The varray for this recommendation.
     */
    public void setVirtualArray(URI varray) {
        _varray = varray;
    }
    
    /**
     * Getter for the VirtualPool for this recommendation.
     * 
     * @return The VirtualPool for this recommendation.
     */
    public VirtualPool getVirtualPool() {
        return _vpool;
    }
    
    /**
     * Setter for the VirtualPool for this recommendation.
     * 
     * @param vpool The VirtualPool for this recommendation.
     */
    public void setVirtualPool(VirtualPool vpool) {
        _vpool = vpool;
    }
}
