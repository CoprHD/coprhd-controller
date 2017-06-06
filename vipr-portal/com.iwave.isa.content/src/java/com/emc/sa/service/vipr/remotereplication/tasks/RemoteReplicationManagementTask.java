package com.emc.sa.service.vipr.remotereplication.tasks;

import java.util.List;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationOperationParam;
import com.emc.vipr.client.core.RemoteReplicationManagementClient;
import com.emc.vipr.client.core.RemoteReplicationManagementClient.Operation;

public class RemoteReplicationManagementTask extends ViPRExecutionTask<List<TaskResourceRep>> {

    RemoteReplicationOperationParam params;
    Operation operation;
    RemoteReplicationManagementClient rrClient;

    public RemoteReplicationManagementTask(RemoteReplicationOperationParam params, Operation operation) {
        this.params = params;
        this.operation = operation;
        this.rrClient = getClient().remoteReplicationManagement();
    }

    @Override
    public List<TaskResourceRep> executeTask() throws Exception {

        TaskList createdTasks;
        
        logInfo("Task executing for operation " + operation.name());

        switch(operation) {
        case FAILOVER:
            createdTasks = rrClient.failoverRemoteReplication(params);
            break;
        case FAILBACK:
            createdTasks = rrClient.failbackRemoteReplication(params);
            break;
        case ESTABLISH:
            createdTasks = rrClient.establishRemoteReplication(params);
            break;
        case SPLIT:
            createdTasks = rrClient.splitRemoteReplication(params);
            break;
        case SUSPEND:
            createdTasks = rrClient.suspendRemoteReplication(params);
            break;
        case RESUME:
            createdTasks = rrClient.resumeRemoteReplication(params);
            break;
        case STOP:
            createdTasks = rrClient.stopRemoteReplication(params);
            break;
        default:
            throw new IllegalStateException("Invalid Remote Replication Management operation: " + 
                    operation.name());        
        }

        ViPRExecutionUtils.addAffectedResources(createdTasks.getTaskList());

        logInfo("Created " + createdTasks.getTaskList().size() + " tasks for operation " + operation.name());
        
        return createdTasks.getTaskList();
    } 
}
