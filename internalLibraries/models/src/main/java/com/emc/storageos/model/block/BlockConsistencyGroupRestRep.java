/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
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
import com.emc.storageos.model.adapters.StringSetMapAdapter;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "consistency_group")
public class BlockConsistencyGroupRestRep extends DataObjectRestRep {

    private List<RelatedResourceRep> volumes;
    private RelatedResourceRep storageController;
    private RelatedResourceRep project;
    private RelatedResourceRep virtualArray;
    private Set<String> types;
    private String linkStatus;
    private Boolean supportsSnapshotSessions;
    private Boolean arrayConsistency;

    // RecoverPoint fields
    private URI rpProtectionSystem;
    private String rpConsistenyGroupId;

    // VPlex fields
    private List<StringSetMapAdapter.Entry> systemConsistencyGroups;

    /**
     * Related storage controller
     * 
     */
    @XmlElement(name = "storage_controller")
    public RelatedResourceRep getStorageController() {
        return storageController;
    }

    public void setStorageController(RelatedResourceRep storageController) {
        this.storageController = storageController;
    }

    /**
     * Related project
     * 
     */
    @XmlElement(name = "project")
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * Related virtual array
     * 
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public RelatedResourceRep getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(RelatedResourceRep virtualArray) {
        this.virtualArray = virtualArray;
    }

    @XmlElementWrapper(name = "volumes")
    /**
     * A volume that exists within the block consistency group
     *
     */
    @XmlElement(name = "volume")
    public List<RelatedResourceRep> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<RelatedResourceRep>();
        }
        return volumes;
    }

    public void setVolumes(List<RelatedResourceRep> volumes) {
        this.volumes = volumes;
    }

    /**
     * The mapping of protection systems/storage systems to consistency groups that
     * are mapped to by the BlockConsistencyGroup.
     * 
     */
    @XmlElement(name = "system_consistency_groups")
    public List<StringSetMapAdapter.Entry> getSystemConsistencyGroups() {
        return systemConsistencyGroups;
    }

    public void setSystemConsistencyGroups(List<StringSetMapAdapter.Entry> systemConsistencyGroups) {
        this.systemConsistencyGroups = systemConsistencyGroups;
    }

    /**
     * The types of the block consistency group
     * 
     */
    @XmlElement(name = "types")
    public Set<String> getTypes() {
        if (types == null) {
            types = new HashSet<String>();
        }
        return types;
    }

    public void setTypes(Set<String> types) {
        this.types = types;
    }

    /**
     * The RecoverPoint protection system. Applies only to CGs of type RP.
     * 
     */
    @XmlElement(name = "rp_protection_system")
    public URI getRpProtectionSystem() {
        return rpProtectionSystem;
    }

    public void setRpProtectionSystem(URI rpProtectionSystem) {
        this.rpProtectionSystem = rpProtectionSystem;
    }

    /**
     * The RecoverPoint consistency group id. Applies only to CGs of type RP.
     * 
     */
    @XmlElement(name = "rp_consistency_group_id")
    public String getRpConsistenyGroupId() {
        return rpConsistenyGroupId;
    }

    public void setRpConsistenyGroupId(String rpConsistenyGroupId) {
        this.rpConsistenyGroupId = rpConsistenyGroupId;
    }

    /**
     * The link status.
     * 
     */
    @XmlElement(name = "link_status")
    public String getLinkStatus() {
        return linkStatus;
    }

    public void setLinkStatus(String linkStatus) {
        this.linkStatus = linkStatus;
    }
    
    /**
     * Specifies whether this is volume supports Snapshot Sessions.
     * 
     * @return true if volume supports Snapshot Sessions, false otherwise
     */
    @XmlElement(name = "supports_snapshot_sessions")
    public Boolean getSupportsSnapshotSessions() {
        return supportsSnapshotSessions;
    }

    public void setSupportsSnapshotSessions(Boolean supportsSnapshotSessions) {
        this.supportsSnapshotSessions = supportsSnapshotSessions;
    }

    /**
     * Flag which says if backend Replication Group needs to be created or not.
     *
     */
    @XmlElement(name = "array_consistency")
    public Boolean getArrayConsistency() {
        return arrayConsistency;
    }

    public void setArrayConsistency(Boolean arrayConsistency) {
        this.arrayConsistency = arrayConsistency;
    }
}
