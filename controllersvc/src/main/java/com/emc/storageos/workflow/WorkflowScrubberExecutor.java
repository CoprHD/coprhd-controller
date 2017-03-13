/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.workflow;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Workflow;
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;

/**
 * Marks Workflows and their WorkflowSteps inactive if older than WORKFLOW_HOLDING_TIME
 * (30 days).
 * (Modeled after TaskScrubberExecutor by David Maddison).
 * 
 * @author watson
 */
public class WorkflowScrubberExecutor {
    private static final Logger log = LoggerFactory.getLogger(WorkflowScrubberExecutor.class);
    private ScheduledExecutorService _executor = new NamedScheduledThreadPoolExecutor("WorkflowScrubber", 1);
    private DbClient dbClient;
    private static final Long DAYS_IN_MSEC = 24L * 3600 * 1000;
    private static final Long WORKFLOW_HOLDING_TIME_MSEC = 30L * DAYS_IN_MSEC; // 30 days

    public void start() {
        log.info("Starting WorkflowScrubber");
        _executor.scheduleWithFixedDelay(
                new Runnable() {
                    public void run() {
                        deleteOldWorkflows();
                    }
                }, 1, 24, TimeUnit.HOURS);
    }

    /**
     * Scan all the workflows, marking any completed workflows older than
     * WORKFLOW_HOLDING_TIME_MSEC as inactive.
     */
    public void deleteOldWorkflows() {
        log.info("Scanning for old workflows to be deleted");
        List<URI> workflowURIs = dbClient.queryByType(Workflow.class, true);
        Long currentTime = System.currentTimeMillis();
        for (URI uri : workflowURIs) {
            try {
                Workflow workflow = dbClient.queryObject(Workflow.class, uri);
                if (workflow == null) {
                    continue;
                }
                if (null == workflow.getCreationTime()) {
                    log.warn("workflow {} with null creation time will be deleted", workflow.getId());
                    dbClient.removeObject(workflow);
                }
                Long creationTime = workflow.getCreationTime().getTimeInMillis();
                Long age = currentTime - creationTime;
                if ((age) >= WORKFLOW_HOLDING_TIME_MSEC) {
                    log.info("Processing workflow {} age (msec) {}", uri, age);
                    // Find all the WorkflowSteps for this Workflow, and them mark them for deletion.
                    URIQueryResultList stepURIs = new URIQueryResultList();
                    dbClient.queryByConstraint(ContainmentConstraint.Factory.getWorkflowWorkflowStepConstraint(uri), stepURIs);
                    for (URI stepURI : stepURIs) {
                        WorkflowStep step = dbClient.queryObject(WorkflowStep.class, stepURI);
                        if (step == null) {
                            continue;
                        }
                        dbClient.markForDeletion(step);
                    }
                    // Mark the workflow itself for deletion
                    dbClient.markForDeletion(workflow);
                    log.info("Workflow {} marked inactive", uri);
                }
            } catch (Exception ex) {
                log.error("Exception processing workflow: " + uri, ex);
            }
        }
        log.info("Done scanning for old workflows");
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
}
