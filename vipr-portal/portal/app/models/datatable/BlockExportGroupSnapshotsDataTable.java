/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static com.emc.sa.util.ResourceType.BLOCK_SNAPSHOT;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import util.BourneUtil;
import util.datatable.DataTable;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.resources.BlockSnapshots;

public class BlockExportGroupSnapshotsDataTable extends DataTable {

    public BlockExportGroupSnapshotsDataTable() {
        addColumn("name").setRenderFunction("renderSnapshotLink");
        addColumn("volume");
        addColumn("createdDate").setRenderFunction("render.localDate");
        addColumn("actions").setRenderFunction("renderSnapshotActions");
        sortAll();
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<ExportBlockSnapshot> fetch(URI exportGroupId) {
        if (exportGroupId == null) {
            return Collections.emptyList();
        }

        ViPRCoreClient client = BourneUtil.getViprClient();

        ExportGroupRestRep exportGroup = client.blockExports().get(exportGroupId);
        List<ExportBlockSnapshot> snapshots = Lists.newArrayList();
        for (ExportBlockParam exportBlockParam : exportGroup.getVolumes()) {
            if (ResourceType.isType(BLOCK_SNAPSHOT, exportBlockParam.getId())) {
                BlockSnapshotRestRep snapshot = client.blockSnapshots().get(exportBlockParam.getId());
                VolumeRestRep volume = client.blockVolumes().get(snapshot.getParent().getId());
                snapshots.add(new ExportBlockSnapshot(snapshot, volume.getName()));
            }
        }
        return snapshots;
    }

    public static class ExportBlockSnapshot {
        public String rowLink;
        public URI id;
        public String name;
        public Long createdDate;
        public String volume;

        public ExportBlockSnapshot(BlockSnapshotRestRep blockSnapshot, String volumeName) {
            this.id = blockSnapshot.getId();
            this.name = blockSnapshot.getName();
            if (blockSnapshot.getCreationTime() != null) {
                this.createdDate = blockSnapshot.getCreationTime().getTime().getTime();
            }
            this.volume = volumeName;
            this.rowLink = createLink(BlockSnapshots.class, "snapshotDetails", "snapshotId", id);
        }

    }

}