/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.emc.storageos.model.RelatedResourceRep;

/**
 * 
 */
public class BlockSnapshotSessionRestRep extends BlockObjectRestRep {

    private RelatedResourceRep parent;
    private RelatedResourceRep project;
    private List<RelatedResourceRep> linkedTargets;
    private String sessionLabel;
    private Boolean syncActive;
    
    /** 
     * URI and reference link to the snapshot session source.
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
     * URI and reference link of the project to which the snapshot belongs.
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
     * List of target volumes, i.e., BlockSnapshot instances, linked to the
     * block snapshot session.
     * 
     * @valid none
     */
    @XmlElementWrapper(name = "linked_targets")
    @XmlElement(name = "linked_target")
    public List<RelatedResourceRep> getLinkedTarget() {
        if (linkedTargets == null) {
            linkedTargets = new ArrayList<RelatedResourceRep>();
        }
        return linkedTargets;
    }

    public void setLinkedTargets(List<RelatedResourceRep> linkedTargets) {
        this.linkedTargets = linkedTargets;
    }
    
    /** 
     * User specified session label.
     * 
     * @valid none
     */
    @XmlElement(name = "session_label")
    public String getSessionLabel() {
        return sessionLabel;
    }

    public void setSessionLabel(String sessionLabel) {
        this.sessionLabel = sessionLabel;
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
}
