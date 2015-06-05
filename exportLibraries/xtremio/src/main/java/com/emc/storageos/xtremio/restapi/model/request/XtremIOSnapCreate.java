/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

public class XtremIOSnapCreate {

    @SerializedName("ancestor-vol-id")
    @JsonProperty(value = "ancestor-vol-id")
    private String parentName;
    
    @SerializedName("snap-vol-name")
    @JsonProperty(value = "snap-vol-name")
    private String snapName;
    
    @SerializedName("folder-id")
    @JsonProperty(value = "folder-id")
    private String folderId;

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public String getSnapName() {
        return snapName;
    }

    public void setSnapName(String snapName) {
        this.snapName = snapName;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }
    
    public String toString() {
        return "ancestor-vol-id: " + parentName + ". snap-vol-name: " + snapName + ", folder-id: " + folderId;
    }
}
