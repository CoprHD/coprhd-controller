/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationOperationParam;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
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
        SUSPEND("/suspend"),SPLIT("/split"),RESUME("/resume"),STOP("/stop"),SWAP("/swap");
        private String path;
        Operation(String path) {
            this.path = path;
        }
        public String getPath(){
            return path;
        }
    }

    public Tasks<TaskResourceRep> failoverRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.FAILOVER);
    }

    public Tasks<TaskResourceRep> failbackRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.FAILBACK);
    }

    public Tasks<TaskResourceRep> establishRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.ESTABLISH);
    }

    public Tasks<TaskResourceRep> splitRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.SPLIT);
    }

    public Tasks<TaskResourceRep> suspendRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.SUSPEND);
    }

    public Tasks<TaskResourceRep> resumeRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.RESUME);
    }

    public Tasks<TaskResourceRep> stopRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.STOP);
    }

    public Tasks<TaskResourceRep> swapRemoteReplication(RemoteReplicationOperationParam operationParam) {
        return performOperation(operationParam,Operation.SWAP);
    }

    private Tasks<TaskResourceRep> performOperation(RemoteReplicationOperationParam operationParam, Operation operation) {
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
        case RR_GROUP_CG:
            operateOnCG(operationParam,operation);
        }

        return new Tasks<TaskResourceRep>(client,
                taskListResult.getTaskList(),TaskResourceRep.class);
    }

    private void operateOnSet(RemoteReplicationOperationParam operationParam, Operation operation) {
        _log.info("Operating on sets " + operationParam.getIds());
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
        _log.info("Operating on group " + operationParam.getIds());
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
        _log.info("Operating on pairs " + operationParam.getIds());
        callApi(operationParam,operation);
    }

    private void operateOnCG(RemoteReplicationOperationParam operationParam, Operation operation) {
        _log.info("Operating on CGs in set " + operationParam.getIds());
        // get pairs in CG for API
        List<NamedRelatedResourceRep> pairsInCgs = new ArrayList<>();
        for(URI cgId : operationParam.getIds()) { // assume all IDs are CG IDs
            for( RelatedResourceRep volume : coreClient.blockConsistencyGroups().get(cgId).getVolumes()) {
                RemoteReplicationPairList pairList = coreClient.remoteReplicationPairs().
                        listRelatedRemoteReplicationPairs(volume.getId());
                pairsInCgs.addAll(pairList.getRemoteReplicationPairs());
            }
        }
        List<URI> pairIds = new ArrayList<>();
        for(NamedRelatedResourceRep pairInCg : pairsInCgs) {
            pairIds.add(pairInCg.getId());
        }
        operationParam.setIds(pairIds);
        callApi(operationParam,operation);
    }

    private void callApi(RemoteReplicationOperationParam params,Operation op) {

        _log.info("Calling API " + op.getPath() + " with context " +
                params.getOperationContext() + " and IDs " + params.getIds());

        TaskList tasks = client.post(TaskList.class, params, 
                PathConstants.BLOCK_REMOTE_REPLICATION_MANAGEMENT_URL + op.getPath());

        taskListResult.addTasks(tasks);

        _log.info("API returned " + taskListResult.getTaskList().size() + " tasks");
    }

    private RemoteReplicationOperationParam.OperationContext getContext(
            RemoteReplicationOperationParam operationParam) {
        return RemoteReplicationOperationParam.OperationContext.
                valueOf(operationParam.getOperationContext());
    }
}
