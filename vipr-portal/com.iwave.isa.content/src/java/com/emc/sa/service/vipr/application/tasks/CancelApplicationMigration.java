/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.ApplicationMigrationParam;
import com.emc.vipr.client.Tasks;

/**
 * @author cgarber
 *
 */
public class CancelApplicationMigration extends WaitForTasks<TaskResourceRep> {
    
    private URI mobilityGroupId;
    private boolean removeEnv;

    /**
     * @param mobilityGroupId
     * @param removeEnv
     */
    public CancelApplicationMigration(URI mobilityGroupId, boolean removeEnv) {
        super();
        this.mobilityGroupId = mobilityGroupId;
        this.removeEnv = removeEnv;
    }

    /* (non-Javadoc)
     * @see com.emc.sa.service.vipr.tasks.LongRunningTasks#doExecute()
     */
    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        ApplicationMigrationParam param = new ApplicationMigrationParam();
        param.setRemoveEnvironment(removeEnv);
        
        TaskList taskList = getClient().application().cancelMigration(mobilityGroupId, param);
        
        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }

}
