/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class that captures the POST data passed in a request to link
 * target volumes to an existing block snapshot session.
 */
@XmlRootElement(name = "snapshot_session_link_targets")
public class SnapshotSessionLinkTargetsParam {

    // The new linked target information.
    private SnapshotSessionNewTargetsParam newLinkedTargets;

    /**
     * Default constructor.
     */
    public SnapshotSessionLinkTargetsParam() {
    }

    /**
     * Constructor.
     * 
     * @param newLinkedTargets A reference to the new linked target information.
     */
    public SnapshotSessionLinkTargetsParam(SnapshotSessionNewTargetsParam newLinkedTargets) {
        this.newLinkedTargets = newLinkedTargets;
    }

    /**
     * Gets the new targets parameter specifying info about new target volumes
     * to be created and linked to the block snapshot session.
     * 
     * @return The new targets parameter specifying info about new target volumes
     *         to be created and linked to the block snapshot session.
     */
    @XmlElement(name = "new_linked_targets", required = true)
    public SnapshotSessionNewTargetsParam getNewLinkedTargets() {
        return newLinkedTargets;
    }

    /**
     * Sets the new targets parameter specifying info about new target volumes
     * to be created and linked to the block snapshot session.
     * 
     * @param newLinkedTargets The new targets parameter specifying info about new target volumes
     *            to be created and linked to the block snapshot session.
     */
    public void setNewLinkedTargets(SnapshotSessionNewTargetsParam newLinkedTargets) {
        this.newLinkedTargets = newLinkedTargets;
    }
}
