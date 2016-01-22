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

@XmlRootElement(name = "site_details")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteDetailRestRep {
    private Date creationTime;
    private Date pausedTime;
    private Date lastUpdateTime;
    private Double networkLatencyInMs;
    private String clusterState;
    private boolean dataSynced;
    private String repairStatus;
    private Date repairCompleteTime;

    @XmlElement(name = "repairStatus")
    public String getRepairStatus() {
        return repairStatus;
    }

    public void setRepairStatus(String repairStatus) {
        this.repairStatus = repairStatus;
    }

    @XmlElement(name = "repairCompleteTime")
    public Date getRepairCompleteTime() {
        return repairCompleteTime;
    }

    public void setRepairCompleteTime(Date repairCompleteTime) {
        this.repairCompleteTime = repairCompleteTime;
    }

    @XmlElement(name = "dataSynced")
    public boolean isDataSynced() {
        return dataSynced;
    }

    public void setDataSynced(boolean dataSynced) {
        this.dataSynced = dataSynced;
    }

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

    @XmlElement(name = "networkLatencyInMs")
    public Double getNetworkLatencyInMs() {
        return networkLatencyInMs;
    }

    public void setNetworkLatencyInMs(Double networkLatencyInMs) {
        this.networkLatencyInMs = networkLatencyInMs;
    }

    @XmlElement(name = "clusterState")
    public String getClusterState() {
        return clusterState;
    }

    public void setClusterState(String clusterState) {
        this.clusterState = clusterState;
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
        builder.append(", networkLatencyInMs=");
        builder.append(networkLatencyInMs);
        builder.append(", clusterState=");
        builder.append(clusterState);
        builder.append("]");
        return builder.toString();
    }

}
