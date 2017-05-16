/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.remotereplication.tasks;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupCreateParams;

public class CreateRemoteReplicationGroup extends ViPRExecutionTask<TaskResourceRep> {

    RemoteReplicationGroupCreateParams params;
    
    public CreateRemoteReplicationGroup(RemoteReplicationGroupCreateParams params) {
       this.params = params;
       this.provideDetailArgs(params.getDisplayName());
    }

    @Override
    public TaskResourceRep executeTask() throws Exception {

        // API expects name for storage type
        params.setStorageSystemType(getClient().storageSystemType().
                getStorageSystemTypeRestRep(params.getStorageSystemType()).getStorageTypeName());

        TaskResourceRep task = getClient().remoteReplicationGroups().createRemoteReplicationGroup(params);

        if ((task != null) && (task.getResource() != null) ) {
            ViPRExecutionUtils.addAffectedResource(task.getResource().getId());
        }

        return task;
    } 

}
