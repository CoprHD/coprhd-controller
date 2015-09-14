/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.FastVP;

public class FastVPRequest extends KHRequests<FastVP> {

    private final static String URL = "/api/types/fastVP/instances";

    public FastVPRequest(KHClient client) {
        super(client);
        _url = URL;
    }

    public List<FastVP> get() {
        return getDataForObjects(FastVP.class);
    }
}
