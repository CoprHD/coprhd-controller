/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_create")
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

    @SerializedName("operating-system")
    @JsonProperty(value = "operating-system")
    private String operatingSystem;

    @SerializedName("cluster-id")
    @JsonProperty(value = "cluster-id")
    private String clusterName;

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

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(String operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    @Override
    public String toString() {
        return "XtremIOInitiatorCreate [name=" + name + ", initiatorGroup=" + initiatorGroup + ", portAddress=" + portAddress
                + ", operating-system=" + operatingSystem + ", clusterName=" + clusterName + "]";
    }

}
