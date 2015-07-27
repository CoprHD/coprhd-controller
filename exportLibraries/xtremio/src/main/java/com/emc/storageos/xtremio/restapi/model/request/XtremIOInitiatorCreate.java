/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_create")
public class XtremIOInitiatorCreate {
    
    @SerializedName("initiator-name")
    @JsonProperty(value = "initiator-name")
    private String name;
    
    @SerializedName("ig-id")
    @JsonProperty(value = "ig-id")
    private String initiatorGroup;
    
    @SerializedName("port-address")
    @JsonProperty(value = "port-address")
    private String portAddress;

    public String getInitiatorGroup() {
        return initiatorGroup;
    }

    public void setInitiatorGroup(String initiatorGroup) {
        this.initiatorGroup = initiatorGroup;
    }

    public String getPortAddress() {
        return portAddress;
    }

    public void setPortAddress(String portAddress) {
        this.portAddress = portAddress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public String toString() {
        return "initiator-name: " + name + ". ig-id: " + initiatorGroup + ", port-address: " + portAddress;
    }

}
