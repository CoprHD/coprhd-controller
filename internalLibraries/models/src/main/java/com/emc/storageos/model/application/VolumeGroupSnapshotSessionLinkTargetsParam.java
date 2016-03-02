/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.block.SnapshotSessionNewTargetsParam;

/**
 * Class that captures the POST data passed in a request to link
 * target volumes to an existing block snapshot sessions in the volume group.
 */
@XmlRootElement(name = "volume_group_snapshot_session_link_targets")
public class VolumeGroupSnapshotSessionLinkTargetsParam extends VolumeGroupSnapshotSessionOperationParam {

    // The new linked target information.
    private SnapshotSessionNewTargetsParam newLinkedTargets;

    /**
     * Default constructor.
     */
    public VolumeGroupSnapshotSessionLinkTargetsParam() {
    }

    /**
     * Constructor.
     * 
     * @param newLinkedTargets A reference to the new linked target information.
     */
    public VolumeGroupSnapshotSessionLinkTargetsParam(SnapshotSessionNewTargetsParam newLinkedTargets) {
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
