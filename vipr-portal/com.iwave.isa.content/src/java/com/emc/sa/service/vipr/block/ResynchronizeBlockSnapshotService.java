/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.SNAPSHOTS;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Task;

@Service("ResynchronizeBlockSnapshot")
public class ResynchronizeBlockSnapshotService extends ViPRService  {
    
    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(value = VOLUME, required = false)
    protected URI consistencyGroupId;

    @Param(SNAPSHOTS)
    protected List<String> snapshotIds;

    @Override
    public void execute() throws Exception {

        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            BlockStorageUtils.resynchronizeBlockSnapshots(uris(snapshotIds));
        } else {
            for (URI copyId : uris(snapshotIds)) {
                Task<? extends DataObjectRestRep> task = ConsistencyUtils.resynchronizeSnapshot(consistencyGroupId, copyId);
                addAffectedResource(task);
            }
        }
    }

}
