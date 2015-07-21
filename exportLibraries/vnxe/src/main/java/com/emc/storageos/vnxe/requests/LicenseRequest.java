/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.VNXeLicense;


public class LicenseRequest extends KHRequests<VNXeLicense>{
    private static final String URL = "/api/types/license/instances";
    
    public LicenseRequest(KHClient client) {
        super(client);
        _url = URL;
    }
    
    public List<VNXeLicense> get() {
        return getDataForObjects(VNXeLicense.class);
    }
}
