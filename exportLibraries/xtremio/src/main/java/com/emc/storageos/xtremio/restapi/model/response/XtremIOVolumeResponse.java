/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.xtremio.restapi.model.XtremIOResponseContent;
import com.google.gson.annotations.SerializedName;

public class XtremIOVolumeResponse {
    @SerializedName("links")
    @JsonProperty(value="links")
    private XtremIOResponseContent[] volumes;

    public XtremIOResponseContent[] getVolumes() {
        return volumes != null ? volumes.clone() : volumes;
    }

    public void setVolumes(XtremIOResponseContent[] volumes) {
    	if(volumes != null){
    		this.volumes = volumes.clone();
    	}
    }
}
