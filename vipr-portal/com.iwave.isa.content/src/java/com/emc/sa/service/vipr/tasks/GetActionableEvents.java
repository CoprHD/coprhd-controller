/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.util.List;

import javax.inject.Inject;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Host;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class GetActionableEvents extends ViPRExecutionTask<Void> {
    @Inject
    private ModelClient models;
    private DataObject resource;

    public GetActionableEvents(DataObject resource) {
        this.resource = resource;
        provideDetailArgs(resource.getId());
    }

    @Override
    public Void executeTask() throws Exception {
        List<ActionableEvent> events = models.actionableEvents().findPendingByAffectedResources(resource.getId());
        if (!events.isEmpty()) {
            ExecutionUtils.fail("failTask.actionableEvents.precheck", new Object[] {},
                    new Object[] { resource.forDisplay(), getEventOutput(events) });
        }

        // for clusters, check all hosts in the cluster
        if (BlockStorageUtils.isCluster(resource.getId())) {
            for (Host host : models.hosts().findByCluster(resource.getId())) {
                events = models.actionableEvents().findPendingByAffectedResources(host.getId());
                if (!events.isEmpty()) {
                    ExecutionUtils.fail("failTask.actionableEvents.precheck", new Object[] {},
                            new Object[] { host.forDisplay(), getEventOutput(events) });

                }
            }
        }
        return null;
    }

    /**
     * Get human readable output for events
     * 
     * @param events the list of events to get output for
     * @return event information
     */
    private String getEventOutput(List<ActionableEvent> events) {
        List<String> result = Lists.newArrayList();
        for (ActionableEvent event : events) {
            result.add(event.getResource().getName() + ": " + event.forDisplay());
        }
        return Joiner.on("\n").join(result);
    }
}
