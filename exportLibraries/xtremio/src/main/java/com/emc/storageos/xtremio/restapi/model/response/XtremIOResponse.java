/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.xtremio.restapi.model.XtremIOResponseContent;
import com.google.gson.annotations.SerializedName;

public class XtremIOResponse {
    @SerializedName("links")
    @JsonProperty(value = "links")
    private XtremIOResponseContent[] response;

    public XtremIOResponseContent[] getVolumes() {
        return response != null ? response.clone() : response;
    }

    public void setVolumes(XtremIOResponseContent[] response) {
    	if(response != null){
    		this.response = response.clone();
    	}
    }
}
