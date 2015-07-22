/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.BulkRestRep;

/**
 *
 */
public class BlockSnapshotSessionBulkRep extends BulkRestRep {

    private List<BlockSnapshotSessionRestRep> blockSnapshotSessions;

    public BlockSnapshotSessionBulkRep() {
    }

    public BlockSnapshotSessionBulkRep(List<BlockSnapshotSessionRestRep> blockSnapshotSessions) {
        this.blockSnapshotSessions = blockSnapshotSessions;
    }

    /**
     * List of block snapshot sessions.
     * 
     * @valid none
     */
    @XmlElement(name = "block_snapshot_session")
    public List<BlockSnapshotSessionRestRep> getBlockSnapshotSessions() {
        if (blockSnapshotSessions == null) {
            blockSnapshotSessions = new ArrayList<BlockSnapshotSessionRestRep>();
        }
        return blockSnapshotSessions;
    }

    public void setBlockSnapshotSessions(List<BlockSnapshotSessionRestRep> blockSnapshotSessions) {
        this.blockSnapshotSessions = blockSnapshotSessions;
    }
}
