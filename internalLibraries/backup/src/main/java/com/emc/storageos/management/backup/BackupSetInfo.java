/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupSetInfo implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(BackupSetInfo.class);

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 301077366599522567L;

    private String name;
    private long size = 0;
    private long createTime = 0;

    public BackupSetInfo() {
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return this.size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getCreateTime() {
        return this.createTime;
    }

    public void setCreateTime(long time) {
        this.createTime = time;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(BackupSetInfo.class.getSimpleName());
        builder.append("(");
        builder.append("name=").append(name);
        double sizeMb = size * 1.0 / BackupConstants.MEGABYTE;
        builder.append(String.format(", size=%1$.2f MB", sizeMb));
        Format format = new SimpleDateFormat(BackupConstants.DATE_FORMAT);
        builder.append(", createTime=").append(format.format(new Date(createTime)));
        builder.append(")");
        return builder.toString();
    }
}
