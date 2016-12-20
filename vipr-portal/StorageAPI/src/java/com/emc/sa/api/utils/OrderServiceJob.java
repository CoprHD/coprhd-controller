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

    public enum JobType {
        DELETE_ORDER,
        DOWNLOAD_ORDER
    };

    private JobType type;
    /*
    private long startTimeInNS;
    private long endTimeInNS;
    private List<URI> tenantIDs;

    // private long total = 0; // number of orders to be deleted
    private long nOrdersDeleted = 0;  // the total number of deleted orders so far
    private long lastCompletedTimeStamp = 0;

    private long nOrdersDeletedWithinPeriod = 0;
    private long startTimeInThisRound = 0;
    */

    //used for deserialize from the distributed queue
    public OrderServiceJob(JobType type) {
        this.type = type;
    }

    /*
    public OrderServiceJob(JobType type, long start, long end, List<URI> tids) {
        startTimeInNS = start;
        endTimeInNS = end;
        tenantIDs = tids;
        this.type = type;
    }
    */

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        out.writeUTF(type.name());

        /*
        //parameters given by REST API
        out.writeLong(startTimeInNS);
        out.writeLong(endTimeInNS);
        out.writeObject(tenantIDs);

        out.writeLong(total);
        out.writeLong(nOrdersDeleted);
        out.writeLong(lastCompletedTimeStamp);

        out.writeLong(nOrdersDeletedWithinPeriod);
        out.writeLong(startTimeInThisRound);
        */
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        String s = in.readUTF();
        type = JobType.valueOf(s);

        /*
        startTimeInNS = in.readLong();
        endTimeInNS = in.readLong();
        tenantIDs = (List<URI>)in.readObject();

        total = in.readLong();
        nOrdersDeleted = in.readLong();
        lastCompletedTimeStamp = in.readLong();

        nOrdersDeletedWithinPeriod = in.readLong();
        startTimeInThisRound = in.readLong();
        */
    }

    public JobType getType() {
        return type;
    }

    /*
    public long getStartTimeInNS() {
        return startTimeInNS;
    }

    public long getEndTimeInNS() {
        return endTimeInNS;
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
    */

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(type.name());

        /*
        builder.append("startTimeInNS:")
                .append(startTimeInNS)
                .append("\nendTimeInNS:")
                .append(endTimeInNS)
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
                */

        return builder.toString();
    }
}
