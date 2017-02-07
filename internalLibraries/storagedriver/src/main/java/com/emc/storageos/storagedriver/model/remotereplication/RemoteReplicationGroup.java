/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

/**
 * Describes remote replication group on device.
 * Group always has one source storage system and one target storage system.
 * Instances of remote replication groups could be configured on array, and have to be discovered.
 * New remote replication groups can be provisioned as well.
 */
public class RemoteReplicationGroup {

    /**
     * Device nativeId of replication group. Type: Input/Output.
     */
    private String nativeId;

    /**
     * When group was provisioned by the system, contains name given by user. Type: Input.
     */
    private String displayName;

    /**
     * Label of this group on device. Type: Output.
     */
    private String deviceLabel;

    /**
     * Replication mode of the group.
     * Type: Input/Output.
     */
    private String replicationMode;

    /**
     * Replication state of the group. Type: Output.
     */
    private RemoteReplicationSet.ReplicationState replicationState;

    /**
     * Defines if group consistency for link operations is enforced. Type: Input/Output.
     * When true, link operations are allowed only on group level and operations affect all
     * replication pairs in the group.
     * When false, link operations are allowed on individual pairs in the group.
     */
    private boolean isGroupConsistencyEnforced = false;

    /**
     * Native id of the source storage system in this replication group. Type: Input/Output.
     */
    private String sourceSystemNativeId;

    /**
     * Native id of the target storage system in this replication group. Type: Input/Output.
     */
    private String targetSystemNativeId;

    /**
     * Device specific capabilities. Type: Input.
     */
    private List<CapabilityInstance> capabilities;

    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    public String getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(String replicationMode) {
        this.replicationMode = replicationMode;
    }

    public RemoteReplicationSet.ReplicationState getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(RemoteReplicationSet.ReplicationState replicationState) {
        this.replicationState = replicationState;
    }

    public boolean isGroupConsistencyEnforced() {
        return isGroupConsistencyEnforced;
    }

    public void setIsGroupConsistencyEnforced(boolean isGroupConsistencyEnforced) {
        this.isGroupConsistencyEnforced = isGroupConsistencyEnforced;
    }

    public String getSourceSystemNativeId() {
        return sourceSystemNativeId;
    }

    public void setSourceSystemNativeId(String sourceSystemNativeId) {
        this.sourceSystemNativeId = sourceSystemNativeId;
    }

    public String getTargetSystemNativeId() {
        return targetSystemNativeId;
    }

    public void setTargetSystemNativeId(String targetSystemNativeId) {
        this.targetSystemNativeId = targetSystemNativeId;
    }

    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public String toString() {
        String msg = String.format("\n\tGroup native id %s: " +
                        "\n\t\t replication mode: %s, replication state: %s" +
                        "\n\t\t is group consistency enforced: %s" +
                        "\n\t\t source system %s" +
                        "\n\t\t target system %s",
                nativeId,  replicationMode, replicationState, isGroupConsistencyEnforced,
                sourceSystemNativeId, targetSystemNativeId);

        return(msg);
    }
}
