/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;


/**
 * Derived Recommendation that adds information about the VPlex storage system
 * associated with the recommendation as well as the VirtualArray for the
 * recommendation.
 */
@SuppressWarnings("serial")
public class VPlexRecommendation extends Recommendation {
    
    // The VPlex storage system.
    private URI vplexStorageSystem;
    private List<URI> backendNetworkList;
    
    /**
     * Getter for the VPlex storage system for the recommendation.
     * 
     * @return The VPlex storage system for the recommendation.
     */
       
     public URI getVPlexStorageSystem() {     
        return this.vplexStorageSystem;
    } 

     /**
      * Getter for the VPlex back-end network for recommendation.
      *
      * @return the Backend Network for recommendation.
      */
     public List<URI> getBackendNetworkList() {
    	 return this.backendNetworkList;
     }
    
    /**
     * Setter for the VPlex storage system for the recommendation.
     * 
     * @param vplexStorageSystem The VPlex storage system for the recommendation.
     */
   
     public void setVPlexStorageSystem(URI vplexStorageSystem) {     
        this.vplexStorageSystem = vplexStorageSystem;
    }

     /**
      * Setter for the VPlex back-end network for recommendation.
      * @param backendNetwork - The back-end network for recommendation.
      */
     public void setBackendNetworkList(URI backendNetwork_1, URI backendNetwork_2){
    	 backendNetworkList = new ArrayList<URI>();
    	 this.backendNetworkList.add(backendNetwork_1);
	 this.backendNetworkList.add(backendNetwork_2);
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
