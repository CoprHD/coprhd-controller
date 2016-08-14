/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;
import java.util.List;

import javax.inject.Inject;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.model.ActionableEvent;

public class GetActionableEvents extends ViPRExecutionTask<Void> {
    @Inject
    private ModelClient models;
    private URI id;

    public GetActionableEvents(URI id) {
        this.id = id;
        provideDetailArgs(id);
    }

    @Override
    public Void executeTask() throws Exception {
        List<ActionableEvent> events = models.actionableEvents().findPendingByResource(id);
        if (!events.isEmpty()) {
            ExecutionUtils.fail("failTask.actionableEvents.precheck", new Object[] {}, new Object[] { id });
        }
        return null;
    }
}
