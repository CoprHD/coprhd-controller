/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteSystem extends VNXeBase {
    private String name;
    private String model;
    private String serialNumber;
    private String managementAddress;
    private ReplicationCapabilityEnum connectionType;
    private String username;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getManagementAddress() {
        return managementAddress;
    }

    public void setManagementAddress(String managementAddress) {
        this.managementAddress = managementAddress;
    }

    public ReplicationCapabilityEnum getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(ReplicationCapabilityEnum connectionType) {
        this.connectionType = connectionType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
