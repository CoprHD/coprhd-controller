/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.service.ServiceParams;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.remotereplication.tasks.RemoteReplicationManagementTask;
import com.emc.storageos.model.remotereplication.RemoteReplicationOperationParam;
import com.emc.vipr.client.core.RemoteReplicationManagementClient.Operation;

@Service("RemoteReplicationFailover")
public class RemoteReplicationFailoverService extends ViPRService {

    Operation operation = Operation.FAILOVER;

    @Param(ServiceParams.REMOTE_REPLICATION_SET)
    protected String remoteReplicationSet;

    @Param(ServiceParams.REMOTE_REPLICATION_GROUP)
    protected String remoteReplicationGroup;

    @Param(ServiceParams.REMOTE_REPLICATION_CG_OR_PAIR)
    protected String remoteReplicationCgOrPair;

    @Param(value = ServiceParams.REMOTE_REPLICATION_PAIRS_CGS, required = false)
    protected List<String> remoteReplicationPairsOrCGs;

    RemoteReplicationOperationParam paramsForApi;

    @Override
    public void precheck() {
        paramsForApi = RemoteReplicationUtils.createParams(remoteReplicationSet,remoteReplicationGroup,
                remoteReplicationCgOrPair,remoteReplicationPairsOrCGs);
    }

    /*
     * Execute Catalog Service
     * @see com.emc.sa.engine.service.ExecutionService#execute()
     */
    @Override
    public void execute() {
       execute(new RemoteReplicationManagementTask(paramsForApi,operation));
    }
}
