/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

public abstract class BackupCallable<T> implements Callable<T>, Cloneable {
    protected String backupTag;
    protected String host;
    protected int port;
    protected CountDownLatch latch;

    public BackupCallable() {
    }

    public BackupCallable(String backupTag, String host,
            int port, CountDownLatch latch) {
        this.backupTag = backupTag;
        this.host = host;
        this.port = port;
        this.latch = latch;
    }

    public void setBackupTag(String backupTag) {
        this.backupTag = backupTag;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public T call() throws Exception {
        try {
            return sendRequest();
        } finally {
            latch.countDown();
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public abstract T sendRequest() throws Exception;
}
