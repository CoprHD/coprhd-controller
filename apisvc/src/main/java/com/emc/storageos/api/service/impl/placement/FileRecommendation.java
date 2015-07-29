/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.List;

import com.emc.storageos.volumecontroller.Recommendation;

/**
 * Recommendation with added support for storage ports.
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

    public FileRecommendation() {
    }

    public List<URI> getStoragePorts() {
        return _storagePortUris;
    }

    public void setStoragePorts(List<URI> storagePortUris) {
        this._storagePortUris = storagePortUris;
    }

}
