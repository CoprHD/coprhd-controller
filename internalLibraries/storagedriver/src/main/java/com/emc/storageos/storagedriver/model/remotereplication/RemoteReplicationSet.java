/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

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
        REPLICATION_PAIR,
        CONSISTENCY_GROUP
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
     * Defines types of replication set elements for which replication link operations are supported. Type: Output.
     */
    private Set<ElementType> replicationLinkGranularity = new HashSet<>();

    /**
     * Defines replication modes supported for elements of this set (groups/pairs).
     * Type: Output.
     */
    private Set<RemoteReplicationMode> supportedReplicationModes;

    /**
     * When replication link operations are supported on the SET level, defines link mode.
     * Type: Output.
     */
    // not applicable to sets: remove
    //private RemoteReplicationMode replicationMode;


    /**
     * Replication state of the set. Type: Input/Output.
     * When Input --- state as known to the system.
     * When Output --- state on device.
     * Should be set by driver when applicable.
     */
    private String replicationState;

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

    public Set<RemoteReplicationMode> getSupportedReplicationModes() {
        return supportedReplicationModes;
    }

    public void setSupportedReplicationModes(Set<RemoteReplicationMode> supportedReplicationModes) {
        this.supportedReplicationModes = supportedReplicationModes;
    }

    //public RemoteReplicationMode getReplicationMode() {
    //    return replicationMode;
    //}

//    public void setReplicationMode(RemoteReplicationMode replicationMode) {
//        this.replicationMode = replicationMode;
//    }

    public String getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(String replicationState) {
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

    @Override
    public String toString() {

       String msg = String.format("\n\tSet nativeId %s: " +
               "\n\t\t supported replication modes: %s, " +
               "\n\t\t replication state: %s" +
               "\n\t\t supported link granularity: %s" +
               "\n\t\t system map: %s",
               nativeId,  supportedReplicationModes, replicationState, replicationLinkGranularity,
               systemMap);

        return(msg);
    }
}
