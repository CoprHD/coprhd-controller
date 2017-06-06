/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.event;

import java.net.URI;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import com.google.common.collect.Lists;
import com.netflix.astyanax.util.TimeUUIDUtils;

/**
 * Deletes events that are over {@link #EVENT_TTL_MINS_PROPERTY} old at periodic intervals
 */
public class ActionableEventScrubberExecutor {
    private static final Logger log = LoggerFactory.getLogger(ActionableEventScrubberExecutor.class);
    private static final String EVENT_TTL_MINS_PROPERTY = "event_ttl";
    private static final String EVENT_CLEAN_INTERVAL_PROPERTY = "event_clean_interval";

    private final static long MIN_TO_MICROSECS = 60 * 1000 * 1000;
    private final static long MINI_TO_MICROSECS = 1000;
    private final static long MINIMUM_PERIOD_MINS = 60;
    private final static int DELETE_BATCH_SIZE = 100;

    private ScheduledExecutorService _executor = new NamedScheduledThreadPoolExecutor("ActionableEventScrubber", 1);
    private DbClient dbClient;
    private CoordinatorClient coordinator;

    public void start() {
        _executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                deleteOldEvents();
            }
        }, 1, getConfigProperty(EVENT_CLEAN_INTERVAL_PROPERTY, MINIMUM_PERIOD_MINS), TimeUnit.MINUTES);
        log.info("Started Actionable Event Scrubber");
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

    private void deleteOldEvents() {
        log.info("Looking for completed events older than {} minutes", getConfigProperty(EVENT_TTL_MINS_PROPERTY, MINIMUM_PERIOD_MINS));

        long eventLifetimeMicroSeconds = getConfigProperty(EVENT_TTL_MINS_PROPERTY, MINIMUM_PERIOD_MINS) * MIN_TO_MICROSECS;
        long currentTimeMicroseconds = TimeUUIDUtils.getMicrosTimeFromUUID(TimeUUIDUtils.getUniqueTimeUUIDinMicros());
        long startTimeMicroSec = currentTimeMicroseconds - eventLifetimeMicroSeconds;
        Calendar startTimeMarker = Calendar.getInstance();
        startTimeMarker.setTimeInMillis(startTimeMicroSec / MINI_TO_MICROSECS);

        List<URI> ids = dbClient.queryByType(ActionableEvent.class, true);
        Iterator<ActionableEvent> events = dbClient.queryIterativeObjects(ActionableEvent.class, ids, true);
        List<ActionableEvent> toBeDeleted = Lists.newArrayList();
        while (events.hasNext()) {
            ActionableEvent event = events.next();
            if (event.getCreationTime().after(startTimeMarker)) {
                continue;
            }
            if (event != null && !ActionableEvent.Status.pending.name().equalsIgnoreCase(event.getEventStatus())
                    && !ActionableEvent.Status.failed.name().equalsIgnoreCase(event.getEventStatus())) {
                toBeDeleted.add(event);
            }
            if (toBeDeleted.size() >= DELETE_BATCH_SIZE) {
                log.info("Deleting {} Actionable Events", toBeDeleted.size());
                dbClient.markForDeletion(toBeDeleted);
                toBeDeleted.clear();
            }
        }

        if (!toBeDeleted.isEmpty()) {
            log.info("Deleting {} Actionable Events", toBeDeleted.size());

            dbClient.markForDeletion(toBeDeleted);
        }
        log.info("delete completed actionable events successfully");
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
