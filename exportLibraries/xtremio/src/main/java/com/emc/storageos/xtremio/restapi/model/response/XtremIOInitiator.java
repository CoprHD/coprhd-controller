/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_initiator")
public class XtremIOInitiator {
    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;

    @SerializedName("port-type")
    @JsonProperty(value = "port-type")
    private String portType;

    @SerializedName("port-address")
    @JsonProperty(value = "port-address")
    private String portAddress;

    @SerializedName("ig-id")
    @JsonProperty(value = "ig-id")
    private List<String> initiatorGroup;

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

    public String getPortAddress() {
        return portAddress;
    }

    public void setPortAddress(String portAddress) {
        this.portAddress = portAddress;
    }

    public List<String> getInitiatorGroup() {
        return initiatorGroup;
    }

    public void setInitiatorGroup(List<String> initiatorGroup) {
        this.initiatorGroup = initiatorGroup;
    }

	@Override
	public String toString() {
		return "XtremIOInitiator [name=" + name + ", portType=" + portType + ", portAddress=" + portAddress
				+ ", initiatorGroup=" + initiatorGroup + "]";
	}

}
