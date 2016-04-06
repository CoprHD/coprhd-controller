/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class DeleteApplication extends ViPRExecutionTask<Void> {
    private final URI id;

    public DeleteApplication(URI id) {
        this.id = id;
        provideDetailArgs(id);
    }

    @Override
    public void execute() throws Exception {
        getClient().application().deleteApplication(id);
    }
}
