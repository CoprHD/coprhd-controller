/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.workflow;

import java.net.URI;
import java.util.Iterator;
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
import com.emc.storageos.db.client.model.WorkflowStepData;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
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
    public static final Long WORKFLOW_HOLDING_TIME_MSEC = 30L * DAYS_IN_MSEC; // 30 days

    public void start() {
        log.info("Starting WorkflowScrubber");
        _executor.scheduleWithFixedDelay(
                new Runnable() {
                    public void run() {
                        try {
                            deleteOldWorkflows();
                        } catch(Exception e) {
                            log.error("Exception thrown while scrubbing workflows");
                            log.error(e.getMessage(), e);
                        }
                    }
                }, 1, 24, TimeUnit.HOURS);
    }

    /**
     * Scan all the workflows, delete: 
     *     workflows older than WORKFLOW_HOLDING_TIME_MSEC 
     *     workflow steps associated with any workflow deleted
     *     workflowStepData associated with any workflow deleted 
     * Also finds and deletes: 
     *     orphaned workflow steps (steps without a valid workflow) 
     *     orphaned workflowStepData (without a workflow)
     */
    public void deleteOldWorkflows() {
        log.info("Scanning for old workflows to be deleted");
        List<URI> workflowURIs = dbClient.queryByType(Workflow.class, true);
        Iterator<Workflow> workflowItr = dbClient.queryIterativeObjects(Workflow.class, workflowURIs);
        Long currentTime = System.currentTimeMillis();
        int workflowCount = 0, workflowsDeletedCount = 0, stepsDeletedCount = 0, stepDataDeletedCount = 0;
        while (workflowItr.hasNext()) {
            workflowCount++;
            Workflow workflow = workflowItr.next();
            URI uri = workflow.getId();
            try {
                Long creationTime = (workflow.getCreationTime() == null) ? (currentTime - WORKFLOW_HOLDING_TIME_MSEC) : workflow.getCreationTime().getTimeInMillis();
                Long age = currentTime - creationTime;
                if (age >= WORKFLOW_HOLDING_TIME_MSEC) {
                    log.info("Processing workflow {} age (msec) {}", uri, age);

                    // Find all the WorkflowSteps for this Workflow, and them mark them for deletion.
                    URIQueryResultList stepURIs = new URIQueryResultList();
                    dbClient.queryByConstraint(ContainmentConstraint.Factory.getWorkflowWorkflowStepConstraint(uri), stepURIs);
                    Iterator<WorkflowStep> wfStepItr = dbClient.queryIterativeObjects(WorkflowStep.class, stepURIs);
                    while (wfStepItr.hasNext()) {
                        WorkflowStep step = wfStepItr.next();
                        URI stepURI = step.getId();
                        stepsDeletedCount++;
                        dbClient.removeObject(step);
                        log.info("Workflow step {} for workflow {} marked inactive", stepURI, uri);
                    }

                    // Find all the WorkflowStepData for this Workflow, and them mark them for deletion.
                    URIQueryResultList stepDataURIs = new URIQueryResultList();
                    dbClient.queryByConstraint(ContainmentConstraint.Factory.getWorkflowStepDataConstraint(uri), stepDataURIs);
                    Iterator<WorkflowStepData> wfStepDataItr = dbClient.queryIterativeObjects(WorkflowStepData.class, stepDataURIs);
                    while (wfStepDataItr.hasNext()) {
                        WorkflowStepData stepData = wfStepDataItr.next();
                        stepDataDeletedCount++;
                        dbClient.removeObject(stepData);
                        log.info("Workflow step data {} for workflow {} marked inactive", stepData.getId(), uri);
                    }

                    // Mark the workflow itself for deletion
                    if (!workflow.getInactive()) {
                        workflowsDeletedCount++;
                        dbClient.removeObject(workflow);
                        log.info("Workflow {} marked inactive", uri);
                    }
                }
            } catch (Exception ex) {
                log.error("Exception processing workflow: " + uri, ex);
            }
        }
        
        // now query workflow steps and clean up any orphaned steps
        Iterator<WorkflowStep> workflowStepItr = dbClient.queryIterativeObjects(WorkflowStep.class, dbClient.queryByType(WorkflowStep.class, true));
        while (workflowStepItr.hasNext()) {
            WorkflowStep step = workflowStepItr.next();
            if (NullColumnValueGetter.isNullURI(step.getWorkflowId())) {
                // step is orphaned -- delete it
                stepsDeletedCount++;
                dbClient.removeObject(step);
                log.info("Orphaned workflow step {} marked inactive", step.getId());
            } else {
                Workflow wf = dbClient.queryObject(Workflow.class, step.getWorkflowId());
                if (wf == null || wf.getInactive()) {
                    // step is orphaned -- delete it
                    stepsDeletedCount++;
                    dbClient.removeObject(step);
                    log.info("Orphaned workflow step {} marked inactive", step.getId());
                }
            }
        }

        // now query workflow step data and clean up any orphaned step data
        Iterator<WorkflowStepData> workflowStepDataItr = dbClient.queryIterativeObjects(WorkflowStepData.class,
                dbClient.queryByType(WorkflowStepData.class, true));
        while (workflowStepDataItr.hasNext()) {
            WorkflowStepData stepData = workflowStepDataItr.next();
            if (NullColumnValueGetter.isNullURI(stepData.getWorkflowId())) {
                // step data is orphaned -- delete it
                stepDataDeletedCount++;
                dbClient.removeObject(stepData);
                log.info("Orphaned workflow step data {} marked inactive", stepData.getId());
            } else {
                Workflow wf = dbClient.queryObject(Workflow.class, stepData.getWorkflowId());
                if (wf == null || wf.getInactive()) {
                    // step data is orphaned -- delete it
                    stepDataDeletedCount++;
                    dbClient.removeObject(stepData);
                    log.info("Orphaned workflow step data {} marked inactive", stepData.getId());
                }
            }
        }
        log.info(
                "Done scanning for old workflows; {} workflows analyzed; {} old workflows deleted; {} workflow steps deleted; {} workflow step data deleted",
                workflowCount, workflowsDeletedCount, stepsDeletedCount, stepDataDeletedCount);
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
}
