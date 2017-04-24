/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.service.ServiceParams;
import java.net.URI;

import org.eclipse.jetty.util.log.Log;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateRemoteReplicationGroup")
public class CreateRemoteReplicationGroupService extends ViPRService {

/**
     <remote_replication_group_create>
       <source_system>urn:storageos:StorageSystem:bf0d90de-5f09-4012-a125-707838d0724a:vdc1</source_system>
       <target_system>urn:storageos:StorageSystem:93b1c291-2b68-4542-a6ac-4391b8c7daae:vdc1</target_system>
       <source_ports>
         <source_port>61:FE:FE:FE:FE:FE:FE:10</source_port>
       </source_ports>
       <target_ports>
         <target_port>62:FE:FE:FE:FE:FE:FE:10</target_port>
       </target_ports>
       <storage_system_type>driversystem</storage_system_type>
       <name>mendes-test</name>
       <replication_mode>synchronous</replication_mode>
       <replication_state>myRepState</replication_state>
       <is_group_consistency_enforced>true</is_group_consistency_enforced>
     </remote_replication_group_create>
*/
    @Param(ServiceParams.PROJECT)
    protected URI project;

    @Param(ServiceParams.NAME)
    protected String name;

    @Param(ServiceParams.SRC_SYSTEM)
    protected String sourceSystem;

    @Param(ServiceParams.TGT_SYSTEM)
    protected String targetSystem;
    
    @Param(ServiceParams.SOURCE_STORAGE_PORTS)
    protected String sourcePorts;
    
    @Param(ServiceParams.TARGET_STORAGE_PORTS)
    protected String targetPorts;
    
    @Param(ServiceParams.STORAGE_TYPE)
    protected String storageType;

    @Param(ServiceParams.REMOTE_REPLICATION_MODE)
    protected String remoteReplicationMode;

    @Param(ServiceParams.REMOTE_REPLICATION_STATE)
    protected String remoteReplicationState;

    @Param(ServiceParams.CONSISTENCY_GROUP_ENFORCED)
    protected Boolean consistencyGroupEnforced;

    @Override
    public void precheck() {
 
        logInfo("DEBUG: project = '" + project + "'");
        logInfo("DEBUG: name = '" + name + "'");
        logInfo("DEBUG: sourceSystem = '" + sourceSystem + "'");
        logInfo("DEBUG: targetSystem = '" + targetSystem + "'");
        logInfo("DEBUG: sourcePorts = '" + sourcePorts + "'");
        logInfo("DEBUG: targetPorts = '" + targetPorts + "'");
        logInfo("DEBUG: storageType = '" + storageType + "'");
        logInfo("DEBUG: remoteReplicationMode = '" + remoteReplicationMode + "'");
        logInfo("DEBUG: remoteReplicationState = '" + remoteReplicationState + "'");
        logInfo("DEBUG: consistencyGroupEnforced = '" + consistencyGroupEnforced + "'");

    }

    @Override
    public void execute() {
  
    }

}
