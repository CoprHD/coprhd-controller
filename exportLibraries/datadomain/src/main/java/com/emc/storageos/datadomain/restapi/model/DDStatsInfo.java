/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class DDStatsInfo {
	
    @SerializedName("resource_name")
    @JsonProperty(value="resource_name")
    private String resourceName;
	
    @SerializedName("retention_info")
    @JsonProperty(value="retention_info")
    private DDRetentionInfo retentionInfo;
	
    private DDRestLinkRep link;

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public DDRetentionInfo getRetentionInfo() {
        return retentionInfo;
    }

    public void setRetentionInfo(DDRetentionInfo retentionInfo) {
        this.retentionInfo = retentionInfo;
    }

    public DDRestLinkRep getLink() {
        return link;
    }

    public void setLink(DDRestLinkRep link) {
        this.link = link;
    }
	
	public String toString() {
        return new Gson().toJson(this).toString();
    }

}
