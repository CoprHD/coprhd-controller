/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.List;

import com.emc.storageos.volumecontroller.Recommendation;

/**
 * Recommendation with added support for storage ports.
 */
public class FileRecommendation extends Recommendation {
	
	public enum FileType {
        FILE_SYSTEM_DATA,            // user's data file
        FILE_SYSTEM_SOURCE,    // local mirror
        FILE_SYSTEM_COPY,      // full copy
        FILE_SYSTEM_TARGET     // remote mirror file
    };

    private static final long serialVersionUID = 1L;
    private List<URI> _storagePortUris;
    private URI vNASURI;
    private URI _id;
    private FileType fileType; 

    
	public FileRecommendation(Recommendation recommendation) {
        setDeviceType(recommendation.getDeviceType());
        setSourceStorageSystem(recommendation.getSourceStorageSystem());
        setSourceStoragePool(recommendation.getSourceStoragePool());
        setResourceCount(recommendation.getResourceCount());
    }
	
	public FileRecommendation(FileRecommendation fileRecommendation) {
        setDeviceType(fileRecommendation.getDeviceType());
        setSourceStorageSystem(fileRecommendation.getSourceStorageSystem());
        setSourceStoragePool(fileRecommendation.getSourceStoragePool());
        setResourceCount(fileRecommendation.getResourceCount());
        //set the file type
        setFileType(fileRecommendation.getFileType());
        
        //set vnas Server
        if(fileRecommendation.getvNAS() != null) {
            setvNAS(fileRecommendation.getvNAS());
        }
        
        //set the storageports
        if(fileRecommendation.getStoragePorts() != null && !fileRecommendation.getStoragePorts().isEmpty()) {
            setStoragePorts(fileRecommendation.getStoragePorts());
        }
    }
	
    public URI getId() {
        return _id;
    }

    public void setId(URI _id) {
        this._id = _id;
    }
    
    public FileRecommendation() {
    }

    public List<URI> getStoragePorts() {
        return _storagePortUris;
    }

    public void setStoragePorts(List<URI> storagePortUris) {
        this._storagePortUris = storagePortUris;
    }
    
    public URI getvNAS() {
		return vNASURI;
	}

	public void setvNAS(URI vNASURI) {
		this.vNASURI = vNASURI;
	}
	
	public FileType getFileType() {
		return fileType;
	}
	public void setFileType(FileType fileType) {
		this.fileType = fileType;
	}

}
