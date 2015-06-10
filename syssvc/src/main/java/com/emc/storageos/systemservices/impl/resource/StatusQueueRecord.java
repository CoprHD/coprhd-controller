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
package com.emc.storageos.systemservices.impl.resource;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * This util tracks status of object that has a queue.
 * 
 * @author luoq1
 * 
 */
public class StatusQueueRecord {
    public static final int STATUS_READY = 0; // Initial state
    public static final int STATUS_ACTIVE = 1;// In progress
    public static final int STATUS_CLEARED = 2;// Cleared
    public static final int STATUS_FINISHED = 3;// Done - No longer
    public static final int STATUS_DISABLED = 4;// Disabled, special condition
    public static final int STATUS_UNKNOWN = -1;
    private String id;
    private Object target;
    private final LinkedBlockingQueue<Object> queue;
    private int status;
    private String description;

    public StatusQueueRecord(String i, Object o, int st, int size) {
        setId(i);
        setTarget(o);
        setStatus(st);
        queue = new LinkedBlockingQueue<Object>();
    }

    public String getId() {
        return id;
    }

    private void setId(String id) {
        this.id = id;
    }

    public Object getTarget() {
        return target;
    }

    public synchronized void setTarget(Object target) {
        this.target = target;
    }

    public synchronized int getStatus() {
        return status;
    }

    public synchronized void setStatus(int status) {
        this.status = status;
    }

    public synchronized String getDescription() {
        return description;
    }

    public synchronized void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return (getStatus() == STATUS_ACTIVE);
    }

    public boolean isFinished() {
        return (getStatus() == STATUS_FINISHED);
    }

    public boolean isReady() {
        return (getStatus() == STATUS_READY);
    }

    public boolean isDisabled() {
        return (getStatus() == STATUS_DISABLED);
    }

    public boolean isClear() {
        return (getStatus() == STATUS_CLEARED);
    }

    public LinkedBlockingQueue<Object> getQueue() {
        return queue;
    }

    public void addRecord(Object entry) {
        queue.add(entry);
    }

    public Object getNextRecord() {
        return queue.remove();
    }

    public boolean isEmpty() {
        return (isClear() || isReady());
    }

    public boolean isFull() {
        return (queue.remainingCapacity() == 0);
    }
}
