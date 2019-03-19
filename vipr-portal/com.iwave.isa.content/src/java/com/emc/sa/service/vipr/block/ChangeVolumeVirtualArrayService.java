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
import com.emc.sa.service.vipr.block.tasks.ChangeBlockVolumeVirtualArray;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("ChangeVolumeVirtualArray")
public class ChangeVolumeVirtualArrayService extends ViPRService {
    @Param(ServiceParams.PROJECT)
    private URI projectId;

    @Param(ServiceParams.VOLUMES)
    private List<String> volumeIds;

    @Param(ServiceParams.TARGET_VIRTUAL_ARRAY)
    private URI targetVirtualArray;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        // check to select max 100 resources at a time.
        checkForMaxResouceOrderLimit(volumeIds);
        checkForBootVolumes(volumeIds);
    }
    
    @Override
    public void execute() throws Exception {
        Tasks<VolumeRestRep> tasks = execute(new ChangeBlockVolumeVirtualArray(volumeIds, targetVirtualArray.toString()));
        addAffectedResources(tasks);
    }

    public URI getProjectId() {
        return projectId;
    }

    public void setProjectId(URI projectId) {
        this.projectId = projectId;
    }

    public List<String> getVolumeIds() {
        return volumeIds;
    }

    public void setVolumeId(List<String> volumeIds) {
        this.volumeIds = volumeIds;
    }

    public URI getTargetVirtualArray() {
        return targetVirtualArray;
    }

    public void setTargetVirtualArray(URI targetVirtualArray) {
        this.targetVirtualArray = targetVirtualArray;
    }
}
