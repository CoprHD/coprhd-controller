/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * List of NamedRelatedResourceRep instances representing BlockSnapshotSession instances.
 */
@XmlRootElement(name = "snapshot_sessions")
public class BlockSnapshotSessionList {

    // The list of NamedRelatedResourceRep for the BlockSnapshotSession instances.
    private List<NamedRelatedResourceRep> snapSessionRelatedResourceList;

    /**
     * Default Constructor
     */
    public BlockSnapshotSessionList() {
    }

    /**
     * Constructor.
     * 
     * @param snapSessionRelatedResourceList The list of NamedRelatedResourceRep for the
     *            BlockSnapshotSession instances.
     */
    public BlockSnapshotSessionList(List<NamedRelatedResourceRep> snapSessionRelatedResourceList) {
        this.snapSessionRelatedResourceList = snapSessionRelatedResourceList;
    }

    /**
     * Get the list of NamedRelatedResourceRep for the BlockSnapshotSession instances.
     * 
     * @return The list of NamedRelatedResourceRep for the BlockSnapshotSession instances.
     */
    @XmlElement(name = "snapshot_session")
    public List<NamedRelatedResourceRep> getSnapSessionRelatedResourceList() {
        if (snapSessionRelatedResourceList == null) {
            snapSessionRelatedResourceList = new ArrayList<NamedRelatedResourceRep>();
        }
        return snapSessionRelatedResourceList;
    }

    /**
     * Set the list of NamedRelatedResourceRep for the BlockSnapshotSession instances.
     * 
     * @param snapSessionRelatedResourceList The list of NamedRelatedResourceRep for the
     *            BlockSnapshotSession instances.
     */
    public void setSnapSessionRelatedResourceList(List<NamedRelatedResourceRep> snapSessionRelatedResourceList) {
        this.snapSessionRelatedResourceList = snapSessionRelatedResourceList;
    }
}