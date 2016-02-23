/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import util.datatable.DataTable;

import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.resources.BlockSnapshotSessions;
import controllers.resources.BlockSnapshots;

public class BlockSnapshotSessionsDataTable extends DataTable {

    public BlockSnapshotSessionsDataTable() {
        addColumn("name");
        addColumn("volume");
        addColumn("createdDate").setRenderFunction("render.localDate");
        sortAll();
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<BlockSnapshotSession> fetch(URI projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }

        ViPRCoreClient client = getViprClient();
        List<BlockSnapshotSessionRestRep> blockSnapshots = client.blockSnapshotSessions().findByProject(projectId);
        Map<URI, VolumeRestRep> parentVolumes = getParentVolumes(blockSnapshots);

        List<BlockSnapshotSession> results = Lists.newArrayList();
        for (BlockSnapshotSessionRestRep blockSnapshot : blockSnapshots) {
        	BlockSnapshotSession snap = new BlockSnapshotSession(blockSnapshot);
            // Get the parent volume of the snapshot
            VolumeRestRep volume = parentVolumes.get(ResourceUtils.id(blockSnapshot.getParent()));
            snap.volume = ResourceUtils.name(volume);
            results.add(snap);
        }
        return results;
    }

    private static Map<URI, VolumeRestRep> getParentVolumes(List<BlockSnapshotSessionRestRep> snapshots) {
        // Collect the volume IDs of each block snapshot to query in one shot
        Set<URI> volumeIds = Sets.newHashSet();
        for (BlockSnapshotSessionRestRep snapshot : snapshots) {
            URI id = ResourceUtils.id(snapshot.getParent());
            if (id != null) {
                volumeIds.add(id);
            }
        }
        // Query all the volumes at once, and map by ID
        return ResourceUtils.mapById(getViprClient().blockVolumes().getByIds(volumeIds));
    }

    public static class BlockSnapshotSession {
        public String rowLink;
        public URI id;
        public String name;
        public Long createdDate;
        public String volume;

        public BlockSnapshotSession(BlockSnapshotSessionRestRep blockSnapshot) {
            id = blockSnapshot.getId();
            name = blockSnapshot.getName();
            if (blockSnapshot.getCreationTime() != null) {
                this.createdDate = blockSnapshot.getCreationTime().getTime().getTime();
            }
            this.rowLink = createLink(BlockSnapshotSessions.class, "snapshotSessionDetails", "snapshotSessionId", id);
        }
    }
}
