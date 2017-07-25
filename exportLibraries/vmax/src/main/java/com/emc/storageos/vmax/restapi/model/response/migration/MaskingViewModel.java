/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.migration;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "sourceMaskingView")
public class MaskingViewModel {
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

    @SerializedName("initiatorGroup")
    @JsonProperty(value = "initiatorGroup")
    private InitiatorGroupModel initiatorGroup;

    @SerializedName("portGroup")
    @JsonProperty(value = "portGroup")
    private PortGroupModel portGroup;

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

    public InitiatorGroupModel getInitiatorGroup() {
        return initiatorGroup;
    }

    public void setInitiatorGroup(InitiatorGroupModel initiatorGroup) {
        this.initiatorGroup = initiatorGroup;
    }

    public PortGroupModel getPortGroup() {
        return portGroup;
    }

    public void setPortGroup(PortGroupModel portGroup) {
        this.portGroup = portGroup;
    }
}
