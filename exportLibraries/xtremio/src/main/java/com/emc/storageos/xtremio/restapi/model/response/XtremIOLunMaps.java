/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_lun_maps")
public class XtremIOLunMaps {
    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOLunMap content;

    public XtremIOLunMap getContent() {
        return content;
    }

    public void setContent(XtremIOLunMap content) {
        this.content = content;
    }
}
