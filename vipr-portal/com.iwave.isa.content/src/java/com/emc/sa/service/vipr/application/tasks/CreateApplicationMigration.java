/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.MigrateApplicationParams;
import com.emc.vipr.client.Tasks;

/**
 * @author cgarber
 *
 */
public class CreateApplicationMigration extends WaitForTasks<TaskResourceRep> {
    
    private URI mobilityGroupId;
    private URI targetVarray;
    private URI targetVpool;

    /**
     * @param mobilityGroupId
     * @param targetVarray
     * @param targetVpool
     */
    public CreateApplicationMigration(URI mobilityGroupId, URI targetVarray, URI targetVpool) {
        super();
        this.mobilityGroupId = mobilityGroupId;
        this.targetVarray = targetVarray;
        this.targetVpool = targetVpool;
    }

    /* (non-Javadoc)
     * @see com.emc.sa.service.vipr.tasks.LongRunningTasks#doExecute()
     */
    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        MigrateApplicationParams param = new MigrateApplicationParams();
        param.setTargetVirtualArray(targetVarray);
        param.setTargetVirtualPool(targetVpool);
        
        TaskList taskList = getClient().application().createMigration(mobilityGroupId, param);
        
        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }

}
