/*
 * Copyright (c) 2016 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.DELETION_TYPE;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;

@Service("RemoveUnexportedBlockStorage")
public class RemoveUnexportedBlockStorageService extends ViPRService {
    @Param(VOLUMES)
    protected List<String> volumeIds;
    
    @Param(PROJECT)
    protected String project;

    @Param(DELETION_TYPE)
    protected VolumeDeleteTypeEnum deletionType;

    @Override
    public void precheck() {
        BlockStorageUtils.getBlockResources(uris(volumeIds));
        if (!deletionType.equals(VolumeDeleteTypeEnum.VIPR_ONLY)) {
            BlockStorageUtils.verifyVolumeDependencies(uris(volumeIds), uri(project));
        }
        checkForBootVolumes(volumeIds);
    }

    @Override
    public void execute() {
        BlockStorageUtils.deactivateVolumes(uris(volumeIds), deletionType);
    }

}
