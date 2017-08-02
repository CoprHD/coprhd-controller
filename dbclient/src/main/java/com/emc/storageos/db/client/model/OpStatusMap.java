/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operation status map.
 */
public class OpStatusMap extends AbstractChangeTrackingMap<Operation> {

    private static final Logger _log = LoggerFactory.getLogger(OpStatusMap.class);

    @Override
    public Operation valFromByte(byte[] value) {
        Operation op = new Operation();
        op.loadBytes(value);
        return op;
    }

    @Override
    public byte[] valToByte(Operation value) {
        return value.toBytes();
    }

    /**
     * create a task status for a new
     * 
     * @param task task id
     * @param op - operation
     * @throws IllegalArgumentException - if trying to update task with non
     *             modifiable fields.
     */
    public Operation createTaskStatus(String task, Operation op)
            throws IllegalArgumentException {
        if (containsKey(task)) {
            throw new IllegalArgumentException("task already exists");
        }

        if (op.getDescription() == null) {
            throw new IllegalArgumentException("missing required parameter: description");
        }

        if (op.getStartTime() == null) {
            op.setStartTime(Calendar.getInstance());
        }

        String status = op.getStatus();
        if ((status.equalsIgnoreCase(Operation.Status.ready.name().toUpperCase()))
                || (status.equalsIgnoreCase(Operation.Status.error.name().toUpperCase()))) {
            op.setEndTime(Calendar.getInstance());
        }
        put(task, op);
        return op;
    }

    /**
     * Update progress for an existing task status in the map
     * 
     * @param task task id
     * @param update
     * @throws IllegalArgumentException - if trying to update task with non
     *             modifiable fields.
     */
    public Operation updateTaskStatus(String task, Operation update)
            throws IllegalArgumentException {
        return updateTaskStatus(task,update, false);
    }

    /**
     * Update progress for an existing task status in the map
     * 
     * @param task task id
     * @param update
     * @param resetStartTime reset the start time to the current time if status is pending
     * @throws IllegalArgumentException - if trying to update task with non
     *             modifiable fields.
     */
    public Operation updateTaskStatus(String task, Operation update, boolean resetStartTime)
            throws IllegalArgumentException {
        if (containsKey(task)) {
            Operation op = get(task);
            Set<String> updatedFields = new HashSet<String>();
            updatedFields.addAll(update._changedFields);
            for (String field : updatedFields) {
                if (field.equals(Operation.PROGRESS_FIELD)) {
                    Integer progress = update.getProgress();
                    if ((progress > 0) && (progress <= 100)) {
                        op.setProgress(progress);
                    }
                } else if (field.equals(Operation.MESSAGE_FIELD)) {
                    String message = update.getMessage();
                    if (message != null) {
                        op.setMessage(message);
                    }
                } else if (field.equals(Operation.STATUS_FIELD)) {
                    String status = update.getStatus();
                    if (status != null) {
                        op.setStatus(status);
                        if ((status.equalsIgnoreCase(Operation.Status.ready.name()
                                .toUpperCase()))
                                || (status.equalsIgnoreCase(Operation.Status.error.name()
                                        .toUpperCase()))) {
                            op.setEndTime(Calendar.getInstance());
                        } else if (resetStartTime && status.equalsIgnoreCase(Operation.Status.pending.name()
                                .toUpperCase())) {
                            op.setStartTime(Calendar.getInstance());
                        }
                    }
                } else if (field.equals(Operation.SERVICE_CODE_FIELD)) {
                    Integer code = update.getServiceCode();
                    if (code != null) {
                        op.setServiceCode(code);
                    }
                } else if (field.equals(Operation.DESCRIPTION_FIELD)
                        || field.equals(Operation.START_TIME_FIELD)
                        || field.equals(Operation.END_TIME_FIELD)) {
                    throw new IllegalArgumentException("can not change the field : "
                            + field);
                }
            }
            put(task, op);
            return op;
        }
        else {
            return null;
        }
    }
}
