/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class XtremIOXMSsInfo {

    @SerializedName("xmss")
    @JsonProperty(value = "xmss")
    private XtremIOObjectInfo[] xmssInfo;

    public XtremIOObjectInfo[] getXmssInfo() {
    	XtremIOObjectInfo[] result = null;
    	if(xmssInfo != null){
    		result = xmssInfo.clone();
    	}
        return result;
    }

    public void setXmssInfo(XtremIOObjectInfo[] xmssInfo) {
    	XtremIOObjectInfo[] input = null;
    	if(xmssInfo != null){
    		input = xmssInfo.clone();
    	}
        this.xmssInfo = input;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
