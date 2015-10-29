/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.List;

import com.emc.storageos.volumecontroller.Recommendation;

/**
 * Recommendation with added support for bucket.
 */
public class BucketRecommendation extends Recommendation {

    private static final long serialVersionUID = 1L;
    private List<URI> _storagePortUris;

    public BucketRecommendation(Recommendation recommendation) {
        setDeviceType(recommendation.getDeviceType());
        setSourceStorageSystem(recommendation.getSourceStorageSystem());
        setSourceStoragePool(recommendation.getSourceStoragePool());
        setResourceCount(recommendation.getResourceCount());
    }

    public BucketRecommendation() {
    }

    public List<URI> getStoragePorts() {
        return _storagePortUris;
    }

    public void setStoragePorts(List<URI> storagePortUris) {
        this._storagePortUris = storagePortUris;
    }

}
