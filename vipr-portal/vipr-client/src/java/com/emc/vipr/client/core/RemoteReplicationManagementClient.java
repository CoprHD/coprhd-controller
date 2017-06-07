/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.remotereplication.RemoteReplicationOperationParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Remote Replication Management resources.
 * <p>
 * Base URL: <tt>/block/remotereplicationmanagement</tt>
 * 
 */
public class RemoteReplicationManagementClient {

    private final static Logger _log = LoggerFactory.getLogger(RemoteReplicationManagementClient.class);
    
    private RestClient client;
    private ViPRCoreClient coreClient;
    private TaskList taskListResult;

    public RemoteReplicationManagementClient(ViPRCoreClient coreClient,RestClient client) {
        this.client = client;
        this.coreClient = coreClient;
    }

    public static enum Operation {FAILOVER("/failover"),FAILBACK("/failback"),ESTABLISH("/establish"),
        SUSPEND("/suspend"),SPLIT("/suspend"),RESUME("/resume"),STOP("/stop"),SWAP("/swap");
        private String path;
        Operation(String path) {
            this.path = path;
        }
        public String getPath(){
            return path;
        }
    }

    public TaskList failoverRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.FAILOVER);
    }

    public TaskList failbackRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.FAILBACK);
    }

    public TaskList establishRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.ESTABLISH);
    }

    public TaskList splitRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.SPLIT);
    }

    public TaskList suspendRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.SUSPEND);
    }

    public TaskList resumeRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.RESUME);
    }

    public TaskList stopRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.STOP);
    }

    public TaskList swapRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.SWAP);
    }

    private TaskList performOperation(RemoteReplicationOperationParam operationParam, Operation operation) {
        /*
         * Catalog Service may have sent:
         *   1) ID of a RR Set
         *   2) ID of a RR Group
         *   3) IDs of one or more CGs
         *   4) IDs of one or more pairs
         */

        if (operationParam.getIds().isEmpty()) {
            throw new IllegalStateException("No IDs supplied for operation");
        }
        
        
        taskListResult = new TaskList();

        switch (getContext(operationParam)) {
        case RR_SET:
            operateOnSet(operationParam,operation);
            break;
        case RR_GROUP:
            operateOnGroup(operationParam,operation);
            break;
        case RR_PAIR:
            operateOnPairs(operationParam,operation);
            break;
        case RR_SET_CG:
            operateOnSetCG(operationParam,operation);
            break;
        case RR_GROUP_CG:
            operateOnGroupCG(operationParam,operation);
            break;
        }
        return taskListResult;
    }

    private void operateOnSet(RemoteReplicationOperationParam operationParam, Operation operation) {
        _log.info("Operating on sets");
        for(URI setId : operationParam.getIds()) { // assume all IDs are RR Set IDs

            List<NamedRelatedResourceRep> pairs = coreClient.remoteReplicationSets().
                    listRemoteReplicationPairs(setId).getRemoteReplicationPairs();

            Map<URI,String> pairMap = new HashMap<>();
            for(NamedRelatedResourceRep pair: pairs) {
                pairMap.put(pair.getId(), pair.getName());
            }

            _log.info("Operating on set " + setId + " with pairs " +
                    pairMap.values() + pairMap.keySet());

            // insert IDs of pairs in this RR Set in parameters for API call
            RemoteReplicationOperationParam pairParam =
                    new RemoteReplicationOperationParam(
                            RemoteReplicationOperationParam.OperationContext.RR_SET.name(),
                            new ArrayList<URI>(pairMap.keySet()));

            callApi(pairParam,operation);
        }
    }

    private void operateOnGroup(RemoteReplicationOperationParam operationParam, Operation operation) {
        _log.info("Operating on group");
        for(URI groupId : operationParam.getIds()) { // assume all IDs are RR Group IDs

            List<NamedRelatedResourceRep> pairs = coreClient.remoteReplicationGroups().
                    listRemoteReplicationPairs(groupId.toString()).getRemoteReplicationPairs();

            Map<URI,String> pairMap = new HashMap<>();
            for(NamedRelatedResourceRep pair: pairs) {
                pairMap.put(pair.getId(), pair.getName());
            }

            _log.info("Operating on group " + groupId + " with pairs " +
                    pairMap.values() + pairMap.keySet());

            RemoteReplicationOperationParam pairParam = 
                    new RemoteReplicationOperationParam(
                            RemoteReplicationOperationParam.OperationContext.RR_GROUP.name(),
                            new ArrayList<URI>(pairMap.keySet()));

            callApi(pairParam,operation);
        }
    }

    private void operateOnPairs(RemoteReplicationOperationParam operationParam, Operation operation) {
        _log.info("Operating on pairs");
        callApi(operationParam,operation);
    }

    private void operateOnSetCG(RemoteReplicationOperationParam operationParam, Operation operation) {
        _log.info("Operating on CG in set");
        callApi(operationParam,operation);
    }

    private void operateOnGroupCG(RemoteReplicationOperationParam operationParam, Operation operation) {
        _log.info("Operating on CG in group");
        callApi(operationParam,operation);
    }

    private void callApi(RemoteReplicationOperationParam params,Operation op) {
        TaskList tasks = client.post(TaskList.class, params, 
                PathConstants.BLOCK_REMOTE_REPLICATION_MANAGEMENT_URL + op.getPath());
        _log.info("API returned " + taskListResult.getTaskList().size() + " tasks");
        taskListResult.addTasks(tasks);
    }

    private RemoteReplicationOperationParam.OperationContext getContext(RemoteReplicationOperationParam operationParam) {
        return RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());
    }
}
