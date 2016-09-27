/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class ReplicationSessionParam extends ParamBase {
    private String srcResourceId;
    private String dstResourceId;
    private Integer maxTimeOutOfSync = null;
    private Boolean autoInitiate = null;
    private RemoteSystem remoteSystem;
    private Boolean sync = null;
    private Boolean forceFullCopy = null;

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

    public RemoteSystem getRemoteSystem() {
        return remoteSystem;
    }

    public void setRemoteSystem(RemoteSystem remoteSystem) {
        this.remoteSystem = remoteSystem;
    }

    public Integer getMaxTimeOutOfSync() {
        return maxTimeOutOfSync;
    }

    public void setMaxTimeOutOfSync(Integer maxTimeOutOfSync) {
        this.maxTimeOutOfSync = maxTimeOutOfSync;
    }

    public Boolean getAutoInitiate() {
        return this.autoInitiate;
    }

    public void setAutoInitiate(Boolean autoInitiate) {
        this.autoInitiate = autoInitiate;
    }

    public Boolean getSync() {
        return sync;
    }

    public void setSync(Boolean sync) {
        this.sync = sync;
    }

    public Boolean getForceFullCopy() {
        return forceFullCopy;
    }

    public void setForceFullCopy(Boolean forceFullCopy) {
        this.forceFullCopy = forceFullCopy;
    }
}
