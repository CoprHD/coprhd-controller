/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class that captures the POST data passed in a request to create
 * a new BlockSnapshotSession instance.
 */
@XmlRootElement(name = "snapshot_session_create")
public class SnapshotSessionCreateParam {

    // The name for the snapshot session.
    private String name;

    // The new linked target information.
    private SnapshotSessionNewTargetsParam newLinkedTargets;

    // field for Application API
    private List<URI> volumes;

    /**
     * Default constructor.
     */
    public SnapshotSessionCreateParam() {
    }

    /**
     * Constructor.
     * 
     * @param name The name for the snapshot session.
     * @param newLinkedTargets A reference to the linked target information.
     */
    public SnapshotSessionCreateParam(String name, SnapshotSessionNewTargetsParam newLinkedTargets) {
        this.name = name;
        this.newLinkedTargets = newLinkedTargets;
    }

    /**
     * Constructor.
     * 
     * @param name The name for the snapshot session.
     * @param newLinkedTargets A reference to the linked target information.
     */
    public SnapshotSessionCreateParam(String name, SnapshotSessionNewTargetsParam newLinkedTargets,
            List<URI> volumes) {
        this(name, newLinkedTargets);
        this.volumes = volumes;
    }

    /**
     * Get the snapshot session name.
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
     * Gets the new targets parameter specifying info about new target volumes
     * to be created and linked to the newly created block snapshot session.
     * When not specified, no targets volumes will be created and linked to the
     * newly created snapshot session.
     * 
     * @return The new targets parameter specifying info about new target volumes
     *         to be created and linked to the newly created block snapshot session.
     */
    @XmlElement(name = "new_linked_targets", required = false)
    public SnapshotSessionNewTargetsParam getNewLinkedTargets() {
        return newLinkedTargets;
    }

    /**
     * Sets the new targets parameter specifying info about new target volumes
     * to be created and linked to the newly created block snapshot session.
     * 
     * @param newLinkedTargets The new targets parameter specifying info about new
     *            target volumes to be created and linked to the newly created
     *            block snapshot session.
     */
    public void setNewLinkedTargets(SnapshotSessionNewTargetsParam newLinkedTargets) {
        this.newLinkedTargets = newLinkedTargets;
    }

    @XmlElementWrapper(required = false, name = "volumes")
    /**
     * List of Volume IDs.
     * This field is applicable only if volume is part of an application.
     * Snapshot sessions of the replication groups (could be subset or full set of replication groups of an application)
     *  that the volumes belong to, will be created.
     *
     * Example: list of valid URIs
     */
    @XmlElement(required = false, name = "volume")
    public List<URI> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<URI>();
        }
        return volumes;
    }

    public void setVolumes(List<URI> volumes) {
        this.volumes = volumes;
    }
}
