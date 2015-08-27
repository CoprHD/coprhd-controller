/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

public class XtremIOXMS {

    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;

    @SerializedName("version")
    @JsonProperty(value = "version")
    private String version;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "XtremIOXMS [name=" + name + ", version=" + version + "]";
    }
}
