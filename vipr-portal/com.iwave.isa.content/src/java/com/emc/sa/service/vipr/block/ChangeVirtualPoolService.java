/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.ChangeBlockVolumeVirtualPool;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("ChangeVirtualPool")
public class ChangeVirtualPoolService extends ViPRService {

    @Param(ServiceParams.PROJECT)
    private URI projectId;

    @Param(ServiceParams.VOLUME)
    private List<String> volumeIds;

    @Param(ServiceParams.TARGET_VIRTUAL_POOL)
    private URI targetVirtualPool;

    @Param(value = ServiceParams.CONSISTENCY_GROUP, required = false)
    private URI consistencyGroup;

    @Param(value = ServiceParams.EXPORT_PATH_POLICY, required = false)
    private URI exportPathPolicy;

    @Param(value = ServiceParams.RDF_GROUP, required = false)
    private URI rdfGroup;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        checkForBootVolumes(volumeIds);
    }
    
    @Override
    public void execute() throws Exception {
        Tasks<VolumeRestRep> tasks = execute(new ChangeBlockVolumeVirtualPool(uris(volumeIds), targetVirtualPool, consistencyGroup, rdfGroup, exportPathPolicy, false));
        addAffectedResources(tasks);
    }
}
