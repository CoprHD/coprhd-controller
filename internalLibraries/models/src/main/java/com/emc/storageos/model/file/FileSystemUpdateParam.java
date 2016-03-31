/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "filesystem_update")
public class FileSystemUpdateParam {

    private int softLimit;
    private int softGrace;
    private int notificationLimit;

    public FileSystemUpdateParam() {
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
