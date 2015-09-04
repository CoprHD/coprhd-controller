/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_tags")
public class XtremIOTags {
    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOTag content;

    public XtremIOTag getContent() {
        return content;
    }

    public void setContent(XtremIOTag content) {
        this.content = content;
    }
}
