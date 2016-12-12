/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.Date;
import java.util.List;

public class OrderServiceJob implements Serializable {

    private static final long serialVersionUID = -3470928289876091965L;

    enum JobType {
        DELETE
    };

    private long startTime;
    private long endTime;
    private List<URI> tenantIDs;

    private long total = 0; // number of orders to be deleted
    private long nOrdersDeleted = 0;  // the total number of deleted orders so far
    private long lastCompletedTimeStamp = 0;

    private long nOrdersDeletedWithinPeriod = 0;
    private long startTimeInThisRound = 0;

    public OrderServiceJob() {
    }

    public OrderServiceJob(long start, long end, List<URI> tids) {
        startTime = start;
        endTime = end;
        tenantIDs = tids;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        //parameters given by REST API
        out.writeLong(startTime);
        out.writeLong(endTime);
        out.writeObject(tenantIDs);

        out.writeLong(total);
        out.writeLong(nOrdersDeleted);
        out.writeLong(lastCompletedTimeStamp);

        out.writeLong(nOrdersDeletedWithinPeriod);
        out.writeLong(startTimeInThisRound);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        startTime = in.readLong();
        endTime = in.readLong();
        tenantIDs = (List<URI>)in.readObject();

        total = in.readLong();
        nOrdersDeleted = in.readLong();
        lastCompletedTimeStamp = in.readLong();

        nOrdersDeletedWithinPeriod = in.readLong();
        startTimeInThisRound = in.readLong();
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<URI> getTenandIDs() {
        return tenantIDs;
    }

    public long getTotal() {
        return total;
    }

    public long getnOrdersDeleted() {
        return nOrdersDeleted;
    }

    public long getLastCompletedTimeStamp() {
        return lastCompletedTimeStamp;
    }

    public long getnOrdersDeletedWithinPeriod() {
        return nOrdersDeletedWithinPeriod;
    }

    public long getStartTimeInThisRound() {
        return startTimeInThisRound;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("startTime:")
                .append(startTime)
                .append("\nendTime:")
                .append(endTime)
                .append("\ntenandIDs:")
                .append(tenantIDs)
                .append("\ntotal:")
                .append(total)
                .append("\norders deleted:")
                .append(nOrdersDeleted)
                .append("\nlast completed time:")
                .append(new Date(lastCompletedTimeStamp))
                .append("\norders deleted in this period:")
                .append(nOrdersDeletedWithinPeriod)
                .append("\nstart time in this period:")
                .append(startTimeInThisRound);

        return builder.toString();
    }
}
