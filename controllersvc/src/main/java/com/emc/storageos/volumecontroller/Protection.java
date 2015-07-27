/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;

import com.emc.storageos.db.client.DbClient;

@SuppressWarnings("serial")
public class Protection implements Serializable {
    // Target (for protection only)
    private URI targetDevice;
    private URI targetJournalDevice;
    private URI targetStoragePool;
    private URI targetJournalStoragePool;
    private String targetInternalSiteName;
    // This is the Storage System that was chosen by placement for connectivity/visibility to the RP Cluster
    private URI targetInternalSiteStorageSystem;
    private URI targetJournalVarray;
    private URI targetJournalVpool;
    private ProtectionType protectionType;
    
    public static enum ProtectionType {
    	LOCAL,
    	REMOTE
    }
    
    public URI getTargetDevice() {
        return targetDevice;
    }
    
    public void setTargetDevice(URI targetDevice) {
    	this.targetDevice = targetDevice;
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
		return targetJournalDevice;
	}

	public void setTargetJournalDevice(URI _targetJournalDevice) {
		this.targetJournalDevice = _targetJournalDevice;
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
	
    @Override
	public String toString() {
		return "Protection [_targetDevice=" + targetDevice
				+ ", _targetPool=" + targetStoragePool
				+ ", _targetJournalPool=" + targetJournalStoragePool
				+ ", _targetInternalSiteName=" + targetInternalSiteName
				+ ", _targetInternalSiteStorageSystem=" + targetInternalSiteStorageSystem
				+ ", _tagetJournalDevice =" + targetJournalDevice
				+ "]";
	}

    public String toString(DbClient _dbClient) {
    	return "NIY";
    }
}
