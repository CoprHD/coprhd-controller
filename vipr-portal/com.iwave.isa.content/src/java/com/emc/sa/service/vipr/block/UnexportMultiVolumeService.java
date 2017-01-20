/*
 * Copyright (c) 2012-2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.db.client.model.Volume;

@Service("UnexportMultiVolume")
public class UnexportMultiVolumeService extends ViPRService {
    
    @Param(PROJECT)
    protected URI projectId;
    
    @Param(VOLUMES)
    protected List<String> volumeIds;
    
    @Override
    public void precheck() throws Exception {
        for (String volumeId : volumeIds) {
            Volume volume = BlockStorageUtils.getBlockVolumeById(URI.create(volumeId));
            BlockStorageUtils.checkEvents(volume);
        }
    }
    
    @Override
    public void execute() throws Exception {
        BlockStorageUtils.unexportVolumes(uris(volumeIds));
    }
}
