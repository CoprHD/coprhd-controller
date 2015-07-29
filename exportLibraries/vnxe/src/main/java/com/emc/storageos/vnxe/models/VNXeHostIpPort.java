/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeHostIpPort extends VNXeBase {
    private String address;
    private String name;
    private HostPortTypeEnum type;
    private boolean isIgnored;
    private String subnetMask;
    private VNXeBase host;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HostPortTypeEnum getType() {
        return type;
    }

    public void setType(HostPortTypeEnum type) {
        this.type = type;
    }

    public boolean getIsIgnored() {
        return isIgnored;
    }

    public void setIsIgnored(boolean isIgnored) {
        this.isIgnored = isIgnored;
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }

    public VNXeBase getHost() {
        return host;
    }

    public void setHost(VNXeBase host) {
        this.host = host;
    }

    public enum HostPortTypeEnum {
        IPV4,
        IPV6,
        NETWORKNAME;
    }
}
