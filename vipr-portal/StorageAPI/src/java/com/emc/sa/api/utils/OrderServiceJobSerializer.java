/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.curator.framework.recipes.queue.QueueSerializer;

public class OrderServiceJobSerializer implements QueueSerializer<OrderServiceJob> {
    final private Logger log = LoggerFactory.getLogger(OrderServiceJobSerializer.class);

    @Override
    public byte[] serialize(OrderServiceJob job) {
        byte[] Objbytes = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)){
            out.writeObject(job);
            Objbytes = bos.toByteArray();
        } catch (Exception e) {
            log.error("Serializing Object to byte Array Exception  :", e);
        }
        return Objbytes;
    }

    @Override
    public OrderServiceJob deserialize(byte[] bytes) {
        Object job = null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInput in = new ObjectInputStream(bis);) {
            job = in.readObject();
        } catch (Exception e) {
            log.error("DeSerializing Object to byte Array Exception  :", e);
        }
        return (OrderServiceJob) job;
    }
}
