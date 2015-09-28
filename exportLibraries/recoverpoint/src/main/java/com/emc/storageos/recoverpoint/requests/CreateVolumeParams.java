/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.requests;

import java.io.Serializable;
import java.net.URI;

/**
 * Every volume in a consistency group, even journals, are represented here.
 * 
 */
@SuppressWarnings("serial")
public class CreateVolumeParams implements Serializable {
    private URI volumeURI;
    private String internalSiteName;
    private URI virtualArray;
    private boolean production;
    private URI storageSystem;
    private String wwn;
    private String rpCopyName;
    private String nativeGuid;

    public URI getVolumeURI() {
        return volumeURI;
    }

    public void setVolumeURI(URI volumeURI) {
        this.volumeURI = volumeURI;
    }

    public String getInternalSiteName() {
        return internalSiteName;
    }

    public void setInternalSiteName(String internalSiteName) {
        this.internalSiteName = internalSiteName;
    }

    public URI getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
    }

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean production) {
        this.production = production;
    }

    public URI getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    public String getRpCopyName() {
        return rpCopyName;
    }

    public void setRpCopyName(String rpCopyName) {
        this.rpCopyName = rpCopyName;
    }

    public String getNativeGuid() {
		return nativeGuid;
	}

	public void setNativeGuid(String nativeGuid) {
		this.nativeGuid = nativeGuid;
	}

	@Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\n\tvolumeURI:        " + volumeURI);
        sb.append("\n\tinternalSiteName: " + internalSiteName);
        sb.append("\n\tvirtualArray:     " + virtualArray);
        sb.append("\n\tproduction:       " + production);
        sb.append("\n\tstorageSystem:    " + storageSystem);
        sb.append("\n\twwn:              " + wwn);
        sb.append("\n\trpCopyName:       " + rpCopyName);
        sb.append("\n\tnativeGuid:       " + nativeGuid);
        return sb.toString();
    }
}
