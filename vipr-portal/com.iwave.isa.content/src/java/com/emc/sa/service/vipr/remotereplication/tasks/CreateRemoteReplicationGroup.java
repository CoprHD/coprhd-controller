/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.remotereplication.tasks;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupCreateParams;
import com.emc.vipr.client.Task;

public class CreateRemoteReplicationGroup extends WaitForTask<TaskResourceRep> {

    RemoteReplicationGroupCreateParams params;
    
    public CreateRemoteReplicationGroup(RemoteReplicationGroupCreateParams params) {
       this.params = params;
       this.provideDetailArgs(params.getDisplayName());
    }

    @Override
    protected Task<TaskResourceRep> doExecute() throws Exception {

        // API expects name for storage type
        params.setStorageSystemType(getClient().storageSystemType().
                getStorageSystemTypeRestRep(params.getStorageSystemType()).getStorageTypeName());

        return getClient().remoteReplicationGroups().createRemoteReplicationGroup(params);
    }
}
