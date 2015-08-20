/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.curator.framework.recipes.queue.QueueSerializer;

/**
 * Discovery Jobs serializer.
 */
public class DataCollectionJobSerializer implements QueueSerializer<DataCollectionJob> {
    private static final Logger _logger = LoggerFactory
            .getLogger(DataCollectionJobSerializer.class);

    /**
     * deserialize Discovery Job
     * 
     * @param data
     * @return DiscoveryJob
     *         To-Do :
     *         Abstract QueuJob and Discovery Job using Job Class, so that same code can be used in
     *         both places.
     */
    @Override
    public DataCollectionJob deserialize(byte[] data) {
        Object discoveryjob = null;
        ByteArrayInputStream bis = null;
        ObjectInput in = null;
        try {
            bis = new ByteArrayInputStream(data);
            in = new ObjectInputStream(bis);
            discoveryjob = in.readObject();
        } catch (Exception e1) {
            _logger.error("DeSerializing Object to byte Array Exception: ", e1);
        } finally {
            try {
                in.close();
                bis.close();
            } catch (IOException e1) {
                _logger.error("Error while closing Streams: ", e1);
            }
        }
        return (DataCollectionJob) discoveryjob;
    }

    /**
     * serialize
     * 
     * @param DataCollectionJob
     * @return bytes
     */
    @Override
    public byte[] serialize(DataCollectionJob job) {
        byte[] Objbytes = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(job);
            Objbytes = bos.toByteArray();
        } catch (Exception e) {
            _logger.error("Serializing Object to byte Array Exception: ", e);
        } finally {
            try {
                out.close();
                bos.close();
            } catch (IOException e) {
                _logger.error("Error while closing Streams: ", e);
            }
        }
        return Objbytes;
    }
}
