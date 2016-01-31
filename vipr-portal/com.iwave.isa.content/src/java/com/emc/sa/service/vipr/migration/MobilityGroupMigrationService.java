package com.emc.sa.service.vipr.migration;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.asset.providers.VirtualDataCenterProvider;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.tasks.ChangeBlockVolumeVirtualPoolNoWait;
import com.emc.sa.service.vipr.block.tasks.GetMobilityGroup;
import com.emc.sa.service.vipr.block.tasks.GetMobilityGroupClusters;
import com.emc.sa.service.vipr.block.tasks.GetMobilityGroupHosts;
import com.emc.sa.service.vipr.block.tasks.GetMobilityGroupVolumes;
import com.emc.sa.service.vipr.block.tasks.GetMobilityGroupVolumesByCluster;
import com.emc.sa.service.vipr.block.tasks.GetMobilityGroupVolumesByHost;
import com.emc.sa.service.vipr.block.tasks.GetUnmanagedVolumesByHostOrCluster;
import com.emc.sa.service.vipr.block.tasks.IngestExportedUnmanagedVolumes;
import com.emc.sa.service.vipr.block.tasks.RemoveVolumeFromMobilityGroup;
import com.emc.sa.service.vipr.compute.ComputeUtils;
import com.emc.sa.util.IngestionMethodEnum;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.exceptions.TimeoutException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Service("MobilityGroupMigration")
public class MobilityGroupMigrationService extends ViPRService {

    @Param(ServiceParams.MOBILITY_GROUP)
    private URI mobilityGroupId;

    @Param(ServiceParams.TARGET_VIRTUAL_POOL)
    private URI targetVirtualPool;

    @Param(value = ServiceParams.MOBILITY_GROUP_METHOD)
    private String mobilityGroupMethod;

    @Param(value = ServiceParams.PROJECT, required = false)
    private URI project;

    @Param(value = ServiceParams.VIRTUAL_ARRAY, required = false)
    private URI virtualArray;

    @Param(value = ServiceParams.VIRTUAL_POOL, required = false)
    private URI virtualPool;

    private VolumeGroupRestRep mobilityGroup;

    @Override
    public void precheck() throws Exception {
        mobilityGroup = execute(new GetMobilityGroup(mobilityGroupId));
    }

    @Override
    public void execute() throws Exception {

        if (mobilityGroupMethod != null && mobilityGroupMethod.equalsIgnoreCase(BlockProvider.INGEST_AND_MIGRATE_OPTION_KEY)) {
            // TODO ingest volumes
            ingestVolumes();
        }

        List<Task<VolumeRestRep>> tasks = new ArrayList<>();

        Tasks<VolumeRestRep> migrationTasks = execute(new ChangeBlockVolumeVirtualPoolNoWait(mapVpoolVolumes(), targetVirtualPool));

        tasks.addAll(migrationTasks.getTasks());

        if (tasks.isEmpty()) {
            ExecutionUtils.fail("failTask.mobilityGroupMigration.noVolumesMigrated", new Object[] {}, new Object[] {});
        }

        while (!tasks.isEmpty()) {
            waitAndRefresh(tasks);
            for (Task<VolumeRestRep> successfulTask : ComputeUtils.getSuccessfulTasks(tasks)) {
                URI volumeId = successfulTask.getResourceId();
                addAffectedResource(volumeId);
                tasks.remove(successfulTask);
                if (mobilityGroup.getMigrationGroupBy().equalsIgnoreCase(VolumeGroup.MigrationGroupBy.VOLUMES.name())) {
                    execute(new RemoveVolumeFromMobilityGroup(mobilityGroup.getId(), volumeId));
                }
            }
            for (Task<VolumeRestRep> failedTask : ComputeUtils.getFailedTasks(tasks)) {
                String errorMessage = failedTask.getMessage() == null ? "" : failedTask.getMessage();
                ExecutionUtils.currentContext().logError("computeutils.exportbootvolumes.failure",
                        failedTask.getResource().getName(), errorMessage);
                tasks.remove(failedTask);
            }
        }
    }

