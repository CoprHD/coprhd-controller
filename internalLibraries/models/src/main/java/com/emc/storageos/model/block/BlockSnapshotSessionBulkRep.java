/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

/**
 * Class encapsulates the data returned in response to a bulk request
 * for multiple BlockSnapshotSession instances.
 */
@XmlRootElement(name = "bulk_block_snapshot_sessions")
public class BlockSnapshotSessionBulkRep extends BulkRestRep {

    // A list of BlockSnapshotSession instance response objects.
    private List<BlockSnapshotSessionRestRep> blockSnapshotSessions;

    /**
     * Default constructor.
     */
    public BlockSnapshotSessionBulkRep() {
    }

    /**
     * Constructor.
     * 
     * @param blockSnapshotSessions A list of BlockSnapshotSession instance response objects.
     */
    public BlockSnapshotSessionBulkRep(List<BlockSnapshotSessionRestRep> blockSnapshotSessions) {
        this.blockSnapshotSessions = blockSnapshotSessions;
    }

    /**
     * Get the list of block snapshot sessions responses.
     * 
     * @return The list of block snapshot sessions responses
     */
    @XmlElement(name = "block_snapshot_session")
    public List<BlockSnapshotSessionRestRep> getBlockSnapshotSessions() {
        if (blockSnapshotSessions == null) {
            blockSnapshotSessions = new ArrayList<BlockSnapshotSessionRestRep>();
        }
        return blockSnapshotSessions;
    }

    /**
     * Set the list of block snapshot sessions responses.
     * 
     * @param blockSnapshotSessions The list of block snapshot sessions responses.
     */
    public void setBlockSnapshotSessions(List<BlockSnapshotSessionRestRep> blockSnapshotSessions) {
        this.blockSnapshotSessions = blockSnapshotSessions;
    }
}
