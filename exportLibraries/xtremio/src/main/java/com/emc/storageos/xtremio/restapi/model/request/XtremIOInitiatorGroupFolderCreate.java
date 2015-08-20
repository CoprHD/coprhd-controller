/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_ig_folder_create")
public class XtremIOInitiatorGroupFolderCreate {
    @SerializedName("caption")
    @JsonProperty(value = "caption")
    private String caption;

    @SerializedName("parent-folder-id")
    @JsonProperty(value = "parent-folder-id")
    private String parentFolderId;

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(String parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    public String toString() {
        return "Name: ".concat(caption).concat("Parent Folder Id:").concat(parentFolderId);
    }

}
