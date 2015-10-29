/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class XtremIOTagsInfo {

    @SerializedName("tags")
    @JsonProperty(value = "tags")
    private XtremIOObjectInfo[] tagsInfo;

    public XtremIOObjectInfo[] getTagsInfo() {
    	XtremIOObjectInfo[] result = null;
    	if(tagsInfo != null){
    		result = tagsInfo.clone();
    	}
        return result;
    }

    public void setTagsInfo(XtremIOObjectInfo[] tagsInfo) {
    	XtremIOObjectInfo[] input = null;
    	if(tagsInfo != null){
    		input = tagsInfo.clone();
    	}
        this.tagsInfo = input;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
