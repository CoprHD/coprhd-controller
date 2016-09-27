/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReplicationSession extends VNXeBase {
    private String name;
    private String srcResourceId;
    private String dstResourceId;
    private ReplicationEndpointResourceTypeEnum replicationResourceType;
    private int maxTimeOutOfSync;
    private VNXeBase remoteSystem;
    private ReplicationSessionReplicationRoleEnum localRole;
    private ReplicationSessionStatusEnum srcStatus;
    private ReplicationSessionStatusEnum dststatus;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSrcResourceId() {
        return srcResourceId;
    }

    public void setSrcResourceId(String srcResourceId) {
        this.srcResourceId = srcResourceId;
    }

    public String getDstResourceId() {
        return dstResourceId;
    }

    public void setDstResourceId(String dstResourceId) {
        this.dstResourceId = dstResourceId;
    }

    public ReplicationEndpointResourceTypeEnum getReplicationResourceType() {
        return replicationResourceType;
    }

    public void setReplicationResourceType(ReplicationEndpointResourceTypeEnum replicationResourceType) {
        this.replicationResourceType = replicationResourceType;
    }

    public ReplicationSessionReplicationRoleEnum getLocalRole() {
        return localRole;
    }

    public void setLocalRole(ReplicationSessionReplicationRoleEnum localRole) {
        this.localRole = localRole;
    }

    public VNXeBase getRemoteSystem() {
        return remoteSystem;
    }

    public void setRemoteSystem(VNXeBase remoteSystem) {
        this.remoteSystem = remoteSystem;
    }

    public int getMaxTimeOutOfSync() {
        return maxTimeOutOfSync;
    }

    public void setMaxTimeOutOfSync(int maxTimeOutOfSync) {
        this.maxTimeOutOfSync = maxTimeOutOfSync;
    }

    public ReplicationSessionStatusEnum getSrcStatus() {
        return srcStatus;
    }

    public void setSrcStatus(ReplicationSessionStatusEnum srcStatus) {
        this.srcStatus = srcStatus;
    }

    public ReplicationSessionStatusEnum getDststatus() {
        return dststatus;
    }

    public void setDststatus(ReplicationSessionStatusEnum dststatus) {
        this.dststatus = dststatus;
    }

    public static enum ReplicationEndpointResourceTypeEnum {
        INVALID0(0), FILESYSTEM(1), CONSISTENCYGROUP(2), VMWAREFS(3), VMWAREISCSI(4), INVALID1(5), INVALID2(6), INVALID3(7), LUN(
                8), INVALID4(9), NAS_SERVER(10);

        private int value;

        private ReplicationEndpointResourceTypeEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }
}
