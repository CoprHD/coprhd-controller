/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class VNXeFileInterface extends VNXeBase{
    private VNXeBase nasServer;
    private VNXeBase ethernetPort;
    private Health health;
    private boolean isSystem;
    private String ipAddress;
    private int ipProtocolVersion;
    private String netmask;
    private List<Integer> operationalStatus;
    private int v6PrefixLength;
    private String gateway;
    private int vlanId;
    private String macAddress;
    private String name;
    private InterfaceConfigurationEnum configuration;
    
    public VNXeBase getNasServer() {
        return nasServer;
    }

    public void setNasServer(VNXeBase nasServer) {
        this.nasServer = nasServer;
    }

    public VNXeBase getEthernetPort() {
        return ethernetPort;
    }

    public void setEthernetPort(VNXeBase eithernetPort) {
        this.ethernetPort = eithernetPort;
    }

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public boolean getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(boolean isSystem) {
        this.isSystem = isSystem;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getIpProtocolVersion() {
        return ipProtocolVersion;
    }

    public void setIpProtocolVersion(int ipProtocolVersion) {
        this.ipProtocolVersion = ipProtocolVersion;
    }

    public String getNetmask() {
        return netmask;
    }

    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }

    public List<Integer> getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(List<Integer> operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public int getV6PrefixLength() {
        return v6PrefixLength;
    }

    public void setV6PrefixLength(int v6PrefixLength) {
        this.v6PrefixLength = v6PrefixLength;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public int getVlanId() {
        return vlanId;
    }

    public void setVlanId(int vlanId) {
        this.vlanId = vlanId;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InterfaceConfigurationEnum getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InterfaceConfigurationEnum configuration) {
        this.configuration = configuration;
    }

    public static enum InterfaceConfigurationEnum {
        GLOBAL,
        OVERRIDE,
        LOCAL;
    }
}
