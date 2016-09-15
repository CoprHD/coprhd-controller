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
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.Host;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

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
        List<ActionableEvent> events = models.actionableEvents().findPendingByAffectedResources(id);
        if (!events.isEmpty()) {
            ExecutionUtils.fail("failTask.actionableEvents.precheck", new Object[] {}, new Object[] { id, getEventOutput(events) });
        }

        // for clusters, check all hosts in the cluster
        if (BlockStorageUtils.isCluster(id)) {
            for (Host host : models.hosts().findByCluster(id)) {
                events = models.actionableEvents().findPendingByAffectedResources(host.getId());
                if (!events.isEmpty()) {
                    ExecutionUtils.fail("failTask.actionableEvents.precheck", new Object[] {},
                            new Object[] { host.getId(), getEventOutput(events) });

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
            result.add(event.forDisplay());
        }
        return Joiner.on(",").join(result);
    }
}
