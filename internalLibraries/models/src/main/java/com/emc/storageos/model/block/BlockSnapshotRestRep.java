/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.block;

import com.emc.storageos.model.RelatedResourceRep;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

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

    /**
     * URI and reference link to the volume that is the
     * source for the snapshot.
     * 
     * @valid none
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
     * @valid true
     * @valid false
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
     * @valid none
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
     * @valid none
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
     * @valid none
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
     * @valid true
     * @valid false
     */
    @XmlElement(name = "read_only")
    public Boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }
}
