/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.geo;

import java.io.*;
import org.apache.curator.framework.recipes.queue.QueueSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeoServiceJobSerializer implements QueueSerializer<GeoServiceJob> {

    final private Logger log = LoggerFactory.getLogger(GeoServiceJobSerializer.class);

    @Override
    public byte[] serialize(GeoServiceJob job) {
        byte[] Objbytes = null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = null;
            try {
                out = new ObjectOutputStream(bos);
                out.writeObject(job);
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
    public GeoServiceJob deserialize(byte[] bytes) {
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
            return (GeoServiceJob) job;
    }

}

