/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

import java.util.List;

/**
 * This class describes remote replication pair.
 * The replication pair is defined as a pair of source and target volumes, linked by replication link.
 */
public class RemoteReplicationPair {

    /**
     * Device nativeId of replication pair. Type: Input/Output.
     */
    private String nativeId;

    /**
     * Native id of remote replication set for this pair. Type: Input.
     */
    private String replicationSetNativeId;

    /**
     * Native id of existing remote replication group of this pair (if applicable). Type: Input.
     */
    private String replicationGroupNativeId;

    /**
     * Replication mode of the pair.
     * Type: Input/Output
     */
    private String replicationMode;

    /**
     * Replication state of the pair. Type: Input/Output.
     * When Input --- state as known to the system.
     * When Output --- state on device.
     * State managed by driver.
     */
    private String replicationState;

    /**
     * Replication direction of the pair. Type: Input/Output.
     * When Input --- direction as known to the system.
     * When Output --- direction on device.
     * Direction managed by driver.
     */
    private String replicationDirection;

    /**
     * Source volume of replication pair. Type: Input.
     */
    private StorageVolume sourceVolume;

    /**
     * Target volume of the replication pair. Type: Input.
     */
    private StorageVolume targetVolume;

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

    public String getReplicationSetNativeId() {
        return replicationSetNativeId;
    }

    public void setReplicationSetNativeId(String replicationSetNativeId) {
        this.replicationSetNativeId = replicationSetNativeId;
    }

    public String getReplicationGroupNativeId() {
        return replicationGroupNativeId;
    }

    public void setReplicationGroupNativeId(String replicationGroupNativeId) {
        this.replicationGroupNativeId = replicationGroupNativeId;
    }

    public String getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(String replicationMode) {
        this.replicationMode = replicationMode;
    }

    public String getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(String replicationState) {
        this.replicationState = replicationState;
    }

    public StorageVolume getSourceVolume() {
        return sourceVolume;
    }

    public void setSourceVolume(StorageVolume sourceVolume) {
        this.sourceVolume = sourceVolume;
    }

    public StorageVolume getTargetVolume() {
        return targetVolume;
    }

    public void setTargetVolume(StorageVolume targetVolume) {
        this.targetVolume = targetVolume;
    }

    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }

    public String getReplicationDirection() {
        return replicationDirection;
    }

    public void setReplicationDirection(String replicationDirection) {
        this.replicationDirection = replicationDirection;
    }
}
