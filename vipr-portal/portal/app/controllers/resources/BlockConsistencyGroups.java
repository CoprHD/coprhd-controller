/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.datatable.BlockVolumesDataTable.Volume;
import models.datatable.ConsistencyGroupsDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupUpdate;
import com.emc.storageos.model.block.BlockConsistencyGroupUpdate.BlockConsistencyGroupVolumeList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.util.FlashException;

@With(Common.class)
public class BlockConsistencyGroups extends ResourceController {
    private static final String UNKNOWN = "resources.consistencygroup.unknown";

    private static ConsistencyGroupsDataTable consistencyGroupsDataTable = new ConsistencyGroupsDataTable();

    public static void consistencyGroups(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", consistencyGroupsDataTable);
        addReferenceData();
        render();
    }

    public static void consistencyGroupsJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        }
        else {
            projectId = getActiveProjectId();
        }
        List<ConsistencyGroupsDataTable.ConsistencyGroup> consistencyGroups = ConsistencyGroupsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(consistencyGroups, params));
    }

    public static void consistencyGroupDetails(String consistencyGroupId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        AffectedResources.BlockConsistencyGroupDetails blockConsistencyGroup =
                new AffectedResources.BlockConsistencyGroupDetails(uri(consistencyGroupId));
        if (blockConsistencyGroup.blockConsistencyGroup == null) {
            flash.error(MessagesUtils.get(UNKNOWN, consistencyGroupId));
            ConsistencyGroups.list();
        }

        Tasks<BlockConsistencyGroupRestRep> tasksResponse = client.blockConsistencyGroups().getTasks(
                blockConsistencyGroup.blockConsistencyGroup.getId());
        List<Task<BlockConsistencyGroupRestRep>> tasks = tasksResponse.getTasks();
        renderArgs.put("tasks", tasks);

        List<VolumeRestRep> volumes = blockConsistencyGroup.volumes;

        Map<URI, String> virtualArrays = ResourceUtils.mapNames(client.varrays().list());
        Map<URI, String> virtualPools = ResourceUtils.mapNames(client.blockVpools().list());

        List<Volume> volumeDetails = Lists.newArrayList();
        for (VolumeRestRep volume : volumes) {
            volumeDetails.add(new Volume(volume, virtualArrays, virtualPools));
        }

        render(blockConsistencyGroup, volumeDetails);
    }

    @FlashException(value = "consistencyGroups")
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        if (ids != null) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            for (URI id : ids) {
                Task<BlockConsistencyGroupRestRep> task = client.blockConsistencyGroups().deactivate(id);
            }
            flash.put("info", MessagesUtils.get("resources.consistencygroups.deactivate", ids.size()));
        }
        consistencyGroups(null);
    }

    @FlashException(referrer = { "consistencyGroupDetails" })
    public static void removeVolume(String consistencyGroupId, String volumeId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        List<URI> uris = new ArrayList<URI>();
        uris.add(uri(volumeId));

        BlockConsistencyGroupUpdate blockConsistencyGroupUpdate = new BlockConsistencyGroupUpdate();
        BlockConsistencyGroupVolumeList volumeList = new BlockConsistencyGroupVolumeList();
        volumeList.setVolumes(uris);

        blockConsistencyGroupUpdate.setRemoveVolumesList(volumeList);

        Task<BlockConsistencyGroupRestRep> task =
                client.blockConsistencyGroups().update(uri(consistencyGroupId), blockConsistencyGroupUpdate);
        flash.put("info", MessagesUtils.get("resources.consistencygroup.volume.removed", task.getOpId()));

        consistencyGroupDetails(consistencyGroupId);
    }

    @FlashException(referrer = { "consistencyGroupDetails" })
    public static void deleteConsistencyGroup(String consistencyGroupId) {
        if (StringUtils.isNotBlank(consistencyGroupId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();

            Task<BlockConsistencyGroupRestRep> task = client.blockConsistencyGroups().deactivate(uri(consistencyGroupId));
            flash.put("info", MessagesUtils.get("resources.consistencygroup.deactivate"));
        }
        consistencyGroupDetails(consistencyGroupId);
    }
}
