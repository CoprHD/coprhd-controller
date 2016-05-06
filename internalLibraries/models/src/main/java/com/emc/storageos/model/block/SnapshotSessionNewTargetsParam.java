/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class that captures the POST data representing the linked targets passed
 * in a request to create a new BlockSnapshotSession instance.
 */
public class SnapshotSessionNewTargetsParam {

    // The number of targets to be created and linked to the session.
    private Integer count;

    // The name to use for the targets.
    private String targetName;

    // The copy mode for the targets when they are linked as specified
    // by BlockSnapshotSession.CopyMode.
    private String copyMode;

    /**
     * Default Constructor.
     */
    public SnapshotSessionNewTargetsParam() {
    }

    /**
     * Constructor.
     * 
     * @param count The number of targets to be created and linked to the session.
     * @param targetName The name to use for the targets.
     * @param copyMode The copy mode for the targets when they are linked to the session.
     */
    public SnapshotSessionNewTargetsParam(Integer count, String targetName, String copyMode) {
        this.count = count;
        this.targetName = targetName;
        this.copyMode = copyMode;
    }

    /**
     * Get the number of new targets to create and link to the snapshot session.
     * 
     * @return The number of new targets to create and link to the snapshot session.
     */
    @XmlElement(required = true)
    public Integer getCount() {
        return count;
    }

    /**
     * Set the number of new targets to create and link to the snapshot session.
     * 
     * @param count The number of new targets to create and link to the snapshot session.
     */
    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * Get the name to use for the targets.
     * 
     * @return The name to use for the targets.
     */
    @XmlElement(name = "target_name", required = true)
    public String getTargetName() {
        return targetName;
    }

    /**
     * Set the name to use for the targets.
     * 
     * @param targetName The name to use for the targets.
     */
    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    /**
     * Get the copy mode for the new target volumes to be linked to
     * the block snapshot session. A volume that is linked to a
     * snapshot session using "copy" copy_mode and achieves the
     * "copied" state will contain a full, usable copy of the
     * snapshot session source device upon being unlinked from
     * the session. This is not true for volumes linked in "nocopy"
     * copy-mode.
     * 
     * Valid values:
     *     copy
     *     nocopy
     * 
     * @return The copy mode for the new target volumes to be linked to
     *         the block snapshot session.
     */
    @XmlElement(name = "copy_mode", required = false, defaultValue = "nocopy")
    public String getCopyMode() {
        return copyMode;
    }

    /**
     * Set the copy mode for the new target volumes to be linked to
     * the block snapshot session.
     * 
     * @param copyMode The copy mode for the new target volumes to be linked to
     *            the block snapshot session.
     */
    public void setCopyMode(String copyMode) {
        this.copyMode = copyMode;
    }
}
