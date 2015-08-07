/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.CreateBlockSnapshot;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

@Service("CreateBlockSnapshot")
public class CreateBlockSnapshotService extends ViPRService {
    @Param(VOLUMES)
    protected List<String> volumeIds;

    @Param(NAME)
    protected String nameParam;

    @Param(value = TYPE, required = false)
    protected String type;

    private List<BlockObjectRestRep> volumes;

    @Override
    public void precheck() {
        volumes = Lists.newArrayList();
        for (String volumeId : volumeIds) {
            volumes.add(BlockStorageUtils.getBlockResource(uri(volumeId)));
        }
    }

    @Override
    public void execute() {
        for (BlockObjectRestRep volume : volumes) {
            Tasks<BlockSnapshotRestRep> tasks = execute(new CreateBlockSnapshot(volume.getId(), type, nameParam));
            addAffectedResources(tasks);
        }
    }
}
