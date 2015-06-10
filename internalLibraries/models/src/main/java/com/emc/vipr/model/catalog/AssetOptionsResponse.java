/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class AssetOptionsResponse {

    private String assetType;
    private Map<String, String> availableAssets;
    private List<AssetOption> options;

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public Map<String, String> getAvailableAssets() {
        if (this.availableAssets == null) {
            this.availableAssets = new HashMap<>();
        }
        return availableAssets;
    }

    public void setAvailableAssets(Map<String, String> availableAssets) {
        this.availableAssets = availableAssets;
    }

    public List<AssetOption> getOptions() {
        if (this.options == null) {
            this.options = new ArrayList<>();
        }
        return options;
    }

    public void setOptions(List<AssetOption> options) {
        this.options = options;
    }
}
