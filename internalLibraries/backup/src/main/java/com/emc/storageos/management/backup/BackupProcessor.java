/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.management.backup;

import java.util.ArrayList; 
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BackupProcessor {
    private static final Logger log = LoggerFactory.getLogger(BackupProcessor.class);
    private ExecutorService executor;
    private CountDownLatch latch;
    private Map<String, String> hosts;
    private List<Integer> ports;
    private String backupTag;
    private int taskCnt = 0;

    public BackupProcessor(Map<String, String> hosts, List<Integer> ports, String backupTag) {
        this.hosts = hosts;
        this.ports = ports;
        if (hosts != null && ports != null)
            taskCnt = hosts.size() * ports.size();
        if (taskCnt == 0)
            throw new IllegalArgumentException(
                    String.format("Invalid argument, hosts:%s ports:%s", hosts, ports));
        this.backupTag = backupTag;
        this.executor = Executors.newFixedThreadPool(taskCnt);
        this.latch = new CountDownLatch(taskCnt);
    }

    public <T> List<BackupTask<T>> process(BackupCallable<T> callable, final boolean needCancel)
            throws Exception {
        List<BackupTask<T>> tasks = new ArrayList<BackupTask<T>>(taskCnt);
        try {
            List<BackupRequest> backupRequests = initBackupRequest(); 
            for (BackupRequest request : backupRequests) {
                BackupCallable<T> task = (BackupCallable<T>)callable.clone();
                task.setBackupTag(backupTag);
                task.setHost(request.host);
                task.setPort(request.port);
                task.setLatch(latch);
                Future<T> future = executor.submit(task);
                BackupResponse<T> response = new BackupResponse<T>(future);
                tasks.add(new BackupTask<T>(request, response));
            }
            latch.await();
            log.info("all tasks are finished");
        } catch (Exception e) {
            if (needCancel) {
                log.error("Try to cancel all the tasks for error, e= ", e);
                for (BackupTask<T> task : tasks) {
                    task.getResponse().getFuture().cancel(true);
                }
            }
            throw e;
        } finally {
            executor.shutdown();
        }
        return tasks;
    }       

    private List<BackupRequest> initBackupRequest() {
        List<BackupRequest> backupRequests = new ArrayList<BackupRequest>();
        for (Map.Entry<String, String> entry : hosts.entrySet()) {
            for (Integer port : ports) {
                backupRequests.add(new BackupRequest(entry.getKey(), entry.getValue(), port));
            }
        }
        return backupRequests;
    }

    public class BackupRequest {
        private final String node;
        private final String host;
        private final int port;
        
        BackupRequest(String node, String host, int port) {
            this.node = node;
            this.host = host;
            this.port = port;
        }

        public String getNode() {
            return node;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
    }

    public class BackupResponse<T> {
        private Future<T> future;

        BackupResponse(Future<T> future) {
            this.future = future;
        }

        public Future<T> getFuture() {
             return future;
        }
    }

    public class BackupTask<T> {
        final BackupRequest request;
        final BackupResponse<T> response;

        BackupTask(final BackupRequest request, final BackupResponse<T> response) {
             this.request = request;
             this.response = response;
        }

        public BackupRequest getRequest() {
             return request;
        }

        public BackupResponse<T> getResponse() {
             return response;
        }
    }
}
