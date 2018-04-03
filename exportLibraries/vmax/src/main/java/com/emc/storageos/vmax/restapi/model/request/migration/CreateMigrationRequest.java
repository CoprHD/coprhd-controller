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
    private Boolean noCompression;

    @SerializedName("srpId")
    @JsonProperty(value = "srpId")
    private String srpId;

    @SerializedName("preCopy")
    @JsonProperty(value = "preCopy")
    private Boolean preCopy;

    @SerializedName("executionOption")
    @JsonProperty(value = "executionOption")
    private String executionOption;

    public String getOtherArrayId() {
        return otherArrayId;
    }

    public void setOtherArrayId(String otherArrayId) {
        this.otherArrayId = otherArrayId;
    }

    public Boolean isNoCompression() {
        return noCompression;
    }

    public void setNoCompression(Boolean noCompression) {
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

    public Boolean getPreCopy() { return preCopy; }

    public void setPreCopy(Boolean preCopy) { this.preCopy = preCopy; }

    public String getExecutionOption() {
        return executionOption;
    }

    public void setExecutionOption(String executionOption) {
        this.executionOption = executionOption;
    }

}
