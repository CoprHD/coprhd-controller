/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class that captures the POST data for a target to
 * be unlinked from a block snapshot session.
 */
public class SnapshotSessionUnlinkTargetParam {

    // The id of a BlockSnapshot representing a linked target for a
    // block snapshot session.
    private URI id;

    // Whether or not the target should be deleted.
    private Boolean deleteTarget;

    /**
     * Default constructor.
     */
    public SnapshotSessionUnlinkTargetParam() {
    }

    /**
     * Constructor.
     * 
     * @param id The id of a BlockSnapshot representing a linked target for a block snapshot session.
     * @param deleteTarget Whether or not the target should be deleted.
     */
    public SnapshotSessionUnlinkTargetParam(URI id, Boolean deleteTarget) {
        this.id = id;
        this.deleteTarget = deleteTarget;
    }

    /**
     * Get the id of a BlockSnapshot representing a linked target.
     * 
     * @return The id of a BlockSnapshot representing a linked target.
     */
    @XmlElement(required = true)
    public URI getId() {
        return id;
    }

    /**
     * Set the id of a BlockSnapshot representing a linked target.
     * 
     * @param id The id of a BlockSnapshot representing a linked target.
     */
    public void setId(URI id) {
        this.id = id;
    }

    /**
     * Get whether or not the target should be deleted.
     * 
     * Valid values:
     *     true
     *     false
     * 
     * @return Whether or not the target should be deleted.
     */
    @XmlElement(name = "delete_target", required = false, defaultValue = "false")
    public Boolean getDeleteTarget() {
        return deleteTarget;
    }

    /**
     * Set whether or not the target should be deleted.
     * 
     * @param deleteTarget Whether or not the target should be deleted.
     */
    public void setDeleteTarget(Boolean deleteTarget) {
        this.deleteTarget = deleteTarget;
    }
}
