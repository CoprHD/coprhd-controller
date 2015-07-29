/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.SHARE_NAME;
import static com.emc.sa.service.ServiceParams.SNAPSHOT;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("RemoveFileSnapshotShare")
public class DeleteFileSnapshotShareService extends ViPRService {

    @Param(SNAPSHOT)
    protected String snapshotId;

    @Param(SHARE_NAME)
    protected String shareName;

    @Override
    public void execute() {
        FileStorageUtils.deactivateSnapshotShare(uri(snapshotId), shareName);
    }
}
