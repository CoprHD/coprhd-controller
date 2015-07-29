/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.emc.storageos.xtremio.restapi.model.XtremIOResponseContent;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_folder_create")
public class XtremIOFolderCreate {

    @SerializedName("folders")
    @JsonProperty(value = "folders")
    private XtremIOResponseContent[] volumeFolders;

    public XtremIOResponseContent[] getVolumeFolders() {
        return volumeFolders != null ? volumeFolders.clone() : volumeFolders;
    }

    public void setVolumeFolders(XtremIOResponseContent[] volumeFolders) {
        if (volumeFolders != null) {
            this.volumeFolders = volumeFolders.clone();
        }
    }

}
