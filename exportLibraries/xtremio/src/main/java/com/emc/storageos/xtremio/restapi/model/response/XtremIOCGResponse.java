/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_consistency_group_response")
public class XtremIOCGResponse {

    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOConsistencyGroup content;

    public XtremIOConsistencyGroup getContent() {
        return content;
    }

    public void setContent(XtremIOConsistencyGroup content) {
        this.content = content;
    }

}
