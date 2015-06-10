/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.NUMBER_OF_VOLUMES;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;

public class CreateBlockVolumeHelper {
    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(PROJECT)
    protected URI project;

    @Param(NAME)
    protected String nameParam;

    @Param(HOST)
    protected URI hostId;

    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    @Param(value = NUMBER_OF_VOLUMES, required = false)
    protected Integer count;

    @Param(value=CONSISTENCY_GROUP, required = false)
    protected URI consistencyGroup;

    @Param(value = HLU, required = false)
    protected Integer hlu;

    private Host host;
    private Cluster cluster;

    public void precheck() {
        if (BlockStorageUtils.isHost(hostId)) {
            host = BlockStorageUtils.getHost(hostId);
        }
        else {
            cluster = BlockStorageUtils.getCluster(hostId);
        }
    }

    public List<BlockObjectRestRep> createAndExportVolumes() {
        // Create the volumes
        List<URI> volumeIds = BlockStorageUtils.createVolumes(project, virtualArray, virtualPool, nameParam,
                sizeInGb, count, consistencyGroup);
        for (URI volumeId : volumeIds) {
            logInfo("create.block.volume.create.volume", volumeId);
        }

        // See if an existing export exists for the host ports
        ExportGroupRestRep export = null;
        if (cluster != null) {
            export = BlockStorageUtils.findExportByCluster(cluster, project, virtualArray, null);
        } else {
            export = BlockStorageUtils.findExportByHost(host, project, virtualArray, null);
        }

        // If the export does not exist, create it
        if (export == null) {
            URI exportId = null;
            if (cluster != null) {
                exportId = BlockStorageUtils.createClusterExport(project, virtualArray, volumeIds, hlu, cluster);
            } else {
                exportId = BlockStorageUtils.createHostExport(project, virtualArray, volumeIds, hlu, host);
            }
            logInfo("create.block.volume.create.export", exportId);
        }
        // Add the volume to the existing export
        else {
            BlockStorageUtils.addVolumesToExport(volumeIds, hlu, export.getId());
            logInfo("create.block.volume.update.export", export.getId());
        }
        
        if (host != null) {
            ExecutionUtils.addAffectedResource(host.getId().toString());
        } else if (cluster != null) {
            ExecutionUtils.addAffectedResource(cluster.getId().toString());
        }

        // The volume is created and exported, clear the rollback steps so it will still be available if any other
        // further steps fail
        ExecutionUtils.clearRollback();

        // Get the volumes after exporting, volumes would not have WWNs until after export in VPLEX
        List<BlockObjectRestRep> volumes = BlockStorageUtils.getVolumes(volumeIds);
        return volumes;
    }
    
    public Double getSizeInGb() {
        return this.sizeInGb;
    }
}
