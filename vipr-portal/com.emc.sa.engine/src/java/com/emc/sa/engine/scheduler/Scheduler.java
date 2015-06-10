/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.engine.ExecutionEngineDispatcher;
import com.emc.storageos.db.client.model.uimodels.Order;

@Component
public class Scheduler implements Runnable {
    private static final Logger LOG = Logger.getLogger(Scheduler.class);
    private static final long ONE_MINUTE = 60000;

    private int threadCount = 5;
    private volatile Thread thread;
    private ExecutorService pool;
    @Autowired
    private SchedulerDataManager dataManager;
    @Autowired
    private ExecutionEngineDispatcher dispatcher;

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Runs the scheduler.
     */
    @Override
    public void run() {
        startProcessor();
        try {
            pollActiveWindows();
        }
        finally {
            stopProcessor();
        }
    }

    /**
     * Stops the scheduler.
     */
    public void stop() {
        Thread t = thread;
        thread = null;

        stopProcessor();
        if (t != null) {
            t.interrupt();
            try {
                t.join();
            }
            catch (InterruptedException e) {
                LOG.info("Interrupted while waiting for completion", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Determines if the scheduler is running.
     * 
     * @return true if the scheduler is running.
     */
    private boolean isRunning() {
        return thread != null;
    }

    /**
     * Starts the order processor.
     */
    protected void startProcessor() {
        thread = Thread.currentThread();

        pool = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            pool.execute(new OrderProcessor());
        }
    }

    /**
     * Stops processing orders and wait for processing to finish.
     */
    protected void stopProcessor() {
        thread = null;
        pool.shutdownNow();
        try {
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                LOG.warn("Failed to gracefully shutdown");
            }
        }
        catch (InterruptedException e) {
            LOG.info("Interrupted while waiting for stop", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Polls the active windows every minute until the scheduler is stopped.
     */
    protected void pollActiveWindows() {
        try {
            while (isRunning()) {
                long nextTime = System.currentTimeMillis() + ONE_MINUTE;

                dataManager.updateActiveWindows();

                long sleepTime = nextTime - System.currentTimeMillis();
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            }
        }
        catch (InterruptedException e) {
            LOG.info("Interrupted waiting for an execution window");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Runnable that handles processing orders.
     * 
     * @author jonnymiller
     */
    private class OrderProcessor implements Runnable {
        @Override
        public void run() {
            LOG.info("Started OrderProcessor");
            try {
                while (isRunning()) {
                    Order order = dataManager.lockNextScheduledOrder();
                    try {
                        processOrder(order);
                    }
                    finally {
                        dataManager.unlockOrder(order);
                    }
                }
            }
            catch (InterruptedException e) {
                LOG.info("Interruped waiting for an order");
                Thread.currentThread().interrupt();
            }
            LOG.info("Finished OrderProcessor");
        }

        protected void processOrder(Order order) {
            try {
                LOG.info("Processing scheduled order: " + order.getId());
                if (!isRunning()) {
                    LOG.info("Processing shutdown, terminating");
                    return;
                }
                dispatcher.processOrder(order);
            }
            catch (Exception e) {
                LOG.error("Unexpected exception processing order: " + order.getId(), e);
            }
        }
    }
}
