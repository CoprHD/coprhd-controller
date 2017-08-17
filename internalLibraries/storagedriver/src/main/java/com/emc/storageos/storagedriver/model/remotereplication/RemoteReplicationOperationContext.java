/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

/**
 * Specifies context of the remote replication link operation
 */
public class RemoteReplicationOperationContext {

    /**
     * Element type of the operation context
     */
    private RemoteReplicationSet.ElementType elementType;

    /**
     * Remote replication set of the operation context. Type: Input .
     */
    private String remoteReplicationSetNativeId;

    /**
     * Remote replication group  of the operation  context. Type: Input.
     */
    private String remoteReplicationGroupNativeId;

    /**
     * State of remote replication Set. Type: Input/Output .
     * Controller will pass this state as currently know to the system.
     * Driver should update this state based on result of remote replication operation on device.
     */
    private String remoteReplicationSetState;

    /**
     * State of remote replication Group. Type: Input/Output .
     * Controller will pass this state as currently know to the system.
     * Driver should update this state based on result of remote replication operation on device.
     */
    private String remoteReplicationGroupState;

    public RemoteReplicationOperationContext(RemoteReplicationSet.ElementType elementType) {
        this.elementType = elementType;
    }

    public RemoteReplicationSet.ElementType getElementType() {
        return elementType;
    }

    public void setElementType(RemoteReplicationSet.ElementType elementType) {
        this.elementType = elementType;
    }

    public String getRemoteReplicationSetNativeId() {
        return remoteReplicationSetNativeId;
    }

    public void setRemoteReplicationSetNativeId(String remoteReplicationSetNativeId) {
        this.remoteReplicationSetNativeId = remoteReplicationSetNativeId;
    }

    public String getRemoteReplicationGroupNativeId() {
        return remoteReplicationGroupNativeId;
    }

    public void setRemoteReplicationGroupNativeId(String remoteReplicationGroupNativeId) {
        this.remoteReplicationGroupNativeId = remoteReplicationGroupNativeId;
    }

    public String getRemoteReplicationSetState() {
        return remoteReplicationSetState;
    }

    public void setRemoteReplicationSetState(String remoteReplicationSetState) {
        this.remoteReplicationSetState = remoteReplicationSetState;
    }

    public String getRemoteReplicationGroupState() {
        return remoteReplicationGroupState;
    }

    public void setRemoteReplicationGroupState(String remoteReplicationGroupState) {
        this.remoteReplicationGroupState = remoteReplicationGroupState;
    }
}
