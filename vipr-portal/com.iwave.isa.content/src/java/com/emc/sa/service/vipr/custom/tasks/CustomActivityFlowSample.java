/*
DataObjectRestRep * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.custom.tasks;

import java.net.URI;
import java.util.Calendar;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.custom.CustomDataObjectRestRep;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.storageos.model.tasks.TasksList;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

public class CustomActivityFlowSample extends WaitForTask<CustomDataObjectRestRep> {
    private String name;

    public CustomActivityFlowSample(String vpoolId, String varrayId, String projectId, String size, Integer count,
            String name, String consistencyGroupId) {
        this(name);
    }

    public CustomActivityFlowSample(String name) {
       this.name = name;
    }

    @Override
    public Task<CustomDataObjectRestRep> doExecute() throws Exception {
        Task<CustomDataObjectRestRep> task = createWorkflow();
        return task;
    }
    
    public Task<CustomDataObjectRestRep> createWorkflow() {
    	CustomDataObjectRestRep data = new CustomDataObjectRestRep();
    	data.setCreationTime(Calendar.getInstance());
        return getClient().invokeWorkflow().create("/opt/storageos/simpleApprovalProcess.bpmn20.xml");
    } 
    
}
