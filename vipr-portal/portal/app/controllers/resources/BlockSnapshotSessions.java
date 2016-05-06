/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.SnapshotSessionRelinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetsParam;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.util.FlashException;
import models.datatable.BlockSnapshotSessionsDataTable;
import models.datatable.BlockSnapshotsDataTable;
import play.data.binding.As;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
public class BlockSnapshotSessions extends ResourceController {
    private static final String UNKNOWN = "resources.snapshot.session.unknown";
    private static final String INVALID_SESSION = "resources.volumes.error";

    private static BlockSnapshotSessionsDataTable blockSnapshotsDataTable = new BlockSnapshotSessionsDataTable();

    public static void snapshotSessions(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", blockSnapshotsDataTable);
        addReferenceData();
        render();
    }

    public static void snapshotSessionsJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        } else {
            projectId = getActiveProjectId();
        }
        List<BlockSnapshotSessionsDataTable.BlockSnapshotSession> blockSnapshots = BlockSnapshotSessionsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(blockSnapshots, params));
    }

    public static void snapshotSessionDetails(String snapshotSessionId) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        AffectedResources.BlockSnapshotSessionDetails blockSnapshotSession = new AffectedResources.BlockSnapshotSessionDetails(
                uri(snapshotSessionId));
        if (blockSnapshotSession.blockSnapshotSession == null) {
            flash.error(MessagesUtils.get(UNKNOWN, snapshotSessionId));
            snapshotSessions(null);
        }

        if (blockSnapshotSession.volume != null) {
            AffectedResources.VolumeDetails volume = new AffectedResources.VolumeDetails(blockSnapshotSession.volume.getId());
            renderArgs.put("volume", volume);
        }

        List<Task<BlockSnapshotSessionRestRep>> tasks = null;
        if (blockSnapshotSession.blockSnapshotSession != null) {
            Tasks<BlockSnapshotSessionRestRep> tasksResponse = client.blockSnapshotSessions().getTasks(
                    blockSnapshotSession.blockSnapshotSession.getId());
            tasks = tasksResponse.getTasks();
        }

        render(blockSnapshotSession, tasks);
    }

    public static void snapshotSessionLinkTarget(String snapshotSessionId) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        List<RelatedResourceRep> targets = client.blockSnapshotSessions().get(uri(snapshotSessionId)).getLinkedTarget();
        List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().getByRefs(targets);
        render(snapshots, snapshotSessionId);
    }

    @FlashException(referrer = { "snapshotSessionDetails" })
    public static void deleteSnapshotSession(String snapshotId) {
        if (StringUtils.isNotBlank(snapshotId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();

            Tasks<BlockSnapshotSessionRestRep> task = client.blockSnapshotSessions().deactivate(uri(snapshotId), VolumeDeleteTypeEnum.FULL);
            flash.put("info", MessagesUtils.get("resources.snapshot.deactivate", snapshotId));
        }
        snapshotSessionDetails(snapshotId);
    }

    @FlashException(value = "snapshotSessions")
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        if (ids != null) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            for (URI id : ids) {
                Tasks<BlockSnapshotSessionRestRep> task = client.blockSnapshotSessions().deactivate(id, VolumeDeleteTypeEnum.FULL);
            }
            flash.put("info", MessagesUtils.get("resources.snapshots.deactivate", ids.size()));
        }
        snapshotSessions(null);
    }

    public static void relinkTarget(String snapshotId, String snapshotSessionId) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        SnapshotSessionRelinkTargetsParam relinkTargetsParam = new SnapshotSessionRelinkTargetsParam();
        relinkTargetsParam.setLinkedTargetIds(uris(snapshotId));
        client.blockSnapshotSessions().relinkTargets(uri(snapshotSessionId), relinkTargetsParam);
        flash.put("info", MessagesUtils.get("resources.snapshot.session.relink.success", snapshotId));
        snapshotSessionDetails(snapshotSessionId);
    }

    public static void unlinkTarget(String snapshotId, String snapshotSessionId, Boolean deleteOption) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        SnapshotSessionUnlinkTargetsParam unlinkTarget = new SnapshotSessionUnlinkTargetsParam();
        List<SnapshotSessionUnlinkTargetParam> unlinkSessions = Lists.newArrayList();
        SnapshotSessionUnlinkTargetParam unlink = new SnapshotSessionUnlinkTargetParam();
        unlink.setDeleteTarget(deleteOption);
        unlink.setId(uri(snapshotId));
        unlinkSessions.add(unlink);
        unlinkTarget.setLinkedTargets(unlinkSessions);
        client.blockSnapshotSessions().unlinkTargets(uri(snapshotSessionId), unlinkTarget);
        flash.put("info", MessagesUtils.get("resources.snapshot.session.unlink.success", snapshotId));
        snapshotSessionDetails(snapshotSessionId);
    }
}
