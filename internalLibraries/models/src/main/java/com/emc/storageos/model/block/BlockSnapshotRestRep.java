/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    /** 
     * URI and reference link to the volume that is the
     * source for the snapshot.
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
     * @valid none
     */
    @XmlElement(name = "source_native_id")
    public String getSourceNativeId() {
        return sourceNativeId;
    }

    public void setSourceNativeId(String sourceNativeId) {
        this.sourceNativeId = sourceNativeId;
    }
}
