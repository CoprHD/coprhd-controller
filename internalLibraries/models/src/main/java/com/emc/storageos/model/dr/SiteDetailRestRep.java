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
public class SiteDetailRestRep {
    private Date creationTime;
    private Date pausedTime;
    private Date lastUpdateTime;
    private Double ping;

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

    @XmlElement(name = "lastUpdateTime")
    public Date getlastUpdateTime() {
        return lastUpdateTime;
    }

    public void setlastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    @XmlElement(name = "network_ping")
    public Double getPing() {
        return ping;
    }

    public void setPing(Double ping) {
        this.ping = ping;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteActionsTime [creationTime=");
        builder.append(creationTime);
        builder.append(", pausedTime=");
        builder.append(pausedTime);
        builder.append(", lastUpdateTime=");
        builder.append(lastUpdateTime);
        builder.append(", ping=");
        builder.append(ping);
        builder.append("]");
        return builder.toString();
    }

}
