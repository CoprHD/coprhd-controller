/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import java.io.*;
import java.util.*;

/**
 * Placeholder implementation of controller async task.
 */
public class ControlRequest implements Serializable {
    private static final String QUEUE_NAME = "queueName";
    private static final String METHOD_FIELD_NAME = "method";
    private static final String TARGET_CLASS_FIELD_NAME = "targettype";
    private static final String ARG_FIELD_NAME = "arguments";
    private static final String DEVICE_INFO_NAME = "deviceinfo";

    private Map<String, Object> _req = new HashMap<String, Object>();

    private ControlRequest() {
    }

    public ControlRequest(String queueName, Dispatcher.DeviceInfo info, Object target, String method, Object... args) {
        _req.put(QUEUE_NAME, queueName);
        _req.put(DEVICE_INFO_NAME, info);
        _req.put(METHOD_FIELD_NAME, method);
        _req.put(TARGET_CLASS_FIELD_NAME, target.getClass().getName());
        _req.put(ARG_FIELD_NAME, Arrays.asList(args));
    }

    public Object[] getArg() {
        List arg = (List) _req.get(ARG_FIELD_NAME);
        return arg.toArray();
    }

    public String getMethodName() {
        return (String) _req.get(METHOD_FIELD_NAME);
    }

    public String getTargetClassName() {
        return (String) _req.get(TARGET_CLASS_FIELD_NAME);
    }

    public Dispatcher.DeviceInfo getDeviceInfo() {
        return (Dispatcher.DeviceInfo) _req.get(DEVICE_INFO_NAME);
    }

    public String getQueueName() {
        return (String) _req.get(QUEUE_NAME);
    }

    @SuppressWarnings({ "squid:S2118" })
    public byte[] serialize() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(out);
            oout.writeObject(_req); // Can not write non-serializable object(Map)
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static ControlRequest deserialize(byte[] data) {
        try {
            ControlRequest request = new ControlRequest();
            ObjectInputStream oim = new ObjectInputStream(new ByteArrayInputStream(data));
            Map<String, Object> req = (Map<String, Object>) oim.readObject();
            request._req = req;
            return request;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
