/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.xtremio.restapi.model.XtremIOResponseContent;
import com.google.gson.annotations.SerializedName;

public class XtremIOResponse {
    @SerializedName("links")
    @JsonProperty(value="links")
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
