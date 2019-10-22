/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.task;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.constraint.AggregationQueryResultList;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import com.google.common.collect.Lists;
import com.netflix.astyanax.util.TimeUUIDUtils;

/**
 * Deletes completed tasks that have completed and are over {@link #TASK_TTL_MINS_PROPERTY} old at periodic intervals
 */
public class TaskScrubberExecutor {
    private static final Logger log = LoggerFactory.getLogger(TaskScrubberExecutor.class);
    private static final String TASK_TTL_MINS_PROPERTY = "task_ttl";
    private static final String TASK_CLEAN_INTERVAL_PROPERTY = "task_clean_interval";
    private static final int TASK_CLEAN_INITIAL_DELAY = 10;

    private final static long MIN_TO_MICROSECS = 60 * 1000 * 1000;
    private final static long MINI_TO_MICROSECS = 1000;
    private final static long MINIMUM_PERIOD_MINS = 60;
    private final static int DELETE_BATCH_SIZE = 100;
    private final static int MAXIMUM_TASK_TO_DELETE = 100 * DELETE_BATCH_SIZE;
    private final static int MAXIMUM_TASK_IN_ONE_QUERY = 100 * DELETE_BATCH_SIZE;
    private static final String SYSTEM_TENANT_ID = "urn:storageos:TenantOrg:system:";
    
    private final static String TASK_SCRUBBER_LOCK = "task_scrubber_lock";
    private static final int TASK_SCRUBBER_LOCK_TIMEOUT = 3 * 60; // 3 minute timeout to acquire the lock

    private ScheduledExecutorService _executor = new NamedScheduledThreadPoolExecutor("TaskScrubber", 1);
    private DbClient dbClient;
    private CoordinatorClient coordinator;
    private InterProcessLock lock = null;

