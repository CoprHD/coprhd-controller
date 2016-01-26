/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class that captures the POST data passed in a request to unlink
 * target volumes from an existing block snapshot session.
 */
@XmlRootElement(name = "snapshot_session_unlink_targets")
public class SnapshotSessionUnlinkTargetsParam {

    // The list of targets to be unlinked.
    private List<SnapshotSessionUnlinkTargetParam> linkedTargets;

    /**
     * Default constructor.
     */
    public SnapshotSessionUnlinkTargetsParam() {
    }

    /**
     * Constructor.
     * 
     * @param linkedTargets The list of targets to be unlinked.
     */
    public SnapshotSessionUnlinkTargetsParam(List<SnapshotSessionUnlinkTargetParam> linkedTargets) {
        this.linkedTargets = linkedTargets;
    }

    @XmlElementWrapper(name = "linked_targets", required = true)
    /**
     * Gets the list of targets to be unlinked.
     * 
     * @valid none
     * 
     * @return The list of targets to be unlinked.
     */
    @XmlElement(name = "linked_target", required = true)
    public List<SnapshotSessionUnlinkTargetParam> getLinkedTargets() {
        return linkedTargets;
    }

    /**
     * Sets the list of targets to be unlinked.
     * 
     * @param linkedTargets The list of targets to be unlinked.
     */
    public void setLinkedTargets(List<SnapshotSessionUnlinkTargetParam> linkedTargets) {
        this.linkedTargets = linkedTargets;
    }
}
