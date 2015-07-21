/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;


import java.net.URI;
import java.util.List;

import com.emc.storageos.volumecontroller.Recommendation;

/**
 *  Recommendation with added support for storage ports. 
 */
public class FileRecommendation extends Recommendation {

    private static final long serialVersionUID = 1L;
    private List<URI> _storagePortUris;
    
    public FileRecommendation(Recommendation recommendation) {
        setDeviceType(recommendation.getDeviceType());
        setSourceDevice(recommendation.getSourceDevice());
        setSourcePool(recommendation.getSourcePool());
        setResourceCount(recommendation.getResourceCount());
    }
    
    public FileRecommendation() {};

    public List<URI> getStoragePorts() {
        return _storagePortUris;
    }

    public void setStoragePorts(List<URI> storagePortUris) {
        this._storagePortUris = storagePortUris;
    }
    
}
