/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_volumes_info")
public class XtremIOVolumesInfo {

    @SerializedName("volumes")
    @JsonProperty(value = "volumes")
    private XtremIOObjectInfo[] volumeInfo;

    public XtremIOObjectInfo[] getVolumeInfo() {
        return volumeInfo != null ? volumeInfo.clone() : volumeInfo;
    }

    public void setVolumeInfo(XtremIOObjectInfo[] volumeInfo) {
        if (volumeInfo != null) {
            this.volumeInfo = volumeInfo.clone();
        }
    }
}
