/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class that captures the POST data passed in a request to create
 * a new BlockSnapshotSession instance.
 */
@XmlRootElement(name = "snapshot_session_create")
public class SnapshotSessionCreateParam {

    // The name for the snapshot session.
    private String name;

    // The linked target information.
    private SnapshotSessionTargetsParam linkedTargets;

    /**
     * Default constructor.
     */
    public SnapshotSessionCreateParam() {
    }

    /**
     * Constructor.
     * 
     * @param name The name for the snapshot session.
     * @param linkedTargets A reference to the linked target information.
     */
    public SnapshotSessionCreateParam(String name, SnapshotSessionTargetsParam linkedTargets) {
        this.name = name;
        this.linkedTargets = linkedTargets;
    }

    /**
     * Get the snapshot session name.
     * 
     * @valid none
     * 
     * @return The snapshot session name.
     */
    @XmlElement
    public String getName() {
        return name;
    }

    /**
     * Set the snapshot session name.
     * 
     * @param name The snapshot session name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the target volumes to be linked to the newly created
     * block snapshot session. When not specified, no targets volumes
     * will be linked to the newly created snapshot session.
     * 
     * @valid none
     * 
     * @return The target volumes to be linked to the newly created
     *         block snapshot session.
     */
    @XmlElement(name = "linked_targets", required = false)
    public SnapshotSessionTargetsParam getLinkedTargets() {
        return linkedTargets;
    }

    /**
     * sets the target volumes to be linked to the newly created
     * block snapshot session.
     * 
     * @param linkedTargets The target volumes to be linked to the newly
     *            created block snapshot session.
     */
    public void setLinkedTargets(SnapshotSessionTargetsParam linkedTargets) {
        this.linkedTargets = linkedTargets;
    }
}
