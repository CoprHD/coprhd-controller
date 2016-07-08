/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_lun_maps_info")
public class XtremIOLunMapsInfo {

    @SerializedName("lun_maps")
    @JsonProperty(value = "lun_maps")
    private XtremIOObjectInfo[] lunMapInfo;

    public XtremIOObjectInfo[] getLunMapInfo() {
        return lunMapInfo != null ? lunMapInfo.clone() : lunMapInfo;
    }

    public void setLunMapInfo(XtremIOObjectInfo[] lunMapInfo) {
        if (lunMapInfo != null) {
            this.lunMapInfo = lunMapInfo.clone();
        }
    }
}
