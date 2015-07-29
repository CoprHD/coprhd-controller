/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_initiators")
public class XtremIOInitiators {
    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOInitiator content;

    public XtremIOInitiator getContent() {
        return content;
    }

    public void setContent(XtremIOInitiator content) {
        this.content = content;
    }

}
