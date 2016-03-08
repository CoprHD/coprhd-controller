/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrPostFailoverHandler;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.DbCheckerFileWriter;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.DbConsistencyChecker;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.Workflow;
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.workflow.Workflow.StepState;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;

/**
 * Post cleanup after failing over to a standby site. For unplanned disaster on active site, 
 * some db/zk data may not be replicated to standby site and we may lose some data after failover. So we
 * need do the following before we announce standby is ready
 * 
 * 1) Db scan. We need check db index/object CF inconsistencies
 * 2) Release persistent lock
 * 3) Remove all pending tasks, workflows
 * 4) Set all in-progress workflow steps/workflow as error
 * 5) Trigger device rediscovery
 * 
 */
public class ControllerWorkflowCleanupHandler extends DrPostFailoverHandler {
    private static final Logger log = LoggerFactory.getLogger(ControllerWorkflowCleanupHandler.class);
    
    @Autowired
    private DbClient dbClient;
    
    public ControllerWorkflowCleanupHandler() {
    }
    
    @Override
    protected void execute() {
        checkPersistentLocks();
        cleanupWorkflow();
        cleanupTasks();
        rediscoverDevices();
    }
    
    private void checkPersistentLocks() {
        CoordinatorClient coordinator = getCoordinator();
        coordinator.deletePath(ZkPath.PERSISTENTLOCK.toString());
        log.info("Released all persistent locks");
    }
    
    private void cleanupWorkflow() {
        log.info("Start workflow cleanup");
        List<URI> workflowIds = dbClient.queryByType(Workflow.class, true);
        Iterator<Workflow> workflows = dbClient.queryIterativeObjects(Workflow.class, workflowIds);
        int cnt = 0;
        while(workflows.hasNext()) {
            Workflow workflow = workflows.next();
            if (!workflow.getCompleted()) {
                completeWorkflow(workflow.getId());
                cnt ++;
            }
        }
        log.info("Total {} workflows processed", cnt);
    }
    
    private void completeWorkflow(URI workflowId) {
        URIQueryResultList stepURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getWorkflowWorkflowStepConstraint(workflowId), stepURIs);
        for (URI stepURI : stepURIs) {
            WorkflowStep step = dbClient.queryObject(WorkflowStep.class, stepURI);
            String state = step.getState();
            List<String> activeStepStates = Arrays.asList(StepState.CREATED.toString(), StepState.BLOCKED.toString(), StepState.QUEUED.toString(), StepState.EXECUTING.toString());
            if (activeStepStates.contains(state)) {
                WorkflowException ex = WorkflowException.exceptions.workflowTerminatedForFailover(workflowId.toString());
                log.info("Terminate workflow step {}", step.getId());
                WorkflowService.completerStepErrorWithoutRollback(step.getStepId(), ex);
            }
        }
    }
    
    private void cleanupTasks() {
        log.info("Start task cleanup");
        List<URI> taskIds = dbClient.queryByType(Task.class, true);
        Iterator<Task> tasks = dbClient.queryIterativeObjects(Task.class, taskIds);
        int cnt = 0;
        while(tasks.hasNext()) {
            Task task = tasks.next();
            if (task.isPending() || task.isQueued()) {
                DeviceControllerException ex = DeviceControllerException.exceptions.terminatedForControllerFailover();
                task.setServiceCode(ex.getServiceCode().getCode());
                task.setStatus(String.valueOf(Task.Status.error));
                task.setMessage(ex.getMessage());
                task.setProgress(100);
                task.setEndTime(Calendar.getInstance());
                log.info("Terminate task {}", task.getId());
                dbClient.updateObject(task);
                cnt ++;
            }
        }
        log.info("Total {} tasks processed", cnt);
    }
    
    private void rediscoverDevices(){
        List<URI> storageSystemIds = dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> storageSystems = dbClient.queryIterativeObjects(StorageSystem.class, storageSystemIds);
        String taskId = UUID.randomUUID().toString();
        while(storageSystems.hasNext()) {
            StorageSystem storageSystem = storageSystems.next();
            URI storageSystemId = storageSystem.getId();
            try {
                log.info("Start discovery {}", storageSystemId);
                ControllerServiceImpl.scheduleDiscoverJobs(
                        new AsyncTask[] { new AsyncTask(StorageSystem.class, storageSystemId, taskId) },
                        Lock.DISCOVER_COLLECTION_LOCK, ControllerServiceImpl.DISCOVERY);
            } catch (Exception ex) {
                log.error("Failed to start discovery : " + storageSystem.getId(), ex);
            }
        }
    }
    
    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    
}
