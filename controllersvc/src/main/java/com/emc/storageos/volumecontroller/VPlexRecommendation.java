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

/**
 * Derived Recommendation that adds information about the VPlex storage system
 * associated with the recommendation as well as the VirtualArray for the
 * recommendation.
 */
@SuppressWarnings("serial")
public class VPlexRecommendation extends Recommendation {
    
    // The VPlex storage system.
    private URI _vplexStorageSystem;
    
    @Override
	public String toString() {
    	StringBuffer ret = new StringBuffer();
 
    	ret.append("VPlexRecommendation [_vplexStorageSystem=" + getVPlexStorageSystem()
						+ ", _varray=" + getVirtualArray()
						+ ", _vpool=" + getVirtualPool()
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
}
