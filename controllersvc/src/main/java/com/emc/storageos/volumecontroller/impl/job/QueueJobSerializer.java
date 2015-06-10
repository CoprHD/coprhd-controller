/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.job;

import org.apache.curator.framework.recipes.queue.QueueSerializer;

/**
 * Serializer for jobs that go into the job queue
 */
public class QueueJobSerializer implements QueueSerializer<QueueJob>
{
    public byte[] serialize(QueueJob job) {
        return job.serialize();
    }

    public QueueJob deserialize(byte[] bytes) {
        return QueueJob.deserialize(bytes);
    }
}
