/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;

@SuppressWarnings("serial")
public class Protection implements Serializable {
    // Target (for protection only)
	// The target virtual array for the recommendation.
    private URI targetVarray;
    // The target vpool for the recommendation
    private VirtualPool targetVpool;   
    private String targetInternalSiteName;
    // This is the Storage System that was chosen by placement for connectivity/visibility to the RP Cluster
    private URI targetInternalSiteStorageSystem;
    private RPRecommendation targetJournalRecommendation;
    private ProtectionType protectionType;
    //Map of storage pool to storage system for target protection
    private Map<URI, URI> protectionPoolStorageMap;
    
          
    private List<Recommendation> targetVPlexHaRecommendations;
    
    public static enum ProtectionType {
    	LOCAL,
    	REMOTE
    }
        
    public String getTargetInternalSiteName() {
        return targetInternalSiteName;
    }
    
    public void setTargetInternalSiteName(String targetInternalSiteName) {
    	this.targetInternalSiteName = targetInternalSiteName;
    }

	public ProtectionType getProtectionType() {
		return protectionType;
	}

	public void setProtectionType(ProtectionType protectionType) {
		this.protectionType = protectionType;
	}
	
	public URI getTargetInternalSiteStorageSystem() {
        return targetInternalSiteStorageSystem;
    }

    public void setTargetInternalSiteStorageSystem(
            URI targetInternalSiteStorageSystem) {
        this.targetInternalSiteStorageSystem = targetInternalSiteStorageSystem;
    }
    
    public URI getTargetVarray() {
		return targetVarray;
	}

	public void setTargetVarray(URI targetVarray) {
		this.targetVarray = targetVarray;
	}

	public VirtualPool getTargetVpool() {
		return targetVpool;
	}

	public void setTargetVpool(VirtualPool targetVpool) {
		this.targetVpool = targetVpool;
	}

	public List<Recommendation> getTargetVPlexHaRecommendations() {
		return targetVPlexHaRecommendations;
	}

	public void setTargetVPlexHaRecommendations(
			List<Recommendation> targetVPlexHaRecommendations) {
		this.targetVPlexHaRecommendations = targetVPlexHaRecommendations;
	}
	
	public Map<URI, URI> getProtectionPoolStorageMap() {
		return protectionPoolStorageMap;
	}

	public void setProtectionPoolStorageMap(Map<URI, URI> protectionPoolStorageMap) {
		this.protectionPoolStorageMap = protectionPoolStorageMap;
	}

	public RPRecommendation getTargetJournalRecommendation() {
		return targetJournalRecommendation;
	}

	public void setTargetJournalRecommendation(
			RPRecommendation targetJournalRecommendation) {
		this.targetJournalRecommendation = targetJournalRecommendation;
	}
	 
	public String toString(DbClient dbClient) {
    	StringBuffer buff = new StringBuffer();
    	StoragePool journalPool = dbClient.queryObject(StoragePool.class, getTargetJournalRecommendation().getSourcePool());
    	StorageSystem journalStorage = dbClient.queryObject(StorageSystem.class, getTargetJournalRecommendation().getSourceDevice());
    	buff.append("\tProtecting to Site : " + targetInternalSiteName + "\n");
    	buff.append("\tTarget Virtual Array :" + dbClient.queryObject(VirtualArray.class, this.getTargetVarray()).getLabel() + "\n");
    	buff.append("\tTarget VirtualPool : " + this.getTargetVpool().getLabel() + "\n");
    	buff.append("\tTarget Journal Storage Pool : " + journalPool.getLabel() + "\n");
    	buff.append("\tTarget Journal Storage System: " +journalStorage.getLabel()+ "\n");    	
    	buff.append("\tTarget Internal Storage System : " + targetInternalSiteStorageSystem + "\n");
    	buff.append("\tTarget Journal Virtual Array : " + dbClient.queryObject(VirtualArray.class, getTargetJournalRecommendation().getVirtualArray()).getLabel() + "\n");
    	buff.append("\tTarget Journal Virtual Pool : " + getTargetJournalRecommendation().getVirtualPool().getLabel());
    	buff.append("\tProtection Storage :\n");
    	for (Map.Entry<URI, URI> poolStorageMap : getProtectionPoolStorageMap().entrySet()) {
    		buff.append("\tTarget Storage System :" + poolStorageMap.getValue() + "\n");
    		buff.append("\tTarget Storage Pool : " + poolStorageMap.getKey() + "\n");    		
    	}    	    	    	
    	return buff.toString();
	}
}
