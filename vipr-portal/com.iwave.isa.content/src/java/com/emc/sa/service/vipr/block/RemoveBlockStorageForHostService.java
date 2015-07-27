/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HOST;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;

@Service("RemoveBlockStorageForHost")
public class RemoveBlockStorageForHostService extends RemoveBlockStorageService {
    
    @Param(HOST)
    protected URI hostId;

    @Override
    public void execute() {
        BlockStorageUtils.removeBlockResources(uris(volumeIds), deletionType);
        //form is always passing hostId, never clusterId - need to figure out which it is.
        String hostOrClusterId = BlockStorageUtils.getHostOrClusterId(hostId);
        if (hostOrClusterId != null) {
            ExecutionUtils.addAffectedResource(hostOrClusterId.toString());
        }
    }

}
