/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.migration.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class DeleteMobilityGroup extends ViPRExecutionTask<Void> {
    private final URI id;

    public DeleteMobilityGroup(URI id) {
        this.id = id;
        provideDetailArgs(id);
    }

    @Override
    public void execute() throws Exception {
        getClient().application().deleteApplication(id);
    }
}