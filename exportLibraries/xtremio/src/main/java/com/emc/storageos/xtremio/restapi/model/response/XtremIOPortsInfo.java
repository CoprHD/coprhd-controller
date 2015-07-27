/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_ports_info")
public class XtremIOPortsInfo {
    
    @SerializedName("targets")
    @JsonProperty(value="targets")
    private XtremIOPortInfo[] portInfo;

    public XtremIOPortInfo[] getPortInfo() {
        return portInfo != null ? portInfo.clone() : portInfo;
    }

    public void setPortInfo(XtremIOPortInfo[] portInfo) {
    	if(portInfo != null){
    		this.portInfo = portInfo.clone();
    	}
    }
}
