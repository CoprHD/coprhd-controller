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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;

@SuppressWarnings("serial")
public class Protection implements Serializable {
    // Target (for protection only)
	// The target virtual array for the recommendation.
    private URI targetVarray;
    // The target vpool for the recommendation
    private VirtualPool targetVpool;
    private URI targetStorageSystem;
    private URI targetVplexStorageSystem; //if target is a VPLEX virtual device
    private URI targetStoragePool;   
    private String targetInternalSiteName;
    // This is the Storage System that was chosen by placement for connectivity/visibility to the RP Cluster
    private URI targetInternalSiteStorageSystem;
    private URI targetJournalStorageSystem;   
    private URI targetJournalVplexStorageSystem;
    private URI targetJournalStoragePool;
    private URI targetJournalVarray;
    private URI targetJournalVpool;
    private ProtectionType protectionType;
   
       
    private List<Recommendation> targetVPlexHaRecommendations;
    
    public static enum ProtectionType {
    	LOCAL,
    	REMOTE
    }
    
    public URI getTargetStorageSystem() {
        return targetStorageSystem;
    }
    
    public void setTargetStorageSystem(URI targetStorageSystem) {
    	this.targetStorageSystem = targetStorageSystem;
    }

    public URI getTargetStoragePool() {
        return targetStoragePool;
    }
    
	public void setTargetStoragePool(URI targetStoragePool) {
        this.targetStoragePool = targetStoragePool;
    }

    public URI getTargetJournalStoragePool() {
        return targetJournalStoragePool;
    }
    
	public void setTargetJournalStoragePool(URI targetJournalStoragePool) {
		this.targetJournalStoragePool = targetJournalStoragePool;
    }
		
	public URI getTargetJournalVarray() {
		return targetJournalVarray;
	}

	public void setTargetJournalVarray(URI targetJournalVarray) {
		this.targetJournalVarray = targetJournalVarray;
	}

	public URI getTargetJournalVpool() {
		return targetJournalVpool;
	}

	public void setTargetJournalVpool(URI targetJournalVpool) {
		this.targetJournalVpool = targetJournalVpool;
	}	

    public String getTargetInternalSiteName() {
        return targetInternalSiteName;
    }
    
    public void setTargetInternalSiteName(String targetInternalSiteName) {
    	this.targetInternalSiteName = targetInternalSiteName;
    }

	public URI getTargetJournalDevice() {
		return targetJournalStorageSystem;
	}

	public void setTargetJournalDevice(URI _targetJournalStorageSystem) {
		this.targetJournalStorageSystem = _targetJournalStorageSystem;
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

	public URI getTargetVplexStorageSystem() {
		return targetVplexStorageSystem;
	}

	public void setTargetVplexStorageSystem(URI targetVplexStorageSystem) {
		this.targetVplexStorageSystem = targetVplexStorageSystem;
	}
	
	public URI getTargetJournalVplexStorageSystem() {
		return targetJournalVplexStorageSystem;
	}

	public void setTargetJournalVplexStorageSystem(
			URI targetJournalVplexStorageSystem) {
		this.targetJournalVplexStorageSystem = targetJournalVplexStorageSystem;
	}	
	
    @Override
	public String toString() {
		return "Protection [_targetStorageSystem =" + targetStorageSystem
				+ ", _targetPool =" + targetStoragePool
				+ ", _targetJournalPool =" + targetJournalStoragePool
				+ ", _targetInternalSiteName =" + targetInternalSiteName
				+ ", _targetInternalSiteStorageSystem =" + targetInternalSiteStorageSystem
				+ ", _tagetJournalStorageSystem =" + targetJournalStorageSystem
				+ "]";
	}

    public String toString(DbClient _dbClient) {
    	return "NIY";
    }
}
