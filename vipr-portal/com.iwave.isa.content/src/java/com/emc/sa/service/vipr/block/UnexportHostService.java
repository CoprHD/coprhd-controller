/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Sets;

@Service("UnexportHost")
public class UnexportHostService extends ViPRService {

    @Param(HOST)
    protected URI hostId;

    @Param(VOLUMES)
    protected List<String> volumeIds;

    protected Host host;
    protected List<? extends BlockObjectRestRep> volumes;
    protected List<ExportGroupRestRep> exports;

    @Override
    public void precheck() {
        // check to select max 100 resources at a time.
        checkForMaxResouceOrderLimit(volumeIds);
        host = BlockStorageUtils.getHost(hostId);
        String hostName = host.getLabel();

        exports = BlockStorageUtils.findExportsContainingHost(hostId, null, null);
        if (exports.isEmpty()) {
            ExecutionUtils.fail("failTask.UnexportHostService.export", args(), args(hostName));
        }
        volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
        if (volumes.isEmpty()) {
            ExecutionUtils.fail("failTask.UnexportHostService.volumes", args(), args());
        }
        if (volumes.size() < volumeIds.size()) {
            logWarn("unexport.host.service.not.found", volumeIds.size(), volumes.size());
        }
        for (ExportGroupRestRep export : exports) {
            for (BlockObjectRestRep volume : volumes) {
                URI volumeId = ResourceUtils.id(volume);
                if (BlockStorageUtils.isVolumeInExportGroup(export, volumeId)
                        && StringUtils.equalsIgnoreCase(export.getType(), ExportGroupType.Cluster.name())) {
                    ExecutionUtils.fail("failTask.UnexportHostService.clusterExport", args(), args(export.getName()));
                }
            }
        }
        checkForBootVolumes(volumeIds);
    }

    @Override
    public void execute() throws Exception {
        for (ExportGroupRestRep export : exports) {
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
            } else {
                logDebug("unexport.host.service.volume.skip", exportName);
            }

            String hostOrClusterId = BlockStorageUtils.getHostOrClusterId(hostId);
            if (hostOrClusterId != null) {
                ExecutionUtils.addAffectedResource(hostOrClusterId.toString());
            }
        }
    }
}
