/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_folders")
public class XtremIOFolders {
    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOFolder content;

    public XtremIOFolder getContent() {
        return content;
    }

    public void setContent(XtremIOFolder content) {
        this.content = content;
    }
}
