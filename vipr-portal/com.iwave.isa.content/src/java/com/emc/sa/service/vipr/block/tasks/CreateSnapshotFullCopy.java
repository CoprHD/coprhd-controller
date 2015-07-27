/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.vipr.client.Tasks;

public class CreateSnapshotFullCopy extends WaitForTasks<BlockSnapshotRestRep> {

    private final URI snapshotId;
    private final String name;
    private final int count;
    
    public CreateSnapshotFullCopy(String snapshotId, String name, int count) {
       this(uri(snapshotId), name, count); 
    }
    
    public CreateSnapshotFullCopy(URI snapshotId, String name, int count) {
       this.snapshotId = snapshotId;
       this.name = name;
       this.count = count;
       provideDetailArgs(snapshotId, name, count);
    }
    
    @Override
    protected Tasks<BlockSnapshotRestRep> doExecute() throws Exception {
        VolumeFullCopyCreateParam param = new VolumeFullCopyCreateParam();
        param.setName(name);
        param.setCount(count);
        return getClient().blockSnapshots().createFullCopy(snapshotId, param);
    }
}
