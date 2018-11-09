/*
 * Copyright (c) 2012-2018 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.SNAPSHOTS;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ExpandBlockSnapshot")
public class ExpandBlockSnapshotService extends ViPRService {
    @Param(SNAPSHOTS)
    protected List<String> snapshotIds;
    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    @Override
    public void precheck() {
        BlockStorageUtils.getBlockResources(uris(snapshotIds));
    }

    @Override
    public void execute() {
        BlockStorageUtils.expandBlockSnapshots(uris(snapshotIds), sizeInGb);
    }
}
