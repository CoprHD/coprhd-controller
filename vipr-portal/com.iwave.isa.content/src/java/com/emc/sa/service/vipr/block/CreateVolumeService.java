/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.NUMBER_OF_VOLUMES;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateVolume")
public class CreateVolumeService extends ViPRService {

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(PROJECT)
    protected URI project;

    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(value = NUMBER_OF_VOLUMES, required = false)
    protected Integer count;

    @Param(NAME)
    protected String volumeName;

    @Param(value = CONSISTENCY_GROUP, required = false)
    protected URI consistencyGroup;

    @Override
    public void execute() throws Exception {
        BlockStorageUtils.createVolumes(project, virtualArray, virtualPool, volumeName, sizeInGb, count,
                consistencyGroup);
    }

    public URI getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        this.virtualPool = virtualPool;
    }

    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    public Double getSizeInGb() {
        return sizeInGb;
    }

    public void setSizeInGb(Double sizeInGb) {
        this.sizeInGb = sizeInGb;
    }

    public URI getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
    }
}
