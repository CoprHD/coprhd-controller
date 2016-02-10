/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.sa.util.ResourceType.BLOCK_CONTINUOUS_COPY;
import static com.emc.sa.util.ResourceType.VOLUME;
import static com.emc.sa.util.ResourceType.VPLEX_CONTINUOUS_COPY;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.datatable.BlockVolumesDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.i18n.Messages;
import play.mvc.Util;
import play.mvc.With;
import util.AppSupportUtil;
import util.BlockConsistencyGroupUtils;
import util.BourneUtil;
import util.MessagesUtils;
import util.StorageSystemUtils;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetsParam;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.util.FlashException;

@With(Common.class)
public class BlockVolumes extends ResourceController {

    public static final String COPY_NATIVE = "native";
    public static final String COPY_RP = "rp";
    public static final String COPY_SRDF = "srdf";

    private static final String UNKNOWN = "resources.volumes.unknown";
    private static final String NOTARGET = "resources.snapshot.session.targets.none";

    private static BlockVolumesDataTable blockVolumesDataTable = new BlockVolumesDataTable();

    public static void volumes(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", blockVolumesDataTable);
        renderArgs.put("application", getApplications());
        addReferenceData();
        render();
    }

    public static void volumesJson(String projectId, String applicationId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        } else {
            projectId = getActiveProjectId();
        }
        List<BlockVolumesDataTable.Volume> volumes = BlockVolumesDataTable.fetch(uri(projectId), uri(applicationId));
        renderJSON(DataTablesSupport.createJSON(volumes, params));
    }

    public static void volumeDetails(String volumeId) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        String consistencygroup = "";
        VolumeRestRep volume = client.blockVolumes().get(uri(volumeId));
        if (volume == null) {
            error(MessagesUtils.get(UNKNOWN, volumeId));
        }
        if (volume.getConsistencyGroup() != null) {
            consistencygroup = client.blockConsistencyGroups().get(volume.getConsistencyGroup().getId()).getName();
        }
        render(consistencygroup);
    }

    public static void volume(String volumeId, String continuousCopyId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        VolumeRestRep volume = null;
        if (isVolumeId(volumeId)) {
            if (isContinuousCopyId(continuousCopyId)) {
                volume = client.blockVolumes().getContinuousCopy(uri(volumeId), uri(continuousCopyId));
                renderArgs.put("isContinuousCopy", Boolean.TRUE);
                renderArgs.put("isBlockContinuousCopy", isBlockContinuousCopyId(continuousCopyId));
                renderArgs.put("isVplexContinuousCopy", isVplexContinuousCopyId(continuousCopyId));
            } else {
                try {
                    volume = client.blockVolumes().get(uri(volumeId));
                } catch (ViPRHttpException e) {
                    if (e.getHttpCode() == 404) {
                        flash.error(MessagesUtils.get(UNKNOWN, volumeId));
                        volumes(null);
                    }
                    throw e;
                }
            }
        }

        if (volume == null) {
            notFound(Messages.get("resources.volume.notfound"));
        }

        if (volume.getVirtualArray() != null) { // NOSONAR
                                                // ("Suppressing Sonar violation of Possible null pointer dereference of volume. When volume is null, the previous if condition handles with throw")
            renderArgs.put("virtualArray", VirtualArrayUtils.getVirtualArrayRef(volume.getVirtualArray()));
        }
        if (volume.getVirtualPool() != null) {
            renderArgs.put("virtualPool", VirtualPoolUtils.getBlockVirtualPoolRef(volume.getVirtualPool()));
        }
        if (volume.getConsistencyGroup() != null) {
            renderArgs.put("consistencyGroup",
                    BlockConsistencyGroupUtils.getBlockConsistencyGroupRef(volume.getConsistencyGroup()));
        }
        if (volume.getStorageController() != null) {
            renderArgs.put("storageSystem", StorageSystemUtils.getStorageSystemRef(volume.getStorageController()));
        }
        if (volume.getAccessState() == null || volume.getAccessState().isEmpty()) {
            renderArgs.put("isAccessStateEmpty", "true");
        }

        Tasks<VolumeRestRep> tasksResponse = client.blockVolumes().getTasks(volume.getId());
        List<Task<VolumeRestRep>> tasks = tasksResponse.getTasks();
        renderArgs.put("tasks", tasks);

        render(volume);
    }

    public static void volumeExports(String volumeId) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        Map<URI, ExportGroupRestRep> exportGroups = Maps.newHashMap();
        Map<URI, List<ITLRestRep>> exportGroupItlMap = Maps.newHashMap();
        List<ITLRestRep> itls = Lists.newArrayList();

        VolumeRestRep volume = client.blockVolumes().get(uri(volumeId));
        if (volume != null && volume.getInactive() == false) {
            itls = client.blockVolumes().getExports(uri(volumeId));
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
        }

        // Remove 'internal' marked export groups, we don't want to show these in the exports list
        Set<URI> internalExportGroups = Sets.newHashSet();
        for (ExportGroupRestRep exportGroup : exportGroups.values()) {
            if (Boolean.TRUE.equals(exportGroup.getInternal())) {
                internalExportGroups.add(exportGroup.getId());
            }
        }
        // Remove internal export groups
        exportGroups.keySet().removeAll(internalExportGroups);
        // Remove initiators from interal export groups
        exportGroupItlMap.keySet().removeAll(internalExportGroups);

        render(itls, exportGroups, exportGroupItlMap);
    }

    public static void volumeSnapshots(String volumeId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        List<NamedRelatedResourceRep> refs = client.blockSnapshots().listByVolume(uri(volumeId));

        List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().getByRefs(refs);

        render(snapshots);
    }
    
    public static void volumeSnapshotSessions(String volumeId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        List<NamedRelatedResourceRep> refs = client.blockSnapshotSessions().listByVolume(uri(volumeId));
        
        List<BlockSnapshotSessionRestRep> snapshotSessions = client.blockSnapshotSessions().getByRefs(refs);

        render(snapshotSessions, volumeId);
    }

    public static void unlinkTargetSnapshot(String sessionId, String volumeId, Boolean deleteOption) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        SnapshotSessionUnlinkTargetsParam sessionTargets = new SnapshotSessionUnlinkTargetsParam();

        SnapshotSessionUnlinkTargetParam targetList = new SnapshotSessionUnlinkTargetParam();

        List<SnapshotSessionUnlinkTargetParam> targetLists = Lists.newArrayList();

        List<RelatedResourceRep> targets = client.blockSnapshotSessions().get(uri(sessionId)).getLinkedTarget();

        List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().getByRefs(targets);

        for (BlockSnapshotRestRep snap : snapshots) {

            targetList.setId(snap.getId());

            targetList.setDeleteTarget(deleteOption);

            targetLists.add(targetList);
        }

        if (!targetLists.isEmpty()) {

            sessionTargets.setLinkedTargets(targetLists);

            Task<BlockSnapshotSessionRestRep> tasks = client.blockSnapshotSessions().unlinkTargets(uri(sessionId), sessionTargets);

            flash.put("info", MessagesUtils.get("resources.snapshot.session.unlink.success", sessionId));
        }
        else {
            flash.error(MessagesUtils.get(NOTARGET, sessionId));
        }
        volume(volumeId, null);
    }

    public static void volumeContinuousCopies(String volumeId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        List<BlockMirrorRestRep> continuousCopies = client.blockVolumes().getContinuousCopies(uri(volumeId));

        render(continuousCopies);
    }

    public static void volumeFullCopies(String volumeId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        List<NamedRelatedResourceRep> refs = client.blockVolumes().listFullCopies(uri(volumeId));

        List<VolumeRestRep> fullCopies = client.blockVolumes().getByRefs(refs);

        render(fullCopies);
    }

    public static void volumeMigrations(String volumeId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        List<NamedRelatedResourceRep> migrations = client.blockVolumes().listMigrations(uri(volumeId));

        render(migrations);
    }

    @FlashException("volumes")
    public static void delete(@As(",") String[] ids, VolumeDeleteTypeEnum type) {
        delete(uris(ids), type);
    }

    @FlashException(referrer = { "volume" })
    public static void deleteVolume(String volumeId, VolumeDeleteTypeEnum type) {
        if (StringUtils.isNotBlank(volumeId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();

            Task<VolumeRestRep> task = client.blockVolumes().deactivate(uri(volumeId), type);
            flash.put("info", MessagesUtils.get("resources.volume.deactivate"));
        }
        volume(volumeId, null);
    }

    @FlashException(referrer = { "volume" })
    public static void pauseContinuousCopy(String volumeId, String continuousCopyId) {
        if (StringUtils.isNotBlank(volumeId) && StringUtils.isNotBlank(continuousCopyId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            CopiesParam input = createCopiesParam(continuousCopyId);
            Tasks<VolumeRestRep> tasks = client.blockVolumes().pauseContinuousCopies(uri(volumeId), input);
            flash.put("info", MessagesUtils.get("resources.continuouscopy.pause"));
        }
        volume(volumeId, null);
    }

    @FlashException(referrer = { "volume" })
    public static void resumeContinuousCopy(String volumeId, String continuousCopyId) {
        if (StringUtils.isNotBlank(volumeId) && StringUtils.isNotBlank(continuousCopyId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            CopiesParam input = createCopiesParam(continuousCopyId);
            Tasks<VolumeRestRep> tasks = client.blockVolumes().resumeContinuousCopies(uri(volumeId), input);
            flash.put("info", MessagesUtils.get("resources.continuouscopy.resume"));
        }
        volume(volumeId, null);
    }

    @FlashException(referrer = { "volume" })
    public static void stopContinuousCopy(String volumeId, String continuousCopyId) {
        if (StringUtils.isNotBlank(volumeId) && StringUtils.isNotBlank(continuousCopyId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            CopiesParam input = createCopiesParam(continuousCopyId);
            Tasks<VolumeRestRep> tasks = client.blockVolumes().stopContinuousCopies(uri(volumeId), input);
            flash.put("info", MessagesUtils.get("resources.continuouscopy.stop"));

        }
        volume(volumeId, null);
    }

    @FlashException(referrer = { "volume" })
    public static void deleteContinuousCopy(String volumeId, String continuousCopyId) {
        if (StringUtils.isNotBlank(volumeId) && StringUtils.isNotBlank(continuousCopyId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            CopiesParam input = createCopiesParam(continuousCopyId);
            Tasks<VolumeRestRep> tasks = client.blockVolumes().deactivateContinuousCopies(uri(volumeId), input, VolumeDeleteTypeEnum.FULL);
            flash.put("info", MessagesUtils.get("resources.continuouscopy.deactivate"));
        }
        volume(volumeId, continuousCopyId);
    }

    @Util
    private static CopiesParam createCopiesParam(String continuousCopyId) {
        Copy copy = new Copy();
        copy.setType(COPY_NATIVE);
        copy.setCopyID(uri(continuousCopyId));
        List<Copy> copies = Lists.newArrayList();
        copies.add(copy);
        CopiesParam input = new CopiesParam(copies);
        return input;
    }

    private static void delete(List<URI> ids, VolumeDeleteTypeEnum type) {
        if (ids != null) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            Tasks<VolumeRestRep> tasks = client.blockVolumes().deactivate(ids, type);
            flash.put("info", MessagesUtils.get("resources.volumes.deactivate", tasks.getTasks().size()));
        }
        volumes(null);
    }

    @Util
    private static boolean isVolumeId(String id) {
        return ResourceType.isType(VOLUME, id);
    }

    @Util
    private static boolean isContinuousCopyId(String id) {
        return isBlockContinuousCopyId(id) || isVplexContinuousCopyId(id);
    }

    @Util
    private static boolean isVplexContinuousCopyId(String id) {
        return ResourceType.isType(VPLEX_CONTINUOUS_COPY, id);
    }

    @Util
    private static boolean isBlockContinuousCopyId(String id) {
        return ResourceType.isType(BLOCK_CONTINUOUS_COPY, id);
    }

    @Util
    private static List<NamedRelatedResourceRep> getApplications() {
        List<NamedRelatedResourceRep> application = AppSupportUtil.getApplications();
        if(!application.isEmpty()) {
        Collections.sort(application, new Comparator<NamedRelatedResourceRep>() {
            @Override
            public int compare(NamedRelatedResourceRep app1, NamedRelatedResourceRep app2)
            {
                return app1.getName().compareTo(app2.getName());
            }
        });
        
    }
        return application;
    }
}
