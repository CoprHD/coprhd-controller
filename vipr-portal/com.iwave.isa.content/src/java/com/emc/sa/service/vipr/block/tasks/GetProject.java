/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.project.ProjectRestRep;

public class GetProject extends ViPRExecutionTask<ProjectRestRep> {
    private URI projectId;

    public GetProject(URI projectId) {
        this.projectId = projectId;
        provideDetailArgs(projectId);
    }

    @Override
    public ProjectRestRep executeTask() throws Exception {
        return getClient().projects().get(projectId);
    }
}
