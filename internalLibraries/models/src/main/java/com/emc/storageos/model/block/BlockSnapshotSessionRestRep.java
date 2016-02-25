/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RelatedResourceRep;

/**
 * Class encapsulates the data returned in response to a request
 * for a BlockSnapshotSession instance.
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "block_snapshot_session")
public class BlockSnapshotSessionRestRep extends BlockObjectRestRep {

    // Related resource representation for the snapshot session source object.
    private RelatedResourceRep parent;

    // Related resource representation for the snapshot session source project.
    private RelatedResourceRep project;

    // Related resource representations for the BlockSnapshot instances
    // representing the targets linked to the snapshot session.
    private List<RelatedResourceRep> linkedTargets;

    // The session label.
    private String sessionLabel;

    // The source replication group for which the snapshot session is created for.
    private String replicationGroupInstance;

    // The session set name to group all snapshot sessions created for replication groups in an Application.
    private String sessionSetName;

    /**
     * URI and reference link to the snapshot session source.
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
     * URI and reference link of the project to which the snapshot belongs.
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
     * List of target volumes, i.e., BlockSnapshot instances, linked to the
     * block snapshot session.
     * 
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
     */
    @XmlElement(name = "session_label")
    public String getSessionLabel() {
        return sessionLabel;
    }

    public void setSessionLabel(String sessionLabel) {
        this.sessionLabel = sessionLabel;
    }

    /**
     * Source Replication Group name for which this session is created for.
     * 
     */
    @XmlElement(name = "replication_group_instance")
    public String getReplicationGroupInstance() {
        return replicationGroupInstance;
    }

    public void setReplicationGroupInstance(String replicationGroupInstance) {
        this.replicationGroupInstance = replicationGroupInstance;
    }

    /**
     * User specified name while creating sessions for Replication Groups.
     * 
     */
    @XmlElement(name = "session_set_name")
    public String getSessionSetName() {
        return sessionSetName;
    }

    public void setSessionSetName(String sessionSetName) {
        this.sessionSetName = sessionSetName;
    }
}
