/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.vipr.client.exceptions.ViPRHttpException;
import models.datatable.BlockExportGroupSnapshotsDataTable;
import models.datatable.BlockExportGroupVolumesDataTable;
import models.datatable.BlockExportGroupsDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.block.export.ClustersUpdateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.HostsUpdateParam;
import com.emc.storageos.model.block.export.InitiatorsUpdateParam;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.exceptions.ViPRException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.util.FlashException;

@With(Common.class)
public class BlockExportGroups extends ResourceController {

    private static final String UNKNOWN = "resources.exportgroup.unknown";

    private static BlockExportGroupsDataTable blockExportGroupsDataTable = new BlockExportGroupsDataTable();

    public static void exportGroups(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", blockExportGroupsDataTable);
        addReferenceData();
        render();
    }

    public static void exportGroupsJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        }
        else {
            projectId = getActiveProjectId();
        }
        List<BlockExportGroupsDataTable.ExportGroup> exportGroups = BlockExportGroupsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(exportGroups, params));
    }

    public static void exportGroup(String exportGroupId) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        ExportGroupRestRep exportGroup = null;
        try {
            exportGroup = client.blockExports().get(uri(exportGroupId));
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                flash.error(MessagesUtils.get(UNKNOWN, exportGroupId));
                exportGroups(null);
            }
            throw e;
        }

        VirtualArrayRestRep virtualArray = null;
        if (exportGroup != null) {
            virtualArray = client.varrays().get(exportGroup.getVirtualArray());
        }
        else {
            notFound("Export Group " + exportGroupId);
        }
        renderArgs.put("volumeDataTable", new BlockExportGroupVolumesDataTable());
        renderArgs.put("snapshotDataTable", new BlockExportGroupSnapshotsDataTable());
        render(exportGroup, virtualArray);
    }

    public static void exportGroupVolumesJson(String exportGroupId) {
        renderJSON(exportGroupId);
    }

    public static void blockSnapshotJson(String exportGroupId) {
        List<BlockExportGroupSnapshotsDataTable.ExportBlockSnapshot> blockSnapshots = BlockExportGroupSnapshotsDataTable
                .fetch(uri(exportGroupId));
        renderJSON(DataTablesSupport.createJSON(blockSnapshots, params));
    }

    public static void volumeJson(String exportGroupId) {
        List<BlockExportGroupVolumesDataTable.Volume> volumes = BlockExportGroupVolumesDataTable.fetch(uri(exportGroupId));
        renderJSON(DataTablesSupport.createJSON(volumes, params));
    }

    @FlashException(referrer = { "exportGroup" })
    public static void deleteExportGroup(String exportGroupId) {
        if (StringUtils.isNotBlank(exportGroupId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();

            Task<ExportGroupRestRep> task = client.blockExports().deactivate(uri(exportGroupId));
            flash.put("info", MessagesUtils.get("resources.exportgroup.deactivate"));
        }
        exportGroup(exportGroupId);
    }

    @FlashException(value = "exportGroups")
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        if (ids != null) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            List<Task<ExportGroupRestRep>> tasks = Lists.newArrayList();
            for (URI id : ids) {
                Task<ExportGroupRestRep> task = client.blockExports().deactivate(id);
                tasks.add(task);
            }
            flash.put("info", MessagesUtils.get("resources.exportgroups.deactivate", tasks.size()));
        }
        exportGroups(null);
    }

    @FlashException(referrer = { "exportGroup" })
    public static void removeHost(String exportGroupId, String hostId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();
        exportUpdateParam.setHosts(new HostsUpdateParam());
        exportUpdateParam.getHosts().getRemove().add(uri(hostId));

        Task<ExportGroupRestRep> task = client.blockExports().update(uri(exportGroupId), exportUpdateParam);
        flash.put("info", MessagesUtils.get("resources.exportgroup.host.removed", task.getOpId()));

        exportGroup(exportGroupId);
    }

    @FlashException(referrer = { "exportGroup" })
    public static void removeCluster(String exportGroupId, String clusterId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();
        exportUpdateParam.setClusters(new ClustersUpdateParam());
        exportUpdateParam.getClusters().getRemove().add(uri(clusterId));

        Task<ExportGroupRestRep> task = client.blockExports().update(uri(exportGroupId), exportUpdateParam);
        flash.put("info", MessagesUtils.get("resources.exportgroup.cluster.removed", task.getOpId()));

        exportGroup(exportGroupId);
    }

    @FlashException(referrer = { "exportGroup" })
    public static void removeInitiator(String exportGroupId, String initiatorId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();
        exportUpdateParam.setInitiators(new InitiatorsUpdateParam());
        Set<URI> remove = Sets.newHashSet();
        remove.add(uri(initiatorId));
        exportUpdateParam.getInitiators().setRemove(remove);

        Task<ExportGroupRestRep> task = client.blockExports().update(uri(exportGroupId), exportUpdateParam);
        flash.put("info", MessagesUtils.get("resources.exportgroup.initiator.removed", task.getOpId()));

        exportGroup(exportGroupId);
    }

    @FlashException(referrer = { "exportGroup" })
    public static void removeVolume(String exportGroupId, String volumeId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();
        exportUpdateParam.removeVolume(uri(volumeId));

        Task<ExportGroupRestRep> task = client.blockExports().update(uri(exportGroupId), exportUpdateParam);
        flash.put("info", MessagesUtils.get("resources.exportgroup.volume.removed", task.getOpId()));

        exportGroup(exportGroupId);
    }

    @FlashException(referrer = { "exportGroup" })
    public static void removeSnapshot(String exportGroupId, String snapshotId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();
        exportUpdateParam.removeVolume(uri(snapshotId));

        Task<ExportGroupRestRep> task = client.blockExports().update(uri(exportGroupId), exportUpdateParam);
        flash.put("info", MessagesUtils.get("resources.exportgroup.snapshot.removed", task.getOpId()));

        exportGroup(exportGroupId);
    }
}
