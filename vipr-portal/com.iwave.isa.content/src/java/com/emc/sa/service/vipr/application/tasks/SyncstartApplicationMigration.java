/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.vipr.client.Tasks;

/**
 * @author cgarber
 *
 */
public class SyncstartApplicationMigration extends WaitForTasks<TaskResourceRep> {
    
    private URI mobilityGroupId;

    /**
     * @param mobilityGroupId
     */
    public SyncstartApplicationMigration(URI mobilityGroupId) {
        super();
        this.mobilityGroupId = mobilityGroupId;
    }

    /* (non-Javadoc)
     * @see com.emc.sa.service.vipr.tasks.LongRunningTasks#doExecute()
     */
    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        TaskList taskList = getClient().application().syncstartMigration(mobilityGroupId);
        
        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }

}
