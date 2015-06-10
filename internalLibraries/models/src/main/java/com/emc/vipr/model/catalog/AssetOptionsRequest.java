/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class AssetOptionsRequest {

    private URI tenantId;
    private Map<String, String> availableAssets;

    public URI getTenantId() {
        return tenantId;
    }

    public void setTenantId(URI tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, String> getAvailableAssets() {
        if (availableAssets == null) {
            availableAssets = new HashMap<>();
        }
        return availableAssets;
    }

    public void setAvailableAssets(Map<String, String> availableAssets) {
        this.availableAssets = availableAssets;
    }
}
