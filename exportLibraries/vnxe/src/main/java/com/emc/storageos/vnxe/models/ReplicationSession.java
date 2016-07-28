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
    private String dstResourceId;;
    private int maxTimeOutOfSync;
    private VNXeBase remoteSystem;
    private ReplicationSessionReplicationRoleEnum localRole;

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

}
