/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.common.DbDependencyPurger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class PurgeRunnable<T extends DataObject> implements Runnable {

    private static final Logger _log = LoggerFactory.getLogger(PurgeRunnable.class);

    private DbDependencyPurger _dbPurger;
    private DbClient _dbClient;
    private T _decommissionResouce;
    private int _max_retries;
    private ScheduledExecutorService _service;
    private String _taskId;
    private int _timeout;

    // volatile int should be sufficient
    // since it is guaranteed that multiple threads won't access it at the same time.
    private volatile int _iteration = 1;

    PurgeRunnable(DbClient dbClient, DbDependencyPurger purger,
            ScheduledExecutorService executorService,
            T resource, int maxIter, String taskId, int timeout) {
        _dbClient = dbClient;
        _dbPurger = purger;
        _service = executorService;
        _decommissionResouce = resource;
        _max_retries = maxIter;
        _taskId = taskId;
        _timeout = timeout;
    }

    public void run() {
        try {
            _dbPurger.purge(_decommissionResouce.getId(), _decommissionResouce.getClass());

            _log.info(String.format("Purged the database successfully with %d iterations: resoruce %s",
                    _iteration, _decommissionResouce.getId()));
            Operation upd = new Operation(Operation.Status.ready.toString(),
                    String.format("Purged the resources %s from the Database", _decommissionResouce.getId()));
            _dbClient.updateTaskOpStatus(_decommissionResouce.getClass(), _decommissionResouce.getId(), _taskId, upd);

        } catch (Exception ex) {
            _log.error(String.format("Failed to purge the database: resoruce %s, attempt #%d,",
                    _decommissionResouce.getId(), _iteration), ex);
            if (_iteration == _max_retries) {
                Operation op = new Operation(Operation.Status.error.toString(),
                        String.format("Failed to remove resource %s from the Database", _decommissionResouce.getId()));
                _dbClient.updateTaskOpStatus(_decommissionResouce.getClass(), _decommissionResouce.getId(), _taskId, op);
            } else {
                try {
                    _iteration++;
                    _service.schedule(this, _timeout, TimeUnit.SECONDS);
                } catch (Exception e) {
                    _log.error(String.format("Failed to reschedule removal of the resource %s from the database",
                            _decommissionResouce.getId()), e);
                    Operation op = new Operation(Operation.Status.error.toString(),
                            String.format("Failed to reschedule removal of the resource %s rom the Database", _decommissionResouce.getId()));
                    _dbClient.updateTaskOpStatus(_decommissionResouce.getClass(), _decommissionResouce.getId(), _taskId, op);

                }
            }
        }
    }

    /**
     * Execute Database purging for the given resource by using provided Executor service.
     * If the first attempt to purge fails, the method would attempt to run the task maxIter
     * number of times with "timeout" delay between consequent attempts.
     * The provided "purger" should find all the children of the resource and deactivate them
     * in the database.
     * 
     * @param dbClient
     * @param purger
     * @param executorService
     * @param resource
     * @param maxIter
     * @param taskId
     * @param timeout
     * @param <T>
     */

    public static <T extends DataObject> void executePurging(DbClient dbClient,
            DbDependencyPurger purger,
            ScheduledExecutorService executorService,
            T resource,
            int maxIter,
            String taskId,
            int timeout) {

        PurgeRunnable<T> purgeRunner = new PurgeRunnable<T>(dbClient, purger,
                executorService, resource,
                maxIter, taskId, timeout);
        try {
            executorService.execute(purgeRunner);
        } catch (Exception e) {
            _log.error(String.format("Failed to reschedule removal of the resource %s from the database",
                    resource.getId()), e);
            Operation op = new Operation(Operation.Status.error.toString(),
                    String.format("Failed to schedule removal of the resource %s rom the Database",
                            resource.getId()));
            dbClient.updateTaskOpStatus(resource.getClass(), resource.getId(), taskId, op);
        }
    }
}