    public void start() {
        _executor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                try {
                    deleteOldTasks();
                } catch(Exception e) {
                    log.error("Exception thrown while running task scrubber executor");
                    log.error(e.getMessage(), e);
                }
            }
        }, TASK_CLEAN_INITIAL_DELAY, getConfigProperty(TASK_CLEAN_INTERVAL_PROPERTY, MINIMUM_PERIOD_MINS), TimeUnit.MINUTES);
        log.info("Started Task Scrubber to run every {} minutes", getConfigProperty(TASK_CLEAN_INTERVAL_PROPERTY, MINIMUM_PERIOD_MINS));
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
        boolean lockAcquired = false;
        try {
            
            // acquire a lock so clean up is only done on one node
            lockAcquired = acquireLock();
            
            if (lockAcquired) {
                log.info("Looking for completed tasks older than {} minutes", getConfigProperty(TASK_TTL_MINS_PROPERTY, MINIMUM_PERIOD_MINS));
                
                long taskLifetimeMicroSeconds = getConfigProperty(TASK_TTL_MINS_PROPERTY, MINIMUM_PERIOD_MINS) * MIN_TO_MICROSECS;
                long currentTimeMicroseconds = TimeUUIDUtils.getMicrosTimeFromUUID(TimeUUIDUtils.getUniqueTimeUUIDinMicros());
                long startTimeMicroSec = currentTimeMicroseconds - taskLifetimeMicroSeconds;
                Calendar startTimeMarker = Calendar.getInstance();
                startTimeMarker.setTimeInMillis(startTimeMicroSec/MINI_TO_MICROSECS);
                
                int tasksDeleted = 0;
                
                List<URI> tenantIds = getTenantIds();
                
                for (URI tenantId : tenantIds) {
                    tasksDeleted += deleteOldCompletedTasksForTenant(tenantId, startTimeMarker);
                    if (tasksDeleted >= MAXIMUM_TASK_TO_DELETE) {
                        break;
                    }
                }
        
                log.info("delete completed tasks successfully; deleted {} tasks", tasksDeleted);
            }
        } finally {
            if (lockAcquired) {
                releaseLock();
            }
        }
    }
    
    /**
     * get the list of all tenant ids
     * @return
     */
    private List<URI> getTenantIds() {
        log.debug("getting a list of all tenants");
        List<URI> tenantIds = new ArrayList<URI>();
        Iterator<URI> tenantItr = dbClient.queryByType(TenantOrg.class, true).iterator();
        while (tenantItr.hasNext()) {
            URI tenantId = tenantItr.next();
            tenantIds.add(tenantId);
        }
        tenantIds.add(URI.create(SYSTEM_TENANT_ID));
        log.debug("found {} tenants (including system tenant)", tenantIds.size());
        return tenantIds;
    }
    
    /**
     * deletes old completed tasks for a tenant
     * @param tenantId tenant to delete tasks for
     * @param startTimeMarker time that determines which tasks are old
     * @return the number of tasks deleted
     */
    private int deleteOldCompletedTasksForTenant(URI tenantId, Calendar startTimeMarker) {
        log.debug("deleting completed tasks for tenant {}", tenantId);
        int tasksDeleted = 0;
        Map<String, List<URI>> batchedIds = findTasksForTenantNotPending(tenantId);
        for (Entry<String, List<URI>> entry : batchedIds.entrySet()) {
            String batch = entry.getKey();
            List<URI> ids = entry.getValue();
            log.debug("processing batch {} with {} completed tasks for tenant {}", batch, ids.size(), tenantId);
            try {
                Iterator<Task> tasks = dbClient.queryIterativeObjects(Task.class, ids, true);
                List<Task> toBeDeleted = Lists.newArrayList();
                while (tasks.hasNext()) {
                    Task task = tasks.next();
                    if (task == null || (task.getCreationTime() != null && task.getCreationTime().after(startTimeMarker))) {
                        continue;
                    }
                    if (!task.isPending() && !task.isSuspendedNoError()) {
                        tasksDeleted++;
                        toBeDeleted.add(task);
                    }
                    if (toBeDeleted.size() >= DELETE_BATCH_SIZE) {
                        removeTasks(toBeDeleted, tenantId);
                        toBeDeleted.clear();
                    }
                    
                    // Don't delete more than MAXIMUM_TASK_TO_DELETE tasks in one iteration
                    // Prior to 3.6, a bug may have caused old completed tasks to not be cleaned up
                    // Having a maximum number of tasks to delete per iteration will prevent the 
                    // system from being overloaded while catching up; we want to avoid customers
                    // experiencing poor performance just after upgrade to 3.6 because they may
                    // have many tasks that now can be cleaned up
                    if (tasksDeleted >= MAXIMUM_TASK_TO_DELETE) {
                        break;
                    }
                }
    
                if (!toBeDeleted.isEmpty()) {
                    removeTasks(toBeDeleted, tenantId);
                } 
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        
        log.debug("done deleting completed tasks for tenant {}", tenantId);
        return tasksDeleted;
    }
    
    /**
     * remove tasks from db with logging
     * @param toBeDeleted
     * @param tenantId
     */
    private void removeTasks(List<Task> toBeDeleted, URI tenantId) {
        log.info("Deleting {} Tasks for tenant {}", toBeDeleted.size(), tenantId);
        dbClient.removeObject(toBeDeleted.toArray(new DataObject[toBeDeleted.size()]));
    }

    /**
     * returns non-pending task ids for a tenant
     * 
     * @param dbClient
     * @return a map of task ids in buckets of 10k each
     */
    private Map<String, List<URI>> findTasksForTenantNotPending(URI tenantId) {
        log.debug("searching for completed tasks for tenant {}", tenantId);
        Constraint constraint = AggregatedConstraint.Factory.getAggregationConstraint(Task.class, "tenant",
                tenantId.toString(), "taskStatus");
        AggregationQueryResultList queryResults = new AggregationQueryResultList();
        dbClient.queryByConstraint(constraint, queryResults);
        Iterator<AggregationQueryResultList.AggregatedEntry> it = queryResults.iterator();
        Map<String, List<URI>> notPendingTasks = new HashMap<String, List<URI>>();
        int batch = 0;
        int count = 0;
        int totalCount = 0; // only used for logging
        while (it.hasNext()) {
            AggregationQueryResultList.AggregatedEntry entry = it.next();
            if (!entry.getValue().equals(Task.Status.pending.name())) {
                if (notPendingTasks.get(Integer.toString(batch)) == null) {
                    notPendingTasks.put(Integer.toString(batch), new ArrayList<URI>());
                }
                notPendingTasks.get(Integer.toString(batch)).add(entry.getId());
                totalCount++;
                count++;
                if (count >= MAXIMUM_TASK_IN_ONE_QUERY) {
                    batch++;
                    count = 0;
                }
            }
        }
        log.debug("found {} completed tasks for tenant {}", totalCount, tenantId);
        return notPendingTasks;
    }
    
    /**
     * Acquire a distrubuted lock to ensure no other node will perform task scrubbing concurrently
     *  wait up to 30 seconds to acquire the lock
     * 
     * @return true if the lock was successfully acquired
     */
    private boolean acquireLock() {
        boolean acquired = false;
        try {
            log.info("Attempting to acquire distributed lock {}", TASK_SCRUBBER_LOCK);
            lock = coordinator.getLock(TASK_SCRUBBER_LOCK);
            acquired = lock.acquire(TASK_SCRUBBER_LOCK_TIMEOUT, TimeUnit.SECONDS);
            if (acquired) {
                log.info("Successfully acquired distributed lock {}", TASK_SCRUBBER_LOCK);
            } else {
                log.info("Failed to acquire distributed lock {}", TASK_SCRUBBER_LOCK);
            }
        } catch (Exception e) {
            log.info("Exception while attempting to acquire distributed lock {}", TASK_SCRUBBER_LOCK);
            log.error(e.getMessage(), e);
        }
        return acquired;
    }
    
    /**
     * release the distributed task scrubber lock
     */
    private void releaseLock() {
        log.info("Attempting to release distributed lock {}", TASK_SCRUBBER_LOCK);
        try {
            lock.release();
            log.info("Successfully released distributed lock {}", TASK_SCRUBBER_LOCK);
        } catch (Exception e) {
            log.error("Failed to release distributed lock {}", TASK_SCRUBBER_LOCK);
            log.error(e.getMessage(), e);
        }
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
