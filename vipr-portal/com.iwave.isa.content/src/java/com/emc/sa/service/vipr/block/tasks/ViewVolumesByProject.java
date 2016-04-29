package com.emc.sa.service.vipr.block.tasks;


import java.util.ArrayList;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.impl.RestClient;


/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */

public class ViewVolumesByProject extends WaitForTasks<String> {
	private  String name;
	private  RestClient client;
	public ViewVolumesByProject(String name, String description) {
        this.name = name;
        provideDetailArgs(name);
    }

    @Override
    protected Tasks<String> doExecute() throws Exception {
    	List<TaskResourceRep> listTask = new ArrayList<>();
    	TaskResourceRep task1 = new TaskResourceRep();
    	task1.setName(name);
    	listTask.add(task1);    	
    	return new Tasks<String>(client, listTask, String.class);
    }
}