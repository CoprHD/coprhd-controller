/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.request.migration;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class CreateMigrationRequest {

    @SerializedName("otherArrayId")
    @JsonProperty(value = "otherArrayId")
    private String otherArrayId;

    @SerializedName("noCompression")
    @JsonProperty(value = "noCompression")
    private boolean noCompression;

    @SerializedName("srpId")
    @JsonProperty(value = "srpId")
    private String srpId;

    public String getOtherArrayId() {
        return otherArrayId;
    }

    public void setOtherArrayId(String otherArrayId) {
        this.otherArrayId = otherArrayId;
    }

    public boolean isNoCompression() {
        return noCompression;
    }

    public void setNoCompression(boolean noCompression) {
        this.noCompression = noCompression;
    }

    public String getSrpId() {
        return srpId;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
