/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class describes set of interconnected storage systems configured for remote replication.
 * The instances of this class are discovered by driver and are read-only from driver management perspective.
 * Any system with SOURCE role can be used to keep source elements, and any system with TARGET role can be used to
 * keep target elements. This should support different remote replication topologies.
 */
public class RemoteReplicationSet {

    // Device native id of the replication set. Identifies replication set for driver. Type: Output.
    private String nativeId;

    // Display name of the replication set. Type: Output.
    private String displayName;

    public enum ReplicationRole {
        SOURCE,
        TARGET
    }

    /**
     * Map of storage systems in the replication set mapped to their roles in the set.
     * The same system can have one or both replication roles.
     */
    private Map<String, Set<ReplicationRole>> systemMap = new HashMap<>();

    /**
     * Defines types of replication set elements for which link
     * management operations are supported.
     */
    public enum ReplicationLinkGranularity {
        SET,
        GROUP,
        PAIR
    }

    /**
     * Defines types of set elements for which replication link operations are supported.
     */
    private Set<ReplicationLinkGranularity> supportedReplicationLinkGranularity = new HashSet<>();

    /**
     * Defines replication modes supported for elements of this set.
     */
    private Set<CapabilityInstance> supportedReplicationModes;

    // When replication link operations are supported on the SET level, defines link mode.
    private CapabilityInstance replicationMode;

    /**
     * State of replication link.
     */
    public enum ReplicationState {
        ACTIVE,
        SYNCHRONIZING,
        PAUSED,
        FAILED_OVER,
        SWAPPED,
        STOPPED
    }

    /**
     * When replication link granularity is SET, defines replication link state of this set.
     */
    private ReplicationState replicationState;

    /**
     * Replication element type
     */
    public enum ElementType {
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    /**
     * Element types supported by this replication set.
     */
    private Set<ElementType> supportedElementTypes;

    /*
     * Set of replication groups in this replication set.
     * Should be populated only if elementTypes contains REPLICATION_GROUP.
     * If elementTypes has only REPLICATIOM_PAIR, driver should leave this element empty.
     */
    private Set<RemoteReplicationGroup> replicationGroups;

    /**
     * Device specific capabilities of this replication set.
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

    public Map<String, Set<ReplicationRole>> getSystemMap() {
        return systemMap;
    }

    public void setSystemMap(Map<String, Set<ReplicationRole>> systemMap) {
        this.systemMap = systemMap;
    }

    public Set<ReplicationLinkGranularity> getSupportedReplicationLinkGranularity() {
        return supportedReplicationLinkGranularity;
    }

    public void setSupportedReplicationLinkGranularity(Set<ReplicationLinkGranularity> supportedReplicationLinkGranularity) {
        this.supportedReplicationLinkGranularity = supportedReplicationLinkGranularity;
    }

    public Set<CapabilityInstance> getSupportedReplicationModes() {
        return supportedReplicationModes;
    }

    public void setSupportedReplicationModes(Set<CapabilityInstance> supportedReplicationModes) {
        this.supportedReplicationModes = supportedReplicationModes;
    }

    public CapabilityInstance getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(CapabilityInstance replicationMode) {
        this.replicationMode = replicationMode;
    }

    public ReplicationState getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(ReplicationState replicationState) {
        this.replicationState = replicationState;
    }

    public Set<ElementType> getSupportedElementTypes() {
        return supportedElementTypes;
    }

    public void setSupportedElementTypes(Set<ElementType> supportedElementTypes) {
        this.supportedElementTypes = supportedElementTypes;
    }

    public Set<RemoteReplicationGroup> getReplicationGroups() {
        return replicationGroups;
    }

    public void setReplicationGroups(Set<RemoteReplicationGroup> replicationGroups) {
        this.replicationGroups = replicationGroups;
    }

    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }
}
