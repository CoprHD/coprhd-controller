/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.SNAPSHOTS;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("RestoreFileSnapshot")
public class RestoreFileSnapshotService extends ViPRService {
    @Param(SNAPSHOTS)
    protected List<String> snapshotIds;

    @Override
    public void execute() {
        for (String snapshotId : snapshotIds) {
            FileStorageUtils.restoreFileSnapshot(uri(snapshotId));
        }
    }
}
