package com.emc.sa.service.vipr.migration;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.GetMobilityGroupVolumes;
import com.emc.sa.service.vipr.block.tasks.MigrateBlockVolumes;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("VolumeMigration")
public class VolumeMigrationService extends ViPRService {

    @Param(ServiceParams.MOBILITY_GROUP)
    private URI mobilityGroup;

    @Param(ServiceParams.TARGET_VIRTUAL_POOL)
    private URI targetVirtualPool;

    @Param(ServiceParams.TARGET_STORAGE_SYSTEM)
    private URI targetStorageSystem;

    @Override
    public void execute() throws Exception {
        List<URI> blockVolumes = execute(new GetMobilityGroupVolumes(mobilityGroup));
        Tasks<VolumeRestRep> tasks = execute(new MigrateBlockVolumes(blockVolumes, targetVirtualPool, targetStorageSystem));
        addAffectedResources(tasks);
    }
}
