/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.request.migration;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "environmentSetup")
public class CreateMigrationEnvironmentRequest {

    @SerializedName("otherArrayId")
    @JsonProperty(value = "otherArrayId")
    private String otherArrayId;

    public String getOtherArrayId() {
        return otherArrayId;
    }

    public void setOtherArrayId(String otherArrayId) {
        this.otherArrayId = otherArrayId;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
