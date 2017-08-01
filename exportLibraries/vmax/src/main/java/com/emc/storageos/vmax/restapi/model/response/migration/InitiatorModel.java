/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.migration;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class InitiatorModel {

    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;

    @SerializedName("wwn")
    @JsonProperty(value = "wwn")
    private String wwn;

    @SerializedName("invalid")
    @JsonProperty(value = "invalid")
    private boolean invalid;

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

}
