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
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_volume_create")
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

    public String toString() {
    	return "Name: " + name + ". Size: " + size + ", Parent Folder Id: " + parentFolderId;
    }
}
