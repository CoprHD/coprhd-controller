/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class AssetDependencyRequest {

    private URI tenantId;
    private Set<String> availableAssetTypes;

    public URI getTenantId() {
        return tenantId;
    }

    public void setTenantId(URI tenantId) {
        this.tenantId = tenantId;
    }

    public Set<String> getAvailableAssetTypes() {
        if (availableAssetTypes == null) {
            availableAssetTypes = new HashSet<>();
        }
        return availableAssetTypes;
    }

    public void setAvailableAssetTypes(Set<String> availableAssetTypes) {
        this.availableAssetTypes = availableAssetTypes;
    }
}
