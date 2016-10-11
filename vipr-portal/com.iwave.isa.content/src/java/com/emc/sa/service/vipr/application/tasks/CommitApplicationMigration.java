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
public class CommitApplicationMigration extends WaitForTasks<TaskResourceRep> {
    
    private URI mobilityGroupId;
    private boolean removeEnv;

    /**
     * @param mobilityGroupId
     * @param removeEnv
     */
    public CommitApplicationMigration(URI mobilityGroupId, boolean removeEnv) {
        super();
        this.mobilityGroupId = mobilityGroupId;
        this.removeEnv = removeEnv;
    }

    /* (non-Javadoc)
     * @see com.emc.sa.service.vipr.tasks.LongRunningTasks#doExecute()
     */
    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        MigrateApplicationParams param = new MigrateApplicationParams();
        param.setRemoveEnvironment(removeEnv);
        
        TaskList taskList = getClient().application().commitMigration(mobilityGroupId, param);
        
        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }

}
