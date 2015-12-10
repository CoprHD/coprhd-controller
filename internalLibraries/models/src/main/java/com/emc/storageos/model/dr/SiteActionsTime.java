/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_actions_time")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteActionsTime {
    private Date creationTime;
    private Date pausedTime;
    private Date lastOperationTime;

    @XmlElement(name = "creationTime")
    public Date getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Date creationTime) {
        this.creationTime = creationTime;
    }

    @XmlElement(name = "pausedTime")
    public Date getPausedTime() {
        return pausedTime;
    }

    public void setPausedTime(Date pausedTime) {
        this.pausedTime = pausedTime;
    }

    @XmlElement(name = "lastOperationTime")
    public Date getLastOperationTime() {
        return lastOperationTime;
    }

    public void setLastOperationTime(Date lastOperationTime) {
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
