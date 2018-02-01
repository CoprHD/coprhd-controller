/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.machinetags.vmware.VMwareDatastoreTagger;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.sa.service.vmware.tasks.ConnectToVCenter;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-UnexportVolume")
public class UnexportVMwareVolumeService extends VMwareHostService {

    @Param(HOST)
    protected URI hostId;

    @Param(VOLUMES)
    protected List<String> volumeIds;

    protected Cluster clusterInstance;
    protected Host hostInstance;
    protected List<? extends BlockObjectRestRep> volumes;
    protected List<ExportGroupRestRep> exports;
    Collection<ExportGroupRestRep> filteredExportGroups;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        List<Host> hosts = Lists.newArrayList();

        if (BlockStorageUtils.isCluster(hostId)) {
            clusterInstance = BlockStorageUtils.getCluster(hostId);
            exports = BlockStorageUtils.findExportsContainingCluster(hostId, null, null);
            hosts = getModelClient().hosts().findByCluster(hostId);
        } else {
            hostInstance = BlockStorageUtils.getHost(hostId);
            exports = BlockStorageUtils.findExportsContainingHost(hostId, null, null);
            hosts = Arrays.asList(hostInstance);
        }

        filteredExportGroups = BlockStorageUtils.filterExportsByType(exports, hostId);

        String hostOrClusterLabel = clusterInstance == null ? hostInstance.getLabel() : clusterInstance.getLabel();

        if (filteredExportGroups.isEmpty()) {
            ExecutionUtils.fail("failTask.UnexportHostService.export", args(), args(hostOrClusterLabel));
        }
        volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
        if (volumes.isEmpty()) {
            ExecutionUtils.fail("failTask.UnexportHostService.volumes", args(), args());
        }
        if (volumes.size() < volumeIds.size()) {
            logWarn("unexport.host.service.not.found", volumeIds.size(), volumes.size());
        }
        for (BlockObjectRestRep volume : volumes) {
            String datastoreName = KnownMachineTags.getBlockVolumeVMFSDatastore(hostId, volume);
            if (!StringUtils.isEmpty(datastoreName)) {
                Datastore datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
                if (datastore != null) {
                    vmware.verifyDatastoreForRemoval(datastore, datacenter.getLabel(), hosts);
                }
            }
        }
    }

    @Override
    public void execute() throws Exception {

        for (BlockObjectRestRep volume : volumes) {
            String datastoreName = KnownMachineTags.getBlockVolumeVMFSDatastore(hostId, volume);
            if (!StringUtils.isEmpty(datastoreName)) {
                Datastore datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
                if (datastore != null) {
                    boolean storageIOControlEnabled = datastore.getIormConfiguration() != null
                            ? datastore.getIormConfiguration().isEnabled() : false;
                    vmware.unmountVmfsDatastore(host, cluster, datastore);
                    datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
                    if (storageIOControlEnabled && datastore != null && datastore.getSummary() != null
                            && datastore.getSummary().isAccessible()) {
                        vmware.setStorageIOControl(datastore, true);
                    }
                }
            }
        }

        for (BlockObjectRestRep volume : volumes) {
            vmware.detachLuns(host, cluster, volume);
        }

        vmware.disconnect();
        ExecutionUtils.clearRollback();

        for (BlockObjectRestRep volume : volumes) {
            if (volume.getTags() != null) {
                vmware.removeVmfsDatastoreTag(volume, hostId);
            }
        }

        for (ExportGroupRestRep export : filteredExportGroups) {
            URI exportId = ResourceUtils.id(export);
            String exportName = ResourceUtils.name(export);

            // Check each volume to see if it is in this export
            Set<URI> exportedVolumeIds = Sets.newHashSet();
            for (BlockObjectRestRep volume : volumes) {
                URI volumeId = ResourceUtils.id(volume);
                String volumeName = ResourceUtils.name(volume);
                if (BlockStorageUtils.isVolumeInExportGroup(export, volumeId)) {
                    logInfo("unexport.host.service.volume.in.export", volumeName, exportName);
                    exportedVolumeIds.add(volumeId);
                }
            }

            if (!exportedVolumeIds.isEmpty()) {
                logInfo("unexport.host.service.volume.remove", exportedVolumeIds.size(), exportName);
                BlockStorageUtils.removeBlockResourcesFromExport(exportedVolumeIds, exportId);
            }
            else {
                logDebug("unexport.host.service.volume.skip", exportName);
            }

            String hostOrClusterId = BlockStorageUtils.getHostOrClusterId(hostId);
            if (hostOrClusterId != null) {
                ExecutionUtils.addAffectedResource(hostOrClusterId.toString());
            }
        }

        connectAndInitializeHost();
        vmware.refreshStorage(host, cluster);
    }
}
