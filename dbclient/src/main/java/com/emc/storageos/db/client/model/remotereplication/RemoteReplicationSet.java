/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;

import java.util.Set;

@Cf("RemoteReplicationSet")
public class RemoteReplicationSet extends DataObject {

    // native id of replication set
    private String nativeId;

    // If replication set is reachable
    private Boolean reachable;

    // index this field.
    private String storageSystemType;
    private String displayName;

    public enum ReplicationRole {
        SOURCE,
        TARGET
    }

    // Map of guid of storage system to its role in the replication set.
    private StringSetMap systemToRolesMap;

    public enum ReplicationLinkGranularity {
        SET,
        GROUP,
        PAIR
    }

    // Defines levels of remote replication objects for which device supports replication link operations.
    private StringSet supportedReplicationLinkGranularity;

    public enum ReplicationMode {
        SYNC,
        ASYNC,
        PERIODIC,
        ASYNC_WRITE_ORDER_CONSISTENT
    }

    /**
     * Defines replication modes supported for elements of this set.
     */
    private StringSet supportedReplicationModes;

    // When replication link operations are supported on the SET level, defines link mode.
    private String replicationMode;

    public enum ReplicationState {
        ACTIVE,
        SYNCHRONIZING,
        PAUSED,
        FAILED_OVER,
        SWAPPED,
        STOPPED
    }

    // When replication link operations are supported on the SET level, defines state of the link for this set.
    private ReplicationState replicationState;

    public enum ElementType {
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    /**
     * Element types supported by this replication set.
     */
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
    public Boolean getReachableStatus() {
        return reachable == null ? false : reachable;
    }

    public void setReachableStatus(final Boolean reachable) {
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

    @Name("displayName")
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        setChanged("displayName");
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

    @Name("supportedReplicationModes")
    public StringSet getSupportedReplicationModes() {
        return supportedReplicationModes;
    }

    public void setSupportedReplicationModes(StringSet supportedReplicationModes) {
        this.supportedReplicationModes = supportedReplicationModes;
        setChanged("supportedReplicationModes");
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

    @Name("replicationMode")
    public String getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(String replicationMode) {
        this.replicationMode = replicationMode;
        setChanged("replicationMode");
    }
}
