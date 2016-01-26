/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.List;
import java.util.Map;

import models.datatable.BlockSnapshotsDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import controllers.Common;
import controllers.util.FlashException;

@With(Common.class)
public class BlockSnapshots extends ResourceController {
    private static final String UNKNOWN = "resources.snapshot.unknown";

    private static BlockSnapshotsDataTable blockSnapshotsDataTable = new BlockSnapshotsDataTable();

    public static void snapshots(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", blockSnapshotsDataTable);
        addReferenceData();
        render();
    }

    public static void snapshotsJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        } else {
            projectId = getActiveProjectId();
        }
        List<BlockSnapshotsDataTable.BlockSnapshot> blockSnapshots = BlockSnapshotsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(blockSnapshots, params));
    }

    public static void snapshotDetails(String snapshotId) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        AffectedResources.BlockSnapshotDetails blockSnapshot = new AffectedResources.BlockSnapshotDetails(uri(snapshotId));
        if (blockSnapshot.blockSnapshot == null) {
            flash.error(MessagesUtils.get(UNKNOWN, snapshotId));
            snapshots(null);
        }

        AffectedResources.VolumeDetails volume = new AffectedResources.VolumeDetails(blockSnapshot.volume.getId());

        List<Task<BlockSnapshotRestRep>> tasks = null;
        if (blockSnapshot.blockSnapshot != null) {
            Tasks<BlockSnapshotRestRep> tasksResponse = client.blockSnapshots().getTasks(blockSnapshot.blockSnapshot.getId());
            tasks = tasksResponse.getTasks();
        }

        render(blockSnapshot, volume, tasks);
    }

    public static void snapshotExports(String snapshotId) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        Map<URI, ExportGroupRestRep> exportGroups = Maps.newHashMap();
        Map<URI, List<ITLRestRep>> exportGroupItlMap = Maps.newHashMap();

        List<ITLRestRep> itls = client.blockSnapshots().listExports(uri(snapshotId));
        for (ITLRestRep itl : itls) {
            NamedRelatedResourceRep export = itl.getExport();
            if (export != null && export.getId() != null) {
                List<ITLRestRep> exportGroupItls = exportGroupItlMap.get(export.getId());
                if (exportGroupItls == null) {
                    exportGroupItls = Lists.newArrayList();
                    exportGroupItlMap.put(export.getId(), exportGroupItls);
                }
                exportGroupItls.add(itl);

                if (exportGroups.keySet().contains(export.getId()) == false) {
                    ExportGroupRestRep exportGroup = client.blockExports().get(export.getId());
                    exportGroups.put(exportGroup.getId(), exportGroup);
                }
            }
        }

        render(itls, exportGroups, exportGroupItlMap);
    }

    @FlashException(referrer = { "snapshotDetails" })
    public static void deleteSnapshot(String snapshotId) {
        if (StringUtils.isNotBlank(snapshotId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();

            Tasks<BlockSnapshotRestRep> task = client.blockSnapshots().deactivate(uri(snapshotId), VolumeDeleteTypeEnum.FULL);
            flash.put("info", MessagesUtils.get("resources.snapshot.deactivate", snapshotId));
        }
        snapshotDetails(snapshotId);
    }

    @FlashException(value = "snapshots")
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        if (ids != null) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            for (URI id : ids) {
                Tasks<BlockSnapshotRestRep> task = client.blockSnapshots().deactivate(id, VolumeDeleteTypeEnum.FULL);
            }
            flash.put("info", MessagesUtils.get("resources.snapshots.deactivate", ids.size()));
        }
        snapshots(null);
    }
}
