/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_volume_create")
public class XtremIOVolumeCreate {
    @SerializedName("vol-name")
    @JsonProperty(value = "vol-name")
    private String name;

    @SerializedName("vol-size")
    @JsonProperty(value = "vol-size")
    private String size;

    @SerializedName("parent-folder-id")
    @JsonProperty(value = "parent-folder-id")
    private String parentFolderId;
    
    @SerializedName("cluster-id")
    @JsonProperty(value = "cluster-id")
    private String clusterName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(String parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    @Override
    public String toString() {
        return "XtremIOVolumeCreate [name=" + name + ", size=" + size + ", parentFolderId=" + parentFolderId + ", clusterName="
                + clusterName + "]";
    }
}
