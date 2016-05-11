/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.ChangeBlockVolumeVirtualPool;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("ChangeVolumeVirtualPool")
public class ChangeVolumeVirtualPoolService extends ViPRService {

    @Param(ServiceParams.PROJECT)
    private URI projectId;

    @Param(ServiceParams.VOLUME)
    private URI volumeId;

    @Param(ServiceParams.TARGET_VIRTUAL_POOL)
    private URI targetVirtualPool;

    @Param(value = ServiceParams.CONSISTENCY_GROUP, required = false)
    private URI consistencyGroup;

    @Param(value = ServiceParams.MIGRATION_TYPE, required = false)
    private String migrationType;

    @Param(value = ServiceParams.LINUX_HOST, required = false)
    private URI migrationHost;

    @Override
    public void execute() throws Exception {
        Tasks<VolumeRestRep> tasks = execute(new ChangeBlockVolumeVirtualPool(volumeId, targetVirtualPool, consistencyGroup,
                migrationType, migrationHost));
        addAffectedResources(tasks);
    }

}
