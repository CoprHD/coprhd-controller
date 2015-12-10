/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_actions_time")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteActionsTime {
    private long creationTime;
    private long pausedTime;
    private long lastOperationTime;

    @XmlElement(name = "creationTime")
    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    @XmlElement(name = "pausedTime")
    public long getPausedTime() {
        return pausedTime;
    }

    public void setPausedTime(long pausedTime) {
        this.pausedTime = pausedTime;
    }

    @XmlElement(name = "lastOperationTime")
    public long getLastOperationTime() {
        return lastOperationTime;
    }

    public void setLastOperationTime(long lastOperationTime) {
        this.lastOperationTime = lastOperationTime;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteActionsTime [creationTime=");
        builder.append(creationTime);
        builder.append(", pausedTime=");
        builder.append(pausedTime);
        builder.append(", lastOperationTime=");
        builder.append(lastOperationTime);
        builder.append("]");
        return builder.toString();
    }

}
