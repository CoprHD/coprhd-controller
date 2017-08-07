package com.emc.sa.service.vipr.remotereplication.tasks;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationOperationParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.core.RemoteReplicationManagementClient;
import com.emc.vipr.client.core.RemoteReplicationManagementClient.Operation;

public class RemoteReplicationManagementTask extends WaitForTasks<TaskResourceRep> {

    RemoteReplicationOperationParam params;
    Operation operation;

    public RemoteReplicationManagementTask(RemoteReplicationOperationParam params, Operation operation) {
        this.params = params;
        this.operation = operation;
        this.setDetail("RemoteReplicationManagementTask.detail",operation.name());
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {

        Tasks<TaskResourceRep> createdTasks;

        RemoteReplicationManagementClient rrClient =
                getClient().remoteReplicationManagement();
        
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
        case RESTORE:
            createdTasks = rrClient.restoreRemoteReplication(params);
            break;
        case STOP:
            createdTasks = rrClient.stopRemoteReplication(params);
            break;
        case SWAP:
            createdTasks = rrClient.swapRemoteReplication(params);
            break;
        default:
            throw new IllegalStateException("Invalid Remote Replication Management operation: " +
                    operation.name());
        }

        logInfo("Created " + createdTasks.getTasks().size() +
                " tasks for operation " + operation.name());

        ViPRExecutionUtils.addAffectedResources(createdTasks);

        for(Task<TaskResourceRep> task : createdTasks.getTasks()) {
            addOrderIdTag(task.getTaskResource().getId()); // link task to order
        }
        
        return createdTasks;
    } 
}
