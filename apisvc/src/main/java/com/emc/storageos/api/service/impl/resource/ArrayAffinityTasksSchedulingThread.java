/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Background thread that schedule array affinity tasks.
 */
class ArrayAffinityTasksSchedulingThread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ArrayAffinityTasksSchedulingThread.class);
    private static int MAX_WAIT_MINUTES_FOR_HOST_DISCOVERY = 20;
    private static int SLEEP_MINUTES = 1;

    private final HostService service;
    private URI hostId;
    private String task;
    private DbClient dbClient;

    /**
     * @param service
     * @param hostId
     * @param task
     * @param dbClient
     */
    public ArrayAffinityTasksSchedulingThread(HostService service, URI hostId, String task, DbClient dbClient) {
        this.service = service;
        this.hostId = hostId;
        this.task = task;
        this.dbClient = dbClient;
    }

    @Override
    public void run() {
        logger.info("Starting scheduling thread...");

        try {
            // wait for host discovery completion
            waitForTaskCompletion(hostId, task, dbClient);
            service.scheduleHostArrayAffinityTasks(hostId, task);

        } catch (Exception e) {
            logger.error("Failed to schedule array affinity tasks for host " + hostId, e);
        }
        logger.info("Ending scheduling thread...");
    }

    /**
     * Static method to schedule array affinity tasks in background
     *
     * @param service host service
     * @param executorService executor service that manages the thread pool
     * @param hostId URI of the host
     * @param task task ID
     * @param dbClient db client
     */
    public static void scheduleArrayAffinityTasks(HostService service, ExecutorService executorService, URI hostId, String task, DbClient dbClient) {
        ArrayAffinityTasksSchedulingThread schedulingThread = new ArrayAffinityTasksSchedulingThread(service, hostId, task, dbClient);
        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            logger.error("Failed to schedule array affinity tasks for host " + hostId, e.getMessage());
        }
    }

    private void waitForTaskCompletion(URI resourceId, String task, DbClient dbClient) throws APIException {
        if (NullColumnValueGetter.isNullURI(resourceId) || StringUtils.isEmpty(task) || dbClient == null) {
            return;
        }

        int waitMinutes = 0;
        while (true) {
            Task taskObj = TaskUtils.findTaskForRequestId(dbClient, resourceId, task);
            if (taskObj == null || taskObj.getInactive()) {
                return;
            }

            String status = taskObj.getStatus();
            if (Task.Status.ready.name().equals(status) || Task.Status.error.name().equals(status)) {
                return;
            }

            if (waitMinutes > MAX_WAIT_MINUTES_FOR_HOST_DISCOVERY) {
                throw APIException.badRequests.arrayAffinityTaskNotExecutedWithUnfinishedHostDiscovery(MAX_WAIT_MINUTES_FOR_HOST_DISCOVERY);
            }

            try {
                logger.info("Waiting host discovery task {} to finish", task);
                Thread.sleep(SLEEP_MINUTES * 60000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            waitMinutes += SLEEP_MINUTES;
        }
    }
}
