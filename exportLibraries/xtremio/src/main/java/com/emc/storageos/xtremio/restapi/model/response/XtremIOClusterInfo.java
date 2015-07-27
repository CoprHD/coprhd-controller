/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;


@JsonRootName(value="xtremio_cluster_info")
public class XtremIOClusterInfo {
    
    @SerializedName("content")
    @JsonProperty(value="content")
	private XtremIOSystem content;

    public XtremIOSystem getContent() {
        return content;
    }

    public void setContent(XtremIOSystem content) {
        this.content = content;
    }
}