    private static <T> void waitAndRefresh(List<Task<T>> tasks) {
        for (Task<T> task : tasks) {
            try {
                task.waitFor();
            } catch (TimeoutException te) {
                // ignore timeout after polling interval
            } catch (Exception e) {
                ExecutionUtils.currentContext().logError("computeutils.task.exception", e.getMessage());
            }
        }
    }

    private void ingestVolumes() {
        // String ingestionMethod = IngestionMethodEnum.FULL.toString();
        List<NamedRelatedResourceRep> hostsOrClusters = Lists.newArrayList();
        if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.HOSTS.name())) {
            hostsOrClusters = execute(new GetMobilityGroupHosts(mobilityGroup.getId()));
        } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.CLUSTERS.name())) {
            hostsOrClusters = execute(new GetMobilityGroupClusters(mobilityGroup.getId()));
        } else {
            // TODO fail
        }

        for (NamedRelatedResourceRep hostOrCluster : hostsOrClusters) {
            int remaining = execute(new GetUnmanagedVolumesByHostOrCluster(
                    hostOrCluster.getId())).size();

            logInfo("ingest.exported.unmanaged.volume.service.remaining", remaining);
        }

        for (NamedRelatedResourceRep hostOrCluster : hostsOrClusters) {

            URI host = BlockStorageUtils.isHost(hostOrCluster.getId()) ? hostOrCluster.getId() : null;
            URI cluster = BlockStorageUtils.isCluster(hostOrCluster.getId()) ? hostOrCluster.getId() : null;

            List<UnManagedVolumeRestRep> volumeIds = execute(new GetUnmanagedVolumesByHostOrCluster(
                    hostOrCluster.getId()));

            List<URI> ingestVolumeIds = Lists.newArrayList();
            for (UnManagedVolumeRestRep unmanaged : volumeIds) {
                if (VirtualDataCenterProvider.matchesVpool(unmanaged, virtualPool)) {
                    ingestVolumeIds.add(unmanaged.getId());
                }
            }

            // logInfo("planning to ingest %s volumes = %s", ingestVolumeIds.size(), ingestVolumeIds);

            int succeed = execute(new IngestExportedUnmanagedVolumes(virtualPool, virtualArray, project,
                    host == null ? null : host,
                    cluster == null ? null : cluster,
                    ingestVolumeIds,
                    IngestionMethodEnum.VIRTUAL_VOLUMES_ONLY.toString()
                    )).getTasks().size();
            logInfo("ingest.exported.unmanaged.volume.service.ingested", succeed);
            logInfo("ingest.exported.unmanaged.volume.service.skipped", volumeIds.size() - succeed);
        }

    }

    private Map<URI, Set<URI>> mapVpoolVolumes() {
        Set<URI> volumes = getVolumes();
        Map<URI, Set<URI>> vpoolVolumes = Maps.newHashMap();

        for (URI volume : volumes) {
            VolumeRestRep vol = getClient().blockVolumes().get(volume);
            if (!vpoolVolumes.containsKey(vol.getVirtualPool().getId())) {
                vpoolVolumes.put(vol.getVirtualPool().getId(), new HashSet<URI>());
            }
            vpoolVolumes.get(vol.getVirtualPool().getId()).add(vol.getId());
        }
        return vpoolVolumes;
    }

    private Set<URI> getVolumes() {
        if (mobilityGroup.getMigrationGroupBy().equalsIgnoreCase(VolumeGroup.MigrationGroupBy.VOLUMES.name())) {
            return execute(new GetMobilityGroupVolumes(Lists.newArrayList(mobilityGroupId)));
        } else if (mobilityGroup.getMigrationGroupBy().equalsIgnoreCase(VolumeGroup.MigrationGroupBy.HOSTS.name())) {
            List<NamedRelatedResourceRep> hosts = execute(new GetMobilityGroupHosts(mobilityGroupId));
            return execute(new GetMobilityGroupVolumesByHost(mobilityGroup, hosts));
        } else if (mobilityGroup.getMigrationGroupBy().equalsIgnoreCase(VolumeGroup.MigrationGroupBy.CLUSTERS.name())) {
            List<NamedRelatedResourceRep> clusters = execute(new GetMobilityGroupClusters(mobilityGroupId));
            return execute(new GetMobilityGroupVolumesByCluster(mobilityGroup, clusters));
        }
        return Sets.newHashSet();
    }
}
