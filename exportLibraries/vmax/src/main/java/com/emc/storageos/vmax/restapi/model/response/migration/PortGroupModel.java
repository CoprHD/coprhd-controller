/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.migration;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class PortGroupModel {
    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;

    @SerializedName("invalid")
    @JsonProperty(value = "invalid")
    private boolean invalid;

    @SerializedName("childInvalid")
    @JsonProperty(value = "childInvalid")
    private boolean childInvalid;

    @SerializedName("missing")
    @JsonProperty(value = "missing")
    private boolean missing;

    @SerializedName("ports")
    @JsonProperty(value = "ports")
    private List<PortModel> portList;

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

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    public boolean isChildInvalid() {
        return childInvalid;
    }

    public void setChildInvalid(boolean childInvalid) {
        this.childInvalid = childInvalid;
    }

    public boolean isMissing() {
        return missing;
    }

    public void setMissing(boolean missing) {
        this.missing = missing;
    }

    public List<PortModel> getPortList() {
        return portList;
    }

    public void setPortList(List<PortModel> portList) {
        this.portList = portList;
    }

}
