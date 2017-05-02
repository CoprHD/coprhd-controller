/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.remotereplication.tasks;

import com.emc.sa.engine.ExecutionUtils;
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
      
        TaskResourceRep task = getClient().remoteReplicationGroups().createRemoteReplicationGroup(params);

        if ((task != null) && (task.getResource() != null) ) {
            ExecutionUtils.addAffectedResource(task.getResource().getId().toString());
        }

        return task;
    } 

}
