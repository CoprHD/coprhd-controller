/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import javax.inject.Inject;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Host;

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
        if (!models.actionableEvents().findPendingByAffectedResources(id).isEmpty()) {
            ExecutionUtils.fail("failTask.actionableEvents.precheck", new Object[] {}, new Object[] { id });
        }

        // for clusters, check all hosts in the cluster
        if (BlockStorageUtils.isCluster(id)) {
            for (Host host : models.hosts().findByCluster(id)) {
                if (!models.actionableEvents().findPendingByAffectedResources(host.getId()).isEmpty()) {
                    ExecutionUtils.fail("failTask.actionableEvents.precheck", new Object[] {}, new Object[] { host.getId() });

                }
            }
        }
        return null;
    }
}
