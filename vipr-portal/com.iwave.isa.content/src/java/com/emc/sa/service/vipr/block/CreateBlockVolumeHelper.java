/*
 * Copyright (c) 2012-2015 iWave Software LLC
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
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;

public class CreateBlockVolumeHelper {
    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(PROJECT)
    protected URI project;

    @Param(NAME)
    protected String nameParam;

    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    @Param(value = NUMBER_OF_VOLUMES, required = false)
    protected Integer count;

    @Param(value = CONSISTENCY_GROUP, required = false)
    protected URI consistencyGroup;

    public List<URI> createVolumes() {
        List<URI> volumeIds = BlockStorageUtils.createVolumes(project, virtualArray, virtualPool, nameParam,
                sizeInGb, count, consistencyGroup);
        for (URI volumeId : volumeIds) {
            logInfo("create.block.volume.create.volume", volumeId);
        }
        return volumeIds;
    }

    public String getName() {
        return this.nameParam;
    }

    public URI getProject() {
        return this.project;
    }

    public URI getVirtualArray() {
        return this.virtualArray;
    }

    public URI getVirtualPool() {
        return this.virtualPool;
    }

    public Double getSizeInGb() {
        return this.sizeInGb;
    }

    public URI getConsistencyGroup() {
        return this.consistencyGroup;
    }

    public Integer getCount() {
        return this.count;
    }
}
