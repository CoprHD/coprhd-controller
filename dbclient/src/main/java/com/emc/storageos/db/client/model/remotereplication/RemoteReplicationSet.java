/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import java.util.Set;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;

@Cf("RemoteReplicationSet")
public class RemoteReplicationSet extends DataObject {

    public enum ElementType {
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    public enum ReplicationState {
        ACTIVE,
        SYNCHRONIZING,
        PAUSED,
        FAILED_OVER,
        SWAPPED,
        STOPPED
    }

    // native id of replication set.
    private String nativeId;

    // Device label of this replication set
    private String deviceLabel;

    // If replication set is reachable.
    private Boolean reachable;

    // Type of storage systems in this replication set.
    private String storageSystemType;

    // Map of nativeId of storage system to its roles in the replication set.
    private StringSetMap systemToRolesMap;

    // Element types in this remote replication set for which which device supports replication link operations.
    private StringSet supportedReplicationLinkGranularity;



    // Maps supported replication modes for elements of this set to a boolean flag indicating if group consistency for
    // link operations is automatically enforced by device.
    private StringMap replicationModesToGroupConsistencyMap;

    // Set of replication modes for which group consistency cannot be enforced on device.
    private StringSet replicationModesNoGroupConsistency;

    // When replication link operations are supported on the SET level, defines link mode.
    private String replicationMode;

    // When replication link operations are supported on the SET level, defines state of the link for this set.
    private ReplicationState replicationState;

    // Element types supported by this replication set.
    private Set<ElementType> supportedElementTypes;


    @Name("nativeId")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
        setChanged("nativeId");
    }

    @Name("reachable")
    public Boolean getReachable() {
        return reachable == null ? false : reachable;
    }

    public void setReachable(final Boolean reachable) {
        this.reachable = reachable;
        setChanged("reachable");
    }

    @AlternateId("AltIdIndex")
    @Name("storageSystemType")
    public String getStorageSystemType() {
        return storageSystemType;
    }


    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
        setChanged("storageSystemType");
    }

    @Name("deviceLabel")
    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
        setChanged("deviceLabel");
    }

    @Name("systemToRolesMap")
    public StringSetMap getSystemToRolesMap() {
        return systemToRolesMap;
    }

    public void setSystemToRolesMap(StringSetMap systemToRolesMap) {
        this.systemToRolesMap = systemToRolesMap;
        setChanged("systemToRolesMap");
    }

    @Name("supportedReplicationLinkGranularity")
    public StringSet getSupportedReplicationLinkGranularity() {
        return supportedReplicationLinkGranularity;
    }

    public void setSupportedReplicationLinkGranularity(StringSet supportedReplicationLinkGranularity) {
        this.supportedReplicationLinkGranularity = supportedReplicationLinkGranularity;
        setChanged("supportedReplicationLinkGranularity");
    }

    @Name("replicationModesToGroupConsistencyMap")
    public StringMap getReplicationModesToGroupConsistencyMap() {
        return replicationModesToGroupConsistencyMap;
    }

    public void setReplicationModesToGroupConsistencyMap(StringMap replicationModesToGroupConsistencyMap) {
        this.replicationModesToGroupConsistencyMap = replicationModesToGroupConsistencyMap;
        setChanged("replicationModesToGroupConsistencyMap");
    }

    @Name("replicationModesNoGroupConsistency")
    public StringSet getReplicationModesNoGroupConsistency() {
        return replicationModesNoGroupConsistency;
    }

    public void setReplicationModesNoGroupConsistency(StringSet replicationModesNoGroupConsistency) {
        this.replicationModesNoGroupConsistency = replicationModesNoGroupConsistency;
        setChanged("replicationModesNoGroupConsistency");
    }

    @Name("replicationMode")
    public String getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(String replicationMode) {
        this.replicationMode = replicationMode;
        setChanged("replicationMode");
    }

    @Name("replicationState")
    public ReplicationState getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(ReplicationState replicationState) {
        this.replicationState = replicationState;
        setChanged("replicationState");
    }

    @Name("supportedElementTypes")
    public Set<ElementType> getSupportedElementTypes() {
        return supportedElementTypes;
    }

    public void setSupportedElementTypes(Set<ElementType> supportedElementTypes) {
        this.supportedElementTypes = supportedElementTypes;
        setChanged("supportedElementTypes");
    }
}
