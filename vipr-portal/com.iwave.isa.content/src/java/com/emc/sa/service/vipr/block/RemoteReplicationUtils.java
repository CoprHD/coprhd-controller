/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.sa.asset.providers.RemoteReplicationProvider;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.remotereplication.RemoteReplicationOperationParam;

public class RemoteReplicationUtils {

    private final static Logger _log = LoggerFactory.getLogger(RemoteReplicationUtils.class);
    private enum ServiceContext {SET, GROUP, CGS_IN_SET, PAIRS_IN_SET ,CGS_IN_GROUP ,PAIRS_IN_GROUP}

    public static RemoteReplicationOperationParam createParams(String remoteReplicationSet, String remoteReplicationGroup,
            String remoteReplicationCgOrPair, List<String> remoteReplicationPairsOrCGs) {

        ServiceContext rrServiceContext = ServiceContext.SET;

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
        _log.info("Context for operation target is " + rrServiceContext.name());

        RemoteReplicationOperationParam paramsForApi;

        switch(rrServiceContext) {
        case SET:
            _log.info("Operating on Remote Replication Set");
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_SET.name(),
                    Arrays.asList(ViPRService.uri(remoteReplicationSet)));
            break;
        case GROUP:
            _log.info("Operating on Remote Replication Group");
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_GROUP.name(),
                    Arrays.asList(ViPRService.uri(remoteReplicationGroup)));
            break;
        case PAIRS_IN_SET:
        case PAIRS_IN_GROUP:
            _log.info("Operating on selected pairs in Remote Replication Group");
            validatePairsSelected(remoteReplicationCgOrPair, remoteReplicationPairsOrCGs);
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_PAIR.name(),
                    ViPRService.uris(remoteReplicationPairsOrCGs));
            break;
        case CGS_IN_SET:
            _log.info("Operating on selected Consistency Groups");
            validatePairsSelected(remoteReplicationCgOrPair, remoteReplicationPairsOrCGs);
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_SET_CG.name(),
                    ViPRService.uris(remoteReplicationPairsOrCGs));
            break;
        case CGS_IN_GROUP:
            _log.info("Operating on selected Consistency Groups");
            validatePairsSelected(remoteReplicationCgOrPair, remoteReplicationPairsOrCGs);
            paramsForApi = new RemoteReplicationOperationParam(
                    RemoteReplicationOperationParam.OperationContext.RR_GROUP_CG.name(),
                    ViPRService.uris(remoteReplicationPairsOrCGs));
            break;
        default:
            throw new IllegalStateException("Invalid Remote Replication Management operation target: " +
                    rrServiceContext.toString());
        }
        return paramsForApi;
    }

    /*
     * fail if no pairs/CGs selected
     */
    private static void validatePairsSelected(String remoteReplicationCgOrPair, List<String> remoteReplicationPairsOrCGs) {
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
