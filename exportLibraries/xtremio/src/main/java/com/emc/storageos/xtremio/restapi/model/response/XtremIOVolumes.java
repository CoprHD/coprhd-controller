/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_volumes")
public class XtremIOVolumes {
    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOVolume content;

    public XtremIOVolume getContent() {
        return content;
    }

    public void setContent(XtremIOVolume content) {
        this.content = content;
    }
}
