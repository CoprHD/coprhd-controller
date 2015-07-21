/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;


public class AsynchJobExecutorService {

    private int  _asynchJobThreads;
    public ScheduledExecutorService _workerThreads;


    final private Logger _logger = LoggerFactory.getLogger(AsynchJobExecutorService.class);



    public void setAsynchJobThreads(int n_threads){
        _asynchJobThreads = n_threads;
    }

    public int getAsynchJobThreads(){
        return _asynchJobThreads;
    }


    public void start() {
        _workerThreads = new NamedScheduledThreadPoolExecutor(AsynchJobExecutorService.class.getSimpleName(), _asynchJobThreads);
    }

    public  ScheduledExecutorService getExecutorService() {
        return _workerThreads;
    }

    public void stop() {
        try{
            _workerThreads.shutdown();
            _workerThreads.awaitTermination(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            _logger.error("TimeOut occured after waiting Client Threads to finish");
        }
    }

}



