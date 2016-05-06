/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.BulkRestRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_block_snapshots")
public class BlockSnapshotBulkRep extends BulkRestRep {
    private List<BlockSnapshotRestRep> blockSnapshots;

    /**
     * List of Block Snapshots.
     * 
     */
    @SuppressWarnings("unchecked")
    @XmlElement(name = "block_snapshot")
    public List<BlockSnapshotRestRep> getBlockSnapshots() {
        if (blockSnapshots == null) {
            blockSnapshots = new ArrayList<BlockSnapshotRestRep>();
        }
        return blockSnapshots;
    }

    public void setBlockSnapshots(List<BlockSnapshotRestRep> blockSnapshots) {
        this.blockSnapshots = blockSnapshots;
    }

    public BlockSnapshotBulkRep() {
    }

    public BlockSnapshotBulkRep(List<BlockSnapshotRestRep> blockSnapshots) {
        this.blockSnapshots = blockSnapshots;
    }
}
