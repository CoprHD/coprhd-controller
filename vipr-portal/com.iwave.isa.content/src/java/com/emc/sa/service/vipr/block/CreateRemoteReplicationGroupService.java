/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.service.ServiceParams;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.remotereplication.tasks.CreateRemoteReplicationGroup;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupCreateParams;


@Service("CreateRemoteReplicationGroup")
public class CreateRemoteReplicationGroupService extends ViPRService {

    @Param(ServiceParams.NAME)
    protected String name;

    @Param(ServiceParams.SRC_SYSTEM)
    protected String sourceSystem;

    @Param(ServiceParams.TGT_SYSTEM)
    protected String targetSystem;
    
    @Param(ServiceParams.SOURCE_STORAGE_PORTS)
    protected List<String> sourcePorts;
    
    @Param(ServiceParams.TARGET_STORAGE_PORTS)
    protected List<String> targetPorts;
    
    @Param(ServiceParams.STORAGE_TYPE)
    protected String storageType;

    @Param(ServiceParams.REMOTE_REPLICATION_MODE)
    protected String remoteReplicationMode;

    @Param(ServiceParams.REMOTE_REPLICATION_STATE)
    protected String remoteReplicationState;

    @Param(ServiceParams.CONSISTENCY_GROUP_ENFORCED)
    protected Boolean groupConsistencyEnforced;

    URI sourceSystemUri;
    URI targetSystemUri;

    @Override
    public void precheck() {

        StringBuffer preCheckErrors = new StringBuffer();

        try {
            sourceSystemUri = new URI(sourceSystem);
        }
        catch (URISyntaxException e) {
            preCheckErrors.append("Source storage system ID (" + sourceSystem +
                    ") is not a valid URI. " + e.getMessage() + ".  ");
        }

        try {
            targetSystemUri = new URI(targetSystem);
        }
        catch (URISyntaxException e) {
            preCheckErrors.append("Target storage system ID (" + targetSystem +
                    ") is not a valid URI. " + e.getMessage() + ".  ");
        }

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString());
        }
    }

    @Override
    public void execute() {

        RemoteReplicationGroupCreateParams params = new RemoteReplicationGroupCreateParams();

        params.setDisplayName(name);
        params.setSourceSystem(sourceSystemUri);
        params.setTargetSystem(targetSystemUri);
        params.setSourcePorts(sourcePorts);
        params.setTargetPorts(targetPorts);
        params.setStorageSystemType(storageType);
        params.setReplicationMode(remoteReplicationMode);
        params.setReplicationState(remoteReplicationState);
        params.setIsGroupConsistencyEnforced(groupConsistencyEnforced);

        TaskResourceRep task = execute(new CreateRemoteReplicationGroup(params));

        logInfo("Created RemoteReplicationGroup '" + name + "'");
    }
}
