/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeHostInitiator extends VNXeBase {
    private List<Integer> operationalStatus;
    private Health health;
    private String name;
    private HostInitiatorTypeEnum type;
    private String initiatorId;
    private boolean isIgnored;
    private String chapUserName;
    private boolean isChapSecretEnabled;
    private VNXeBase parentHost;
    private List<VNXeBase> paths;
    private String nodeWWN;
    private String portWWN;
    private String hostOsType;

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

    public HostInitiatorTypeEnum getType() {
        return type;
    }

    public void setType(HostInitiatorTypeEnum type) {
        this.type = type;
    }

    public String getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(String initiatorId) {
        this.initiatorId = initiatorId;
    }

    public boolean getIsIgnored() {
        return isIgnored;
    }

    public void setIsIgnored(boolean isIgnored) {
        this.isIgnored = isIgnored;
    }

    public String getChapUserName() {
        return chapUserName;
    }

    public void setChapUserName(String chapUserName) {
        this.chapUserName = chapUserName;
    }

    public boolean getIsChapSecretEnabled() {
        return isChapSecretEnabled;
    }

    public void setIsChapSecretEnabled(boolean isChapSecretEnabled) {
        this.isChapSecretEnabled = isChapSecretEnabled;
    }

    public VNXeBase getParentHost() {
        return parentHost;
    }

    public void setParentHost(VNXeBase parentHost) {
        this.parentHost = parentHost;
    }

    public List<VNXeBase> getPaths() {
        return paths;
    }

    public void setPaths(List<VNXeBase> paths) {
        this.paths = paths;
    }

    public String getNodeWWN() {
        return nodeWWN;
    }

    public void setNodeWWN(String nodeWWN) {
        this.nodeWWN = nodeWWN;
    }

    public String getPortWWN() {
        return portWWN;
    }

    public void setPortWWN(String portWWN) {
        this.portWWN = portWWN;
    }

    public String getHostOsType() {
        return hostOsType;
    }

    public void setHostOsType(String hostOsType) {
        this.hostOsType = hostOsType;
    }

    public static enum HostInitiatorTypeEnum {
        INITIATOR_TYPE_UNKNOWN(0),
        INITIATOR_TYPE_FC(1),
        INITIATOR_TYPE_ISCSI(2);

        private int value;

        private HostInitiatorTypeEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }
}
