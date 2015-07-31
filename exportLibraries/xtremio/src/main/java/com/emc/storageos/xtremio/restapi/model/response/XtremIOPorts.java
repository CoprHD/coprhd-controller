/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_ports")
public class XtremIOPorts {
    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOPort content;

    public XtremIOPort getContent() {
        return content;
    }

    public void setContent(XtremIOPort content) {
        this.content = content;
    }
}
