/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class that captures the POST data passed in a request to relink
 * target volumes to the same or new block snapshot session.
 */
@XmlRootElement(name = "snapshot_session_relink_targets")
public class SnapshotSessionRelinkTargetsParam {

    // The new linked target information.
    private List<URI> linkedTargetIds;

    /**
     * Default constructor.
     */
    public SnapshotSessionRelinkTargetsParam() {
    }

    /**
     * Constructor.
     * 
     * @param linkedTargetIds The URIs of the linked targets to be re-linked.
     */
    public SnapshotSessionRelinkTargetsParam(List<URI> linkedTargetIds) {
        this.linkedTargetIds = linkedTargetIds;
    }

    /**
     * Gets the URIs of the linked targets to be re-linked.
     * 
     * @return The URIs of the linked targets to be re-linked.
     */
    @XmlElementWrapper(name = "ids", required = true)
    @XmlElement(name = "id", required = true)
    public List<URI> getLinkedTargetIds() {
        return linkedTargetIds;
    }

    /**
     * Sets the URIs of the linked targets to be re-linked.
     * 
     * @param linkedTargetIds The URIs of the linked targets to be re-linked.
     */
    public void setLinkedTargetIds(List<URI> linkedTargetIds) {
        this.linkedTargetIds = linkedTargetIds;
    }
}
