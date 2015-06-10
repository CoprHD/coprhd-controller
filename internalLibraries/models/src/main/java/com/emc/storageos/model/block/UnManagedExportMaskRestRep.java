/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "unmanaged_export_mask")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class UnManagedExportMaskRestRep extends DataObjectRestRep {

    private RelatedResourceRep storageSystem;
    private String maskName;
    private String nativeId;
    
    private List<RelatedResourceRep> knownInitiatorUris;
    private Set<String> unmanagedInitiatorNetworkIds;
    private List<RelatedResourceRep> knownStoragePortUris;
    private Set<String> unmanagedStoragePortNetworkIds;
    private List<RelatedResourceRep> knownStorageVolumeUris;
    private List<RelatedResourceRep> unmanagedVolumeUris;

    public UnManagedExportMaskRestRep() {}
    
    @XmlElement(name = "known_initiators")
    public List<RelatedResourceRep> getKnownInitiatorUris() {
        if (knownInitiatorUris == null) {
            knownInitiatorUris = new ArrayList<RelatedResourceRep>();
        }
        return knownInitiatorUris;
    }

    public void setKnownInitiatorUris(List<RelatedResourceRep> knownInitiatorUris) {
        this.knownInitiatorUris = knownInitiatorUris;
    }

    @XmlElementWrapper(name = "unmanaged_initiator_network_ids")
    @XmlElement(name = "network_id")
    public Set<String> getUnmanagedInitiatorNetworkIds() {
        if (unmanagedInitiatorNetworkIds == null) {
            unmanagedInitiatorNetworkIds = new HashSet<String>();
        }
        return unmanagedInitiatorNetworkIds;
    }

    public void setUnmanagedInitiatorNetworkIds(
            Set<String> unmanagedInitiatorNetworkIds) {
        this.unmanagedInitiatorNetworkIds = unmanagedInitiatorNetworkIds;
    }

    @XmlElement(name = "known_storage_ports")
    public List<RelatedResourceRep> getKnownStoragePortUris() {
        if (knownStoragePortUris == null) {
            knownStoragePortUris = new ArrayList<RelatedResourceRep>();
        }
        return knownStoragePortUris;
    }

    public void setKnownStoragePortUris(
            List<RelatedResourceRep> knownStoragePortUris) {
        this.knownStoragePortUris = knownStoragePortUris;
    }

    @XmlElementWrapper(name = "unmanaged_storage_port_network_ids")
    @XmlElement(name = "network_id")
    public Set<String> getUnmanagedStoragePortNetworkIds() {
        if (unmanagedStoragePortNetworkIds == null) {
            unmanagedStoragePortNetworkIds = new HashSet<String>();
        }
        return unmanagedStoragePortNetworkIds;
    }

    public void setUnmanagedStoragePortNetworkIds(
            Set<String> unmanagedStoragePortNetworkIds) {
        this.unmanagedStoragePortNetworkIds = unmanagedStoragePortNetworkIds;
    }

    @XmlElement(name = "known_storage_volumes")
    public List<RelatedResourceRep> getKnownStorageVolumeUris() {
        if (knownStorageVolumeUris == null) {
            knownStorageVolumeUris = new ArrayList<RelatedResourceRep>();
        }
        return knownStorageVolumeUris;
    }

    public void setKnownStorageVolumeUris(
            List<RelatedResourceRep> knownStorageVolumeUris) {
        this.knownStorageVolumeUris = knownStorageVolumeUris;
    }

    @XmlElement(name = "unmanaged_volumes")
    public List<RelatedResourceRep> getUnmanagedVolumeUris() {
        if (unmanagedVolumeUris == null) {
            unmanagedVolumeUris = new ArrayList<RelatedResourceRep>();
        }
        return unmanagedVolumeUris;
    }

    public void setUnmanagedVolumeUris(
            List<RelatedResourceRep> unmanagedVolumeUris) {
        this.unmanagedVolumeUris = unmanagedVolumeUris;
    }

    @XmlElement(name = "storage_system")
    public RelatedResourceRep getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(RelatedResourceRep storageSystem) {
        this.storageSystem = storageSystem;
    }

    @XmlElement(name = "mask_name")
    public String getMaskName() {
        return maskName;
    }

    public void setMaskName(String maskName) {
        this.maskName = maskName;
    }

    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }
}
