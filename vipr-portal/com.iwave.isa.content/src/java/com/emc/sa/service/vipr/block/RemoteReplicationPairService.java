/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.service.ServiceParams;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;


@Service("RemoteReplicationPair")
public class RemoteReplicationPairService extends ViPRService {

    @Param(ServiceParams.REMOTE_REPLICATION_SET)
    protected String remoteReplicationSet;

    @Param(ServiceParams.REMOTE_REPLICATION_GROUP)
    protected String remoteReplicationGroup;

    @Param(ServiceParams.REMOTE_REPLICATION_PAIR)
    protected String remoteReplicationPair;

    @Param(ServiceParams.REMOTE_REPLICATION_OPERATION)
    protected String remoteReplicationOperation;

    @Override
    public void precheck() {
   
    }

    @Override
    public void execute() {
        logInfo("Not implemented");
    }
}
