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
 * The instances of this class are discovered by driver.
 * Any system with SOURCE role can be used to keep source elements, and any system with TARGET role can be used to
 * keep target elements. This should support different remote replication topologies.
 *
 * Annotation "Output" means that only driver can set this attribute.
 */
public class RemoteReplicationSet {

    /**
     * Defines replication roles.
     */
    public enum ReplicationRole {
        SOURCE,
        TARGET
    }

    /**
     * Type of remote replication element.
     */
    public enum ElementType {
        REPLICATION_SET,
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    /**
     * State of remote replication link.
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
     * Device native id of the replication set. Identifies replication set for driver. Type: Output.
     */
    private String nativeId;

    /**
     * Display name of the replication set. Type: Output.
     */
    private String deviceLabel;

    /**
     * Map of storage systems in the replication set. Key: nativeId of storage system. Value: set of system roles in the set.
     * The same system can have one or both replication roles.
     * Type: Output.
     */
    private Map<String, Set<ReplicationRole>> systemMap = new HashMap<>();

    /**
     * Element types supported by this replication set. Type: Output.
     */
    private Set<ElementType> supportedElementTypes;

    /**
     * Defines types of set elements for which replication link operations are supported. Type: Output.
     */
    private Set<ElementType> replicationLinkGranularity = new HashSet<>();

    /**
     * Defines replication modes supported for elements of this set. Type: Output.
     */
    private Set<CapabilityInstance> supportedReplicationModes;

    /**
     * When replication link operations are supported on the SET level, defines link mode. Type: Output.
     */
    private CapabilityInstance replicationMode;


    /**
     * When replication link granularity is SET, defines replication link state of this set. Type: Output.
     */
    private ReplicationState replicationState;

    /**
     * Set of replication groups in this replication set. Type: Output.
     */
    private Set<RemoteReplicationGroup> replicationGroups;

    /**
     * Device specific capabilities of this replication set. Type: Input.
     */
    private List<CapabilityInstance> capabilities;


    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    public Map<String, Set<ReplicationRole>> getSystemMap() {
        return systemMap;
    }

    public void setSystemMap(Map<String, Set<ReplicationRole>> systemMap) {
        this.systemMap = systemMap;
    }

    public Set<ElementType> getReplicationLinkGranularity() {
        return replicationLinkGranularity;
    }

    public void setReplicationLinkGranularity(Set<ElementType> replicationLinkGranularity) {
        this.replicationLinkGranularity = replicationLinkGranularity;
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
