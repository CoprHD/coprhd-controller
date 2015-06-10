/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class AssetDependencyResponse {
    
    private String assetType;
    private List<String> assetDependencies;

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public List<String> getAssetDependencies() {
        if (assetDependencies == null) {
            assetDependencies = new ArrayList<>();
        }
        return assetDependencies;
    }

    public void setAssetDependencies(List<String> assetDependencies) {
        this.assetDependencies = assetDependencies;
    }

}
