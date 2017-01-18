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
     * Remote replication set of the operation context
     */
    private String remoteReplicationSetNativeId;

    /**
     * Remote replication group  of the operation  context
     */
    private String remoteReplicationGroupNativeId;

    public RemoteReplicationOperationContext(RemoteReplicationSet.ElementType elementType, String remoteReplicationSetNativeId,
                                             String remoteReplicationGroupNativeId) {
        this.elementType = elementType;
        this.remoteReplicationSetNativeId = remoteReplicationSetNativeId;
        this.remoteReplicationGroupNativeId = remoteReplicationGroupNativeId;
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
}
