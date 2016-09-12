/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

public class ReplicationSessionCreateParam extends ParamBase {
    private String srcResourceId;
    private String dstResourceId;
    private int maxTimeOutOfSync;
    private boolean autoInitiate;
    private RemoteSystem remoteSystem = null;

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

    public int getMaxTimeOutOfSync() {
        return maxTimeOutOfSync;
    }

    public void setMaxTimeOutOfSync(int maxTimeOutOfSync) {
        this.maxTimeOutOfSync = maxTimeOutOfSync;
    }

    public boolean getAutoInitiate() {
        return this.autoInitiate;
    }

    public void setAutoInitiate(boolean autoInitiate) {
        this.autoInitiate = autoInitiate;
    }

}
