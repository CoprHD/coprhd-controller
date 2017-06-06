/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.service.ServiceParams;

import java.util.List;

import com.emc.sa.asset.providers.RemoteReplicationProvider;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("RemoteReplicationSplit")
public class RemoteReplicationSplitService extends ViPRService {

    @Param(ServiceParams.REMOTE_REPLICATION_SET)
    protected String remoteReplicationSet;

    @Param(ServiceParams.REMOTE_REPLICATION_GROUP)
    protected String remoteReplicationGroup;

    @Param(ServiceParams.REMOTE_REPLICATION_CG_OR_PAIR)
    protected String remoteReplicationCgOrPair;

    @Param(value = ServiceParams.REMOTE_REPLICATION_PAIRS_CGS, required = false)
    protected List<String> remoteReplicationPairsOrCGs;

    private enum OperationTarget {SET, GROUP, CGS_IN_SET, PAIRS_IN_SET ,CGS_IN_GROUP ,PAIRS_IN_GROUP}

    OperationTarget thisOperationTarget;

    @Override
    public void precheck() {

        setOperationTarget();

        switch(thisOperationTarget) {
        case SET:
            logInfo("Operating on Remote Replication Set");
            break;
        case GROUP:
            logInfo("Operating on Remote Replication Group");
            break;
        case PAIRS_IN_SET:
            logInfo("Operating on selected pairs in Remote Replication Set");
            validatePairsSelected();
            break;
        case PAIRS_IN_GROUP:
            logInfo("Operating on selected pairs in Remote Replication Group");
            validatePairsSelected();
            break;
        case CGS_IN_SET:
            logInfo("Operating on selected Consistency Groups");
            validatePairsSelected();
            break;
        case CGS_IN_GROUP:
            logInfo("Operating on selected Consistency Groups");
            validatePairsSelected();
        }
    }

    @Override
    public void execute() {

        switch(thisOperationTarget) {
        case SET:
            logError("Pair operation not implemented for Remote Replication Sets");
            break;
        case GROUP:
            logError("Pair operation not implemented for Remote Replication Groups");
            break;
        case PAIRS_IN_SET:
            logError("Pair operation not implemented for Remote Replication Pairs in Sets");
            break;
        case PAIRS_IN_GROUP:
            logError("Pair operation not implemented for Remote Replication Pairs in Groups");
            break;
        case CGS_IN_SET:
            logError("Pair operation not implemented for Consistency Groups in Remote Replication Sets");
            break;
        case CGS_IN_GROUP:
            logError("Pair operation not implemented for Consistency Groups in Remote Replication Groups");
        }
    }

    private void setOperationTarget() {

        thisOperationTarget = OperationTarget.SET;

        if (!RemoteReplicationProvider.NO_GROUP.equals(remoteReplicationGroup)) {
            thisOperationTarget = OperationTarget.GROUP;
        }

        if (RemoteReplicationProvider.RR_PAIR.equals(remoteReplicationCgOrPair)) {
            if (thisOperationTarget == OperationTarget.GROUP) {
                thisOperationTarget = OperationTarget.PAIRS_IN_GROUP;
            } else {
                thisOperationTarget = OperationTarget.PAIRS_IN_SET;
            }
        } else if (RemoteReplicationProvider.CONSISTENCY_GROUP.equals(remoteReplicationCgOrPair)) {
            if (thisOperationTarget == OperationTarget.GROUP) {
                thisOperationTarget = OperationTarget.CGS_IN_GROUP;
            } else {
                thisOperationTarget = OperationTarget.CGS_IN_SET;
            }
        }
    }

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
