/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeHost extends VNXeBase {
    private List<Integer> operationalStatus;
    private Health health;
    private String name;
    private String description;
    private Integer type;
    private String address;
    private String osType;
    private List<VNXeBase> hostIPPorts;
    private List<VNXeBase> iscsiHostInitiators;
    private List<VNXeBase> fcHostInitiators;
    private List<VNXeBase> hostLUNs;

    public List<Integer> getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(List<Integer> operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getOsType() {
        return osType;
    }

    public void setOsType(String osType) {
        this.osType = osType;
    }

    public List<VNXeBase> getHostIPPorts() {
        return hostIPPorts;
    }

    public void setHostIPorts(List<VNXeBase> hostIPPorts) {
        this.hostIPPorts = hostIPPorts;
    }

    public List<VNXeBase> getIscsiHostInitiators() {
        return iscsiHostInitiators;
    }

    public void setIscsiHostInitiators(List<VNXeBase> iscsiHostInitiators) {
        this.iscsiHostInitiators = iscsiHostInitiators;
    }

    public List<VNXeBase> getFcHostInitiators() {
        return fcHostInitiators;
    }

    public void setFcHostInitiators(List<VNXeBase> fcHostInitiators) {
        this.fcHostInitiators = fcHostInitiators;
    }

    public List<VNXeBase> getHostLUNs() {
        return hostLUNs;
    }

    public void setHostLUNs(List<VNXeBase> hostLUNs) {
        this.hostLUNs = hostLUNs;
    }
}
