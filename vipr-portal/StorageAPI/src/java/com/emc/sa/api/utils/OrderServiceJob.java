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

    public OrderServiceJob(JobType type) {
        this.type = type;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        out.writeUTF(type.name());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        String s = in.readUTF();
        type = JobType.valueOf(s);
    }

    public JobType getType() {
        return type;
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(type.name());

        return builder.toString();
    }
}
