/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.jobs.common;

import java.io.Serializable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.apache.curator.framework.recipes.queue.QueueSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobSerializer implements QueueSerializer<Serializable> {
    private static final Logger log = LoggerFactory.getLogger(JobSerializer.class);

    @Override
    public byte[] serialize(Serializable item) {
        byte[] Objbytes = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(item);
            Objbytes = bos.toByteArray();
        } catch (Exception e) {
            log.error("Serializing Object to byte Array Exception  :", e);
        } finally {
            try {
                out.close();
                bos.close();
            } catch (IOException e) {
                log.error("Error while closing Streams ", e);
            }
        }
        return Objbytes;
    }

    @Override
    public Serializable deserialize(byte[] bytes) {
        Object job = null;
        ByteArrayInputStream bis = null;
        ObjectInput in = null;
        try {
            bis = new ByteArrayInputStream(bytes);
            in = new ObjectInputStream(bis);
            job = in.readObject();
        } catch (Exception e) {
            log.error("DeSerializing Object to byte Array Exception  :", e);
        } finally {
            try {
                in.close();
                bis.close();
            } catch (IOException e) {
                log.error("Error while closing Streams ", e);
            }
        }
        return (Serializable) job;
    }

}
