/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.task;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import com.google.common.collect.Lists;
import com.netflix.astyanax.util.TimeUUIDUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Deletes completed tasks that have completed and are over {@link #TASK_TTL_MINS_PROPERTY} old at periodic intervals
 */
public class TaskScrubberExecutor {
    private static final Logger log = LoggerFactory.getLogger(TaskScrubberExecutor.class);
    private static final String TASK_TTL_MINS_PROPERTY = "task_ttl";
    private static final String TASK_CLEAN_INTERVAL_PROPERTY = "task_clean_interval";

    private final static long MIN_TO_MICROSECS = 60 * 1000 * 1000;
    private final static long MINI_TO_MICROSECS = 1000;
    private final static long MINIMUM_PERIOD_MINS = 60;
    private final static int DELETE_BATCH_SIZE = 100;

    private ScheduledExecutorService _executor = new NamedScheduledThreadPoolExecutor("TaskScrubber", 1);
    private DbClient dbClient;
    private CoordinatorClient coordinator;

    public void start() {
        _executor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                deleteOldTasks();
            }
        }, 1, getConfigProperty(TASK_CLEAN_INTERVAL_PROPERTY, MINIMUM_PERIOD_MINS), TimeUnit.MINUTES);
        log.info("Started Task Scrubber");
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    private void deleteOldTasks() {
        log.info("Looking for completed tasks older than {} minutes", getConfigProperty(TASK_TTL_MINS_PROPERTY, MINIMUM_PERIOD_MINS));

        long taskLifetimeMicroSeconds = getConfigProperty(TASK_TTL_MINS_PROPERTY, MINIMUM_PERIOD_MINS) * MIN_TO_MICROSECS;
        long currentTimeMicroseconds = TimeUUIDUtils.getMicrosTimeFromUUID(TimeUUIDUtils.getUniqueTimeUUIDinMicros());
        long startTimeMicroSec = currentTimeMicroseconds - taskLifetimeMicroSeconds;
        Calendar startTimeMarker = Calendar.getInstance();
        startTimeMarker.setTimeInMillis(startTimeMicroSec/MINI_TO_MICROSECS);

        List<URI> ids = dbClient.queryByType(Task.class, true);
        List<Task> toBeDeleted = Lists.newArrayList();
        for (URI id : ids) {
            Task task = dbClient.queryObject(Task.class, id);
            
            if (task.getCreationTime().after(startTimeMarker)) {
            	continue;
            }
            if (task != null && !task.isPending()) {
                toBeDeleted.add(task);
            }
            if (toBeDeleted.size() >= DELETE_BATCH_SIZE) {
            	log.info("Deleting {} Tasks", toBeDeleted.size());
            	dbClient.markForDeletion(toBeDeleted);
            	toBeDeleted.clear();
            }
        }

        if (!toBeDeleted.isEmpty()) {
            log.info("Deleting {} Tasks", toBeDeleted.size());

            dbClient.markForDeletion(toBeDeleted);
        } 
        log.info("delete completed tasks successfully");
    }

    private long getConfigProperty(String propertyName, long minimumValue) {
        String value = coordinator.getPropertyInfo().getProperty(propertyName);

        if (value != null && StringUtils.isNotBlank(value)) {
            try {
                return Math.max(Long.valueOf(value), minimumValue);
            } catch (Exception e) {
                log.error("Configuration property " + propertyName + " invalid number, using minimum value of " + minimumValue, e);
                return minimumValue;
            }
        } else {
            log.error("Configuration property " + propertyName + " not found, using minimum value of " + minimumValue);
            return minimumValue;
        }
    }
}
