/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.NamedThreadPoolExecutor;

/**
 * ExecutorService container that allows us to perform background tasks in the API
 * so we can return to the caller faster.
 */
public class AsyncTaskExecutorService {

    // Number of threads in the controller bound thread pool. Fed from api-conf.xml
    private int _asyncTaskThreads;
    // Cache of threads in the pool
    public ExecutorService _workerThreads;

    final private Logger _logger = LoggerFactory.getLogger(AsyncTaskExecutorService.class);

    public void setAsyncTaskThreads(int n_threads) {
        _asyncTaskThreads = n_threads;
    }

    public int getAsyncTaskThreads() {
        return _asyncTaskThreads;
    }

    public void start() {
        _workerThreads = new NamedThreadPoolExecutor(AsyncTaskExecutorService.class.getSimpleName(), _asyncTaskThreads);
    }

    public ExecutorService getExecutorService() {
        return _workerThreads;
    }

    public void stop() {
        try {
            _workerThreads.shutdown();
            _workerThreads.awaitTermination(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            _logger.error("TimeOut occured after waiting Client Threads to finish");
        }
    }

}
