/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.emc.storageos.coordinator.client.service.impl.LeaderSelectorListenerImpl;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;

/**
 * LeaderSelectorListenerForPeriodicTask is an implementation of an adapter to translate the
 * LeaderSelectorListener interface into arbitrary Runnable interface.
 * Use this class if you have a Runnable object for a task to be run on a single node periodically.
 */
public class LeaderSelectorListenerForPeriodicTask extends LeaderSelectorListenerImpl {
    private static final Log _log = LogFactory.getLog(LeaderSelectorListenerForPeriodicTask.class);
    private boolean ignoreSuspend = false;

    List<ScheduledTask> tasks;
    volatile boolean started;
    private final ScheduledExecutorService scheduler;

    public LeaderSelectorListenerForPeriodicTask(Runnable worker, long initialDelay, long interval) {
        tasks = new ArrayList<>();
        tasks.add(new ScheduledTask(worker, initialDelay, interval));
        scheduler = new NamedScheduledThreadPoolExecutor(worker.getClass().getSimpleName(), 1);
        ((NamedScheduledThreadPoolExecutor) scheduler).setAppendTaskName(true);
        started = false;
    }

    public LeaderSelectorListenerForPeriodicTask(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        tasks = new ArrayList<>();
        started = false;
    }

    public LeaderSelectorListenerForPeriodicTask(ScheduledExecutorService scheduler, boolean ignoreSuspend) {
        this(scheduler);
        this.ignoreSuspend = ignoreSuspend;
    }

    public boolean addScheduledTask(Runnable worker, long initialDelay, long interval) {
        if (!started) {
            synchronized (this) {
                tasks.add(new ScheduledTask(worker, initialDelay, interval));
                return true;
            }
        }
        else {
            return false;
        }
    }

    protected void startLeadership() throws Exception {
        started = true;
        List<ScheduledTask> locTasks;
        synchronized (this) {
            locTasks = new ArrayList(tasks);
        }
        for (final ScheduledTask task : locTasks) {
            try {
                task.taskFuture = scheduler.scheduleAtFixedRate(new Runnable() {
                    public void run() {
                        try {
                            task.taskProcessor.run();
                        } catch (Exception e) {
                            _log.error("Failed to execute leader's tasks", e);
                        }
                    }
                }, task.delay, task.interval, TimeUnit.SECONDS);
            } catch (Exception e) {
                _log.error("StartLeadership()  failed", e);
            }
        }
    }

    protected void stopLeadership() {
        if (ignoreSuspend) {
            _log.info("### Got stopLeader but ingore it");
            return;
        }
        List<ScheduledTask> locTasks;
        synchronized (this) {
            locTasks = new ArrayList(tasks);
        }
        for (final ScheduledTask task : locTasks) {
            if (task.taskFuture != null) {
                task.taskFuture.cancel(true);
            }
        }
        started = false;
    }

    class ScheduledTask {
        final Runnable taskProcessor;
        final private long delay;
        final private long interval;

        private ScheduledFuture<?> taskFuture;

        ScheduledTask(Runnable taskProcessor, long delay, long interval) {
            this.taskProcessor = taskProcessor;
            this.delay = delay;
            this.interval = interval;
            this.taskFuture = null;
        }
    }
}
