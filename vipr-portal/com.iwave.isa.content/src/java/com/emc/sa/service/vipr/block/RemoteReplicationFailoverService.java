/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.service.ServiceParams;

import java.util.Arrays;
import java.util.List;

import com.emc.sa.asset.providers.RemoteReplicationProvider;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.remotereplication.tasks.RemoteReplicationManagementTask;
import com.emc.storageos.model.remotereplication.RemoteReplicationOperationParam;
import com.emc.vipr.client.core.RemoteReplicationManagementClient.Operation;

@Service("RemoteReplicationFailover")
public class RemoteReplicationFailoverService extends ViPRService {

    @Param(ServiceParams.REMOTE_REPLICATION_SET)
    protected String remoteReplicationSet;

    @Param(ServiceParams.REMOTE_REPLICATION_GROUP)
    protected String remoteReplicationGroup;

    @Param(ServiceParams.REMOTE_REPLICATION_CG_OR_PAIR)
    protected String remoteReplicationCgOrPair;

    @Param(value = ServiceParams.REMOTE_REPLICATION_PAIRS_CGS, required = false)
    protected List<String> remoteReplicationPairsOrCGs;

    private enum ServiceContext {SET, GROUP, CGS_IN_SET, PAIRS_IN_SET ,CGS_IN_GROUP ,PAIRS_IN_GROUP}

    ServiceContext rrServiceContext;
    RemoteReplicationOperationParam paramsForApi;

    @Override
    public void precheck() {

        setServiceContext();

        /*
         * Validate and construct params for service
         */
        switch(rrServiceContext) {
        case SET:
            logInfo("Operating on Remote Replication Set");
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_SET.name(),
                    Arrays.asList(uri(remoteReplicationSet)));
            break;
        case GROUP:
            logInfo("Operating on Remote Replication Group");
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_GROUP.name(),
                    Arrays.asList(uri(remoteReplicationGroup)));
            break;
        case PAIRS_IN_SET:
            logInfo("Operating on selected pairs in Remote Replication Set");
            validatePairsSelected();
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_PAIR.name(),
                    Arrays.asList(uri(remoteReplicationGroup)));
            break;
        case PAIRS_IN_GROUP:
            logInfo("Operating on selected pairs in Remote Replication Group");
            validatePairsSelected();
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_PAIR.name(),
                    Arrays.asList(uri(remoteReplicationGroup)));
            break;
        case CGS_IN_SET:
            logInfo("Operating on selected Consistency Groups");
            validatePairsSelected();
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_SET_CG.name(),
                    Arrays.asList(uri(remoteReplicationGroup)));
            break;
        case CGS_IN_GROUP:
            logInfo("Operating on selected Consistency Groups");
            validatePairsSelected();
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_GROUP_CG.name(),
                    Arrays.asList(uri(remoteReplicationGroup)));
        default:
            throw new IllegalStateException("Invalid Remote Replication Management operation target: " +
                    rrServiceContext.toString());
        }
    }

    /*
     * Execute Catalog Service
     * @see com.emc.sa.engine.service.ExecutionService#execute()
     */
    @Override
    public void execute() {
       execute(new RemoteReplicationManagementTask(paramsForApi,Operation.FAILOVER));
    }

    /*
     * Determine whether to operate on a set, group, cgs or RR pairs
     */
    private void setServiceContext() {

        rrServiceContext = ServiceContext.SET;

        if (!RemoteReplicationProvider.NO_GROUP.equals(remoteReplicationGroup)) {
            rrServiceContext = ServiceContext.GROUP;
        }

        if (RemoteReplicationProvider.RR_PAIR.equals(remoteReplicationCgOrPair)) {
            if (rrServiceContext == ServiceContext.GROUP) {
                rrServiceContext = ServiceContext.PAIRS_IN_GROUP;
            } else {
                rrServiceContext = ServiceContext.PAIRS_IN_SET;
            }
        } else if (RemoteReplicationProvider.CONSISTENCY_GROUP.equals(remoteReplicationCgOrPair)) {
            if (rrServiceContext == ServiceContext.GROUP) {
                rrServiceContext = ServiceContext.CGS_IN_GROUP;
            } else {
                rrServiceContext = ServiceContext.CGS_IN_SET;
            }
        }
        logInfo("Context for operation target is " + rrServiceContext.name());
    }

    /*
     * fail if no pairs/CGs selected
     */
    private void validatePairsSelected() {
        if (remoteReplicationPairsOrCGs == null) {
            String missingItems = "Remote Replication Pair(s)/Consistency Group(s)";
            if (RemoteReplicationProvider.RR_PAIR.equals(remoteReplicationCgOrPair)) {
                missingItems = "Remote Replication Pair(s)";
            } else {
                missingItems = "Consistency Group(s)";
            }
            throw new IllegalStateException("No " + missingItems + " selected");
        }
    }
}
