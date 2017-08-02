/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;

/**
 * Generic serialization for an Object
 * 
 * @author watson
 * 
 */
public class GenericSerializer {
    private static final Logger _log = LoggerFactory.getLogger(GenericSerializer.class);
    private static final int MAX_ZK_OBJECT_SIZE_IN_BYTES = 250000;
    private static final int LOG_SIZE_IN_BYTES =  4 * MAX_ZK_OBJECT_SIZE_IN_BYTES;

    /**
     * Will serialize any serializable object.
     * @param object -- Java object that is serializable.
     * @param logName -- Name of object for log messages (can be null)
     * @param zkData -- if true, will impose a maximum size limit of MAX_ZK_OBJECT_SIZE_IN_BYTES, which is maximum size for zookeeper data
     * @return byte[] representing serialized data
     * @throws CoordinatorException for exceedingLimit if checked
     */
    static public byte[] serialize(Object object, String logName, boolean zkData) {
        String className = (object != null) ? object.getClass().getSimpleName() : "";
        String label = (logName != null) ? logName : "";
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            ObjectOutputStream ostream = new ObjectOutputStream(stream);
            ostream.writeObject(object);
            byte[] byteArray = stream.toByteArray();
            if (zkData && byteArray.length > MAX_ZK_OBJECT_SIZE_IN_BYTES) {
                _log.error(String.format("Serialization failure: Class %s %s Byte Array length is %d limit is %d", 
                        className, label, byteArray.length, MAX_ZK_OBJECT_SIZE_IN_BYTES));
                throw CoordinatorException.fatals.exceedingLimit("byte array size", MAX_ZK_OBJECT_SIZE_IN_BYTES);
            } else if (byteArray.length > LOG_SIZE_IN_BYTES) {
                _log.info(String.format("Serialization large object class %s %s size %d", className, label, byteArray.length));   
            }
            return byteArray;
        } catch (Exception ex) {
            throw CoordinatorException.fatals.failedToSerialize(ex);
        }
    }

    /**
     * De-serializes an object from byte[] data
     * @param data-- object data as byte[]
     * @return Object
     */
    static public Object deserialize(byte[] data) {
        try {
            ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(data));
            Object object = stream.readObject();
            return object;
        } catch (Exception ex) {
            throw CoordinatorException.fatals.failedToDeserialize(ex);
        }
    }
}
