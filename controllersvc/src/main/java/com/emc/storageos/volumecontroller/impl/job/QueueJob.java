/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.job;

import com.emc.storageos.volumecontroller.Job;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * A job that resides in the job queue
 */
public class QueueJob implements Serializable {

    protected Map<String, Object> _map = new HashMap<String, Object>();
    private static final String JOB_NAME = "job";

    protected QueueJob() {
    }

    public QueueJob(Job job) {
        _map.put(JOB_NAME, job);
    }

    public Job getJob() {
        return (Job) _map.get(JOB_NAME);
    }

    public byte[] serialize() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(out);
            oout.writeObject(_map); // NOSONAR
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static QueueJob deserialize(byte[] bytes) {
        try {
            QueueJob job = new QueueJob();
            ObjectInputStream oim = new ObjectInputStream(new ByteArrayInputStream(bytes));
            Map<String, Object> map = (Map<String, Object>) oim.readObject();
            job._map = map;
            return job;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
