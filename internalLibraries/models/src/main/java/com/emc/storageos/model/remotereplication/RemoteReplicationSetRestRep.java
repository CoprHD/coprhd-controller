/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.remotereplication;

import com.emc.storageos.model.DataObjectRestRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashSet;
import java.util.Set;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "remote_replication_set")
public class RemoteReplicationSetRestRep extends DataObjectRestRep {

    // native id of replication set.
    private String nativeId;

    // Device label of this replication set
    private String deviceLabel;

    // If replication set is reachable.
    private Boolean reachable;

    // Type of storage systems in this replication set.
    private String storageSystemType;

    // Replication state
    String replicationState;

    // Supported element types in this set: group/pair
    private Set<String> supportedElementTypes;

    // Supported remote replication modes
    private Set<String> supportedReplicationModes;

    // Supported replication link granularities
    private Set<String> supportedReplicationLinkGranularity;

    // Source storage systems
    private Set<String> sourceSystems;

    // Target storage systems
    private Set<String> targetSystems;

    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    @XmlElement(name = "name")
    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    @XmlElement(name = "reachable")
    public Boolean getReachable() {
        return reachable;
    }

    public void setReachable(Boolean reachable) {
        this.reachable = reachable;
    }


    @XmlElement(name = "storage_system_type")
    public String getStorageSystemType() {
        return storageSystemType;
    }

    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
    }

    @XmlElementWrapper(name = "supported_element_types")
    @XmlElement(name = "supported_element_type")
    public Set<String> getSupportedElementTypes() {
        if (supportedElementTypes == null) {
            supportedElementTypes = new HashSet<>();
        }
        return supportedElementTypes;
    }

    public void setSupportedElementTypes(Set<String> supportedElementTypes) {
        this.supportedElementTypes = supportedElementTypes;
    }

    @XmlElementWrapper(name = "supported_replication_modes")
    @XmlElement(name = "supported_replication_mode")
    public Set<String> getSupportedReplicationModes() {
        if (supportedReplicationModes == null) {
            supportedReplicationModes = new HashSet<>();
        }
        return supportedReplicationModes;
    }

    public void setSupportedReplicationModes(Set<String> supportedReplicationModes) {
        this.supportedReplicationModes = supportedReplicationModes;
    }

    @XmlElementWrapper(name = "supported_replication_link_granularities")
    @XmlElement(name = "supported_replication_link_granularity")
    public Set<String> getSupportedReplicationLinkGranularity() {
        return supportedReplicationLinkGranularity;
    }

    public void setSupportedReplicationLinkGranularity(Set<String> supportedReplicationLinkGranularity) {
        this.supportedReplicationLinkGranularity = supportedReplicationLinkGranularity;
    }

    @XmlElement(name = "replication_state")
    public String getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(String replicationState) {
        this.replicationState = replicationState;
    }

    @XmlElementWrapper(name = "source_systems")
    @XmlElement(name = "source_system")
    public Set<String> getSourceSystems() {
        return sourceSystems;
    }

    public void setSourceSystems(Set<String> sourceSystems) {
        this.sourceSystems = sourceSystems;
    }

    @XmlElementWrapper(name = "target_systems")
    @XmlElement(name = "target_system")
    public Set<String> getTargetSystems() {
        return targetSystems;
    }

    public void setTargetSystems(Set<String> targetSystems) {
        this.targetSystems = targetSystems;
    }

}
