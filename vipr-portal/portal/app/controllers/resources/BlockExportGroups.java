/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.export.ClustersUpdateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.HostsUpdateParam;
import com.emc.storageos.model.block.export.InitiatorsUpdateParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.DefaultResourceFilter;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.util.FlashException;
import models.datatable.BlockExportGroupSnapshotsDataTable;
import models.datatable.BlockExportGroupVolumesDataTable;
import models.datatable.BlockExportGroupsDataTable;
import models.datatable.HostClusterDataTable;
import models.datatable.NetworkEndpointDataTable;
import models.datatable.NetworkEndpointDataTable.EndpointInfo;
import models.datatable.SimpleHostDataTable;
import play.data.binding.As;
import play.mvc.With;
import util.BourneUtil;
import util.ClusterUtils;
import util.HostUtils;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

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

    @FlashException(referrer = { "exportGroup" })
    public static void addHosts(String exportId, @As(",") String[] ids) {
        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();
        exportUpdateParam.setHosts(new HostsUpdateParam());

        if (ids != null && ids.length > 0) {
            for (String hostId : ids) {
                exportUpdateParam.getHosts().getAdd().add(uri(hostId));
            }
        }

        Task<ExportGroupRestRep> task = getViprClient().blockExports().update(uri(exportId), exportUpdateParam);
        flash.put("info", MessagesUtils.get("resources.exportgroup.hosts.added", task.getOpId()));

        exportGroup(exportId);
    }

    @FlashException(referrer = { "exportGroup" })
    public static void addClusters(String exportId, @As(",") String[] ids) {
        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();
        exportUpdateParam.setClusters(new ClustersUpdateParam());

        if (ids != null && ids.length > 0) {
            for (String clusterId : ids) {
                exportUpdateParam.getClusters().getAdd().add(uri(clusterId));
            }
        }

        Task<ExportGroupRestRep> task = getViprClient().blockExports().update(uri(exportId), exportUpdateParam);
        flash.put("info", MessagesUtils.get("resources.exportgroup.clusters.added", task.getOpId()));

        exportGroup(exportId);
    }

    @FlashException(referrer = { "exportGroup" })
    public static void addInitiators(String exportId, @As(",") String[] ids) {
        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();
        exportUpdateParam.setInitiators(new InitiatorsUpdateParam());

        if (ids != null && ids.length > 0) {
            for (String initiatorId : ids) {
                exportUpdateParam.getInitiators().getAdd().add(uri(initiatorId));
            }
        }

        Task<ExportGroupRestRep> task = getViprClient().blockExports().update(uri(exportId), exportUpdateParam);
        flash.put("info", MessagesUtils.get("resources.exportgroup.initiators.added", task.getOpId()));

        exportGroup(exportId);
    }

    public static void exportGroupsJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        } else {
            projectId = getActiveProjectId();
        }
        List<BlockExportGroupsDataTable.ExportGroup> exportGroups = BlockExportGroupsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(exportGroups, params));
    }

    /**
     * Return list of initiators that can be added to this export group
     * 
     * @param exportGroupId id of the export group
     * @return list of initiators
     */
    public static List<InitiatorRestRep> getEligibleInitiators(URI exportGroupId) {

        if (exportGroupId == null) {
            return Lists.newArrayList();
        }

        ExportGroupRestRep exportGroup = getViprClient().blockExports().get(exportGroupId);
        final List<URI> initiatorsInExport = ResourceUtils.ids(exportGroup.getInitiators());
        List<URI> allInitiatorIds = getViprClient().initiators().listBulkIds();

        final List<URI> validHosts = getValidHostsForInitiatorExport(exportGroup);

        return getViprClient().initiators().getByIds(allInitiatorIds, new DefaultResourceFilter<InitiatorRestRep>() {
            @Override
            public boolean accept(InitiatorRestRep item) {
                return !initiatorsInExport.contains(item.getId())
                        && (validHosts.isEmpty() || (item.getHost() != null && !NullColumnValueGetter.isNullURI(item.getHost().getId())
                                && validHosts.contains(item.getHost().getId())));
            }
        });
    }

    /**
     * Returns a list of valid hosts that can be added to the provided exportGroup based on the type of export
     * 
     * @param exportGroup export group
     * @return list of valid hosts that can be exported to this export group
     */
    public static List<URI> getValidHostsForInitiatorExport(ExportGroupRestRep exportGroup) {
        if (exportGroup.getType().equals(ExportGroupType.Cluster.name())
                || exportGroup.getType().equals(ExportGroupType.Host.name())) {
            return ResourceUtils.ids(exportGroup.getHosts());
        } else if (exportGroup.getType().equals(ExportGroupType.Exclusive.name())
                || exportGroup.getType().equals(ExportGroupType.Initiator.name())) {

            List<URI> validHosts = Lists.newArrayList();
            for (InitiatorRestRep initiator : exportGroup.getInitiators()) {
                if (initiator.getHost() != null && !NullColumnValueGetter.isNullURI(initiator.getHost().getId())) {
                    validHosts.add(initiator.getHost().getId());
                }
            }
            return validHosts;
        }

        return Lists.newArrayList();
    }

    public static void availableClustersJson(String exportGroupId) {
        List<ClusterRestRep> availableClusters = Lists.newArrayList();

        ExportGroupRestRep exportGroup = getViprClient().blockExports().get(uri(exportGroupId));

        List<URI> allClusterIds = getViprClient().clusters().listBulkIds();
        final List<URI> exportGroupClusters = ResourceUtils.ids(exportGroup.getClusters());

        availableClusters = getViprClient().clusters().getByIds(allClusterIds, new DefaultResourceFilter<ClusterRestRep>() {
            @Override
            public boolean accept(ClusterRestRep item) {
                return !exportGroupClusters.contains(item.getId());
            }
        });

        renderJSON(DataTablesSupport.createJSON(availableClusters, params));
    }

    public static void availableInitiatorsJson(String id) {
        CachedResources<HostRestRep> hosts = HostUtils.createCache();

        List<EndpointInfo> items = Lists.newArrayList();
        for (InitiatorRestRep initiator : getEligibleInitiators(uri(id))) {
            items.add(new EndpointInfo(initiator, hosts));
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    /**
     * Return list of valid hosts that can be added to the given export group
     * 
     * @param exportGroup export group
     * @return list of hosts that can be exported
     */
    public static List<URI> getValidHosts(ExportGroupRestRep exportGroup) {
        if (exportGroup.getType().equals(ExportGroup.ExportGroupType.Cluster.name())) {
            List<URI> hostIds = Lists.newArrayList();
            for (ClusterRestRep cluster : exportGroup.getClusters()) {
                hostIds.addAll(ResourceUtils.ids(ClusterUtils.getHosts(cluster.getId())));
            }
            return hostIds;
        } else if (exportGroup.getType().equals(ExportGroup.ExportGroupType.Host.name())) {
            return getViprClient().hosts().listBulkIds();
        }
        return Lists.newArrayList();
    }

    public static void availableHostsJson(String exportGroupId) {
        List<HostRestRep> availableHosts = Lists.newArrayList();

        ExportGroupRestRep exportGroup = getViprClient().blockExports().get(uri(exportGroupId));

        final List<URI> exportGroupHosts = ResourceUtils.ids(exportGroup.getHosts());
        List<URI> hostIds = getValidHosts(exportGroup);

        if (!hostIds.isEmpty()) {
            availableHosts = getViprClient().hosts().getByIds(hostIds, new DefaultResourceFilter<HostRestRep>() {
                @Override
                public boolean accept(HostRestRep item) {
                    return !exportGroupHosts.contains(item.getId());
                }
            });
        }

        renderJSON(DataTablesSupport.createJSON(availableHosts, params));
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
        } else {
            notFound("Export Group " + exportGroupId);
        }
        renderArgs.put("volumeDataTable", new BlockExportGroupVolumesDataTable());
        renderArgs.put("snapshotDataTable", new BlockExportGroupSnapshotsDataTable());

        SimpleHostDataTable hostsDataTable = new SimpleHostDataTable();

        NetworkEndpointDataTable initiatorsDataTable = NetworkEndpointDataTable.createDataTable("FC");
        initiatorsDataTable.alterColumn("portGroup").hidden().setSearchable(false);
        initiatorsDataTable.alterColumn("storageSystem").hidden().setSearchable(false);
        initiatorsDataTable.alterColumn("discovered").hidden().setSearchable(false);

        HostClusterDataTable clustersDataTable = new HostClusterDataTable();
        render(hostsDataTable, initiatorsDataTable, clustersDataTable, exportGroup, virtualArray);
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
