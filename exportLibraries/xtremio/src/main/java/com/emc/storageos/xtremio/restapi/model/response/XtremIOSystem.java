/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_system_info")
public class XtremIOSystem {

    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;

    @SerializedName("sys-sw-version")
    @JsonProperty(value = "sys-sw-version")
    private String version;

    @SerializedName("sys-psnt-serial-number")
    @JsonProperty(value = "sys-psnt-serial-number")
    private String serialNumber;

    @SerializedName("ud-ssd-space")
    @JsonProperty(value = "ud-ssd-space")
    private Long totalCapacity; // KB

    @SerializedName("ud-ssd-space-in-use")
    @JsonProperty(value = "ud-ssd-space-in-use")
    private Long usedCapacity; // KB

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

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Long getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(Long capacity) {
        this.totalCapacity = capacity;
    }

    public Long getUsedCapacity() {
        return usedCapacity;
    }

    public void setUsedCapacity(Long usedCapacity) {
        this.usedCapacity = usedCapacity;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
