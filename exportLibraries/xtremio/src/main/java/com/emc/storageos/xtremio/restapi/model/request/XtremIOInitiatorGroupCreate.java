/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

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

    public String toString() {
        return "ig-name: " + name + ". parent-folder-id: " + parentFolderId;
    }

}
