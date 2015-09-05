/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_snapshot_set_response")
public class XtremIOSnapshotSetResponse {

    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOSnapshotSet content;

    public XtremIOSnapshotSet getContent() {
        return content;
    }

    public void setContent(XtremIOSnapshotSet content) {
        this.content = content;
    }
}
