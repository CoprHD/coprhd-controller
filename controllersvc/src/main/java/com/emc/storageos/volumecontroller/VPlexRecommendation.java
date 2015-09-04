/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
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
    private URI vplexStorageSystem;
    
    /**
     * Getter for the VPlex storage system for the recommendation.
     * 
     * @return The VPlex storage system for the recommendation.
     */
       
     public URI getVPlexStorageSystem() {     
        return this.vplexStorageSystem;
    } 
    
    /**
     * Setter for the VPlex storage system for the recommendation.
     * 
     * @param vplexStorageSystem The VPlex storage system for the recommendation.
     */
   
     public void setVPlexStorageSystem(URI vplexStorageSystem) {     
        this.vplexStorageSystem = vplexStorageSystem;
    }
          
     @Override
 	public String toString() {
     	StringBuffer ret = new StringBuffer(); 
     	ret.append(String.format("%s %n", super.toString()));
     	ret.append(String.format("Vplex Recommendation : %n"));
     	ret.append(String.format("Vplex Storage System : %s %n", this.getVPlexStorageSystem().toString()));
     
 		return ret.toString();
 	}
}
