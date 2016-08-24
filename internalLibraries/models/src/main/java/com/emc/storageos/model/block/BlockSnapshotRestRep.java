/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "block_snapshot")
public class BlockSnapshotRestRep extends BlockObjectRestRep {
    private RelatedResourceRep parent;
    private RelatedResourceRep project;
    private String newVolumeNativeId;
    private String sourceNativeId;
    private Boolean syncActive;
    private String replicaState;
    private Boolean readOnly;
    private String snapsetLabel;
    private String provisionedCapacity;
    private String allocatedCapacity;
    private String technologyType;

    /**
     * URI and reference link to the volume that is the
     * source for the snapshot.
     * 
     */
    @XmlElement
    public RelatedResourceRep getParent() {
        return parent;
    }

    public void setParent(RelatedResourceRep parent) {
        this.parent = parent;
    }

    /**
     * Whether or not the snapshot is active.
     * 
     */
    @XmlElement(name = "is_sync_active")
    public Boolean getSyncActive() {
        return syncActive;
    }

    public void setSyncActive(Boolean syncActive) {
        this.syncActive = syncActive;
    }

    /**
     * ID of the snapshot resource.
     * 
     */
    @XmlElement(name = "volume_native_id")
    public String getNewVolumeNativeId() {
        return newVolumeNativeId;
    }

    public void setNewVolumeNativeId(String newVolumeNativeId) {
        this.newVolumeNativeId = newVolumeNativeId;
    }

    /**
     * URI of the project to which the snapshot belongs.
     * 
     */
    @XmlElement
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * ID of the volume that is the snapshot's source.
     * 
     */
    @XmlElement(name = "source_native_id")
    public String getSourceNativeId() {
        return sourceNativeId;
    }

    public void setSourceNativeId(String sourceNativeId) {
        this.sourceNativeId = sourceNativeId;
    }

    @XmlElement(name = "replica_state")
    public String getReplicaState() {
        return replicaState;
    }

    public void setReplicaState(String replicaState) {
        this.replicaState = replicaState;
    }

    /**
     * Returns the read-only status of the snapshot.
     *
     * 
     */
    @XmlElement(name = "read_only")
    public Boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    /**
     * Label for snapshots generated at the same time, with the same consistency group or volume group.
     */
    @XmlElement(name = "snapset_label")
    public String getSnapsetLabel() {
        return snapsetLabel;
    }

    public void setSnapsetLabel(String snapsetLabel) {
        this.snapsetLabel = snapsetLabel;
    }

    /**
     * This snapshot's logical capacity in Gb (Gigabytes).
     * 
     */
    @XmlElement(name = "provisioned_capacity_gb")
    public String getProvisionedCapacity() {
        return provisionedCapacity;
    }

    public void setProvisionedCapacity(String provisionedCapacity) {
        this.provisionedCapacity = provisionedCapacity;
    }

    /**
     * The total amount of space allocated from the snapshot's storage pool (GB)
     * 
     */
    @XmlElement(name = "allocated_capacity_gb")
    public String getAllocatedCapacity() {
        return allocatedCapacity;
    }

    public void setAllocatedCapacity(String allocatedCapacity) {
        this.allocatedCapacity = allocatedCapacity;
    }
    
    /**
     * The technology type of this snapshot 
     * 
     */
    @XmlElement(name = "technology_type")
    public String getTechnologyType() {
        return technologyType;
    }

    public void setTechnologyType(String technologyType) {
        this.technologyType = technologyType;
    }
}
