/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class XtremIOLunMapsInfo {

    @SerializedName("lun-maps")
    @JsonProperty(value = "lun-maps")
    private XtremIOObjectInfo[] lunMapsInfo;

    public XtremIOObjectInfo[] getLunMapsInfo() {
        XtremIOObjectInfo[] result = null;
        if (lunMapsInfo != null) {
            result = lunMapsInfo.clone();
        }
        return result;
    }

    public void setTagsInfo(XtremIOObjectInfo[] lunMapsInfo) {
        XtremIOObjectInfo[] input = null;
        if (lunMapsInfo != null) {
            input = lunMapsInfo.clone();
        }
        this.lunMapsInfo = input;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
