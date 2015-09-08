/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_group_create")
public class XtremIOInitiatorGroupCreate {

    @SerializedName("ig-name")
    @JsonProperty(value = "ig-name")
    private String name;

    @SerializedName("parent-folder-id")
    @JsonProperty(value = "parent-folder-id")
    private String parentFolderId;
    
    @SerializedName("cluster-id")
    @JsonProperty(value = "cluster-id")
    private String clusterName;
    
    @SerializedName("tag-list")
    @JsonProperty(value = "tag-list")
    private List<String> tagList;

    public String getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(String parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public List<String> getTagList() {
        return tagList;
    }

    public void setTagList(List<String> tagList) {
        this.tagList = tagList;
    }

    @Override
    public String toString() {
        return "XtremIOInitiatorGroupCreate [name=" + name + ", parentFolderId=" + parentFolderId + ", clusterName=" + clusterName
                + ", tagList=" + tagList + "]";
    }

}
