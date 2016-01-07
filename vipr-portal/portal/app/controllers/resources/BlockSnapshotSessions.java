/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;

import controllers.Common;
import models.datatable.BlockSnapshotsDataTable;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
public class BlockSnapshotSessions extends ResourceController {
    private static final String UNKNOWN = "resources.snapshot.unknown";

    // Update for Session
    private static BlockSnapshotsDataTable blockSnapshotsDataTable = new BlockSnapshotsDataTable();

    // Update for Session
    public static void snapshotSessions(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", blockSnapshotsDataTable);
        addReferenceData();
        render();
    }

    // Update for Session
    public static void snapshotsJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        } else {
            projectId = getActiveProjectId();
        }
        List<BlockSnapshotsDataTable.BlockSnapshot> blockSnapshots = BlockSnapshotsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(blockSnapshots, params));
    }

    public static void snapshotSessionDetails(String snapshotSessionId) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        AffectedResources.BlockSnapshotSessionDetails blockSnapshotSession = new AffectedResources.BlockSnapshotSessionDetails(uri(snapshotSessionId));
        if (blockSnapshotSession.blockSnapshotSession == null) {
            flash.error(MessagesUtils.get(UNKNOWN, snapshotSessionId));
            snapshotSessions(null);
        }

        AffectedResources.VolumeDetails volume = new AffectedResources.VolumeDetails(blockSnapshotSession.volume.getId());

        List<Task<BlockSnapshotSessionRestRep>> tasks = null;
        if (blockSnapshotSession.blockSnapshotSession != null) {
            Tasks<BlockSnapshotSessionRestRep> tasksResponse = client.blockSnapshotSessions().getTasks(blockSnapshotSession.blockSnapshotSession.getId());
            tasks = tasksResponse.getTasks();
        }

        render(blockSnapshotSession, volume, tasks);
    }
}
