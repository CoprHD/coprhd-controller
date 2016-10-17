/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.Feature;

public class FeatureRequest extends KHRequests<Feature>{
    private static final String URL = "/api/instances/feature/";
    private static final String FIELDS = "state";
    
    public FeatureRequest(KHClient client, String id) {
        super(client);
        _url = URL + id;
        _fields = FIELDS;
    }

    public Feature get() {
        return getDataForOneObject(Feature.class);
    }

}
