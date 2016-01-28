/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "filesystem_expand")
public class FileSystemExpandParam {

    private String newSize;
    private int softLimit;
    private int softGrace;
    private int notificationLimit;

    public FileSystemExpandParam() {
    }

    public FileSystemExpandParam(String newSize) {
        this.newSize = newSize;
    }

    /**
     * Defines new expanded size of a FileSystem.
     * Supported size formats: TB, GB, MB, B. Default format is size in bytes.
     * Examples: 100GB, 614400000, 614400000B
     * 
     * @valid none
     */
    @XmlElement(required = true, name = "new_size")
    public String getNewSize() {
        return newSize;
    }

    public void setNewSize(String newSize) {
        this.newSize = newSize;
    }

    @XmlElement(name="soft_limit", required=false)
    public int getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(int softLimit) {
        this.softLimit = softLimit;
    }

    @XmlElement(name="soft_grace", required=false)
    public int getSoftGrace() {
        return softGrace;
    }

    public void setSoftGrace(int softGrace) {
        this.softGrace = softGrace;
    }
    @XmlElement(name="notification_limit", required=false)
    public int getNotificationLimit() {
        return notificationLimit;
    }

    public void setNotificationLimit(int notificationLimit) {
        this.notificationLimit = notificationLimit;
    }
}
