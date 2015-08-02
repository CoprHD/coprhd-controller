/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.EXPORT;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SNAPSHOT;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("UnexportSnapshot")
public class UnexportSnapshotService extends ViPRService {

    @Param(PROJECT)
    protected URI projectId;

    @Param(SNAPSHOT)
    protected URI snapshotId;

    @Param(EXPORT)
    protected URI exportId;

    @Override
    public void execute() throws Exception {
        BlockStorageUtils.removeBlockResourceFromExport(snapshotId, exportId);
    }
}
