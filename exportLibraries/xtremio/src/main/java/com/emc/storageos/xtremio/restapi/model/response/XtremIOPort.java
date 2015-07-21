/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

public class XtremIOPort {
    @SerializedName("name")
    @JsonProperty(value="name")
    private String name;

    @SerializedName("port-type")
    @JsonProperty(value="port-type")
    private String portType;
    
    @SerializedName("port-speed")
    @JsonProperty(value="port-speed")
    private String portSpeed;
    
    @SerializedName("port-address")
    @JsonProperty(value="port-address")
    private String portAddress;
    
    @SerializedName("port-state")
    @JsonProperty(value="port-state")
    private String operationalStatus;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPortType() {
        return portType;
    }

    public void setPortType(String portType) {
        this.portType = portType;
    }

    public String getPortSpeed() {
        return portSpeed;
    }

    public void setPortSpeed(String portSpeed) {
        this.portSpeed = portSpeed;
    }

    public String getPortAddress() {
        return portAddress;
    }

    public void setPortAddress(String portAddress) {
        this.portAddress = portAddress;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }
    
}
