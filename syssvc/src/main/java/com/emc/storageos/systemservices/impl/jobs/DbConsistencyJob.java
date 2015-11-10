/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs;

import java.io.Serializable;
import java.util.Date;

import com.emc.storageos.model.db.DbConsistencyStatusRestRep.Status;

@SuppressWarnings("serial")
public class DbConsistencyJob implements Serializable {
    private Status status;
    private Date startTime;
    private String workingPoint;
    
    public Status getStatus() {
        return status;
    }
    public void setStatus(Status status) {
        this.status = status;
    }
    public Date getStartTime() {
        return startTime;
    }
    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }
    public String getWorkingPoint() {
        return workingPoint;
    }
    public void setWorkingPoint(String workingPoint) {
        this.workingPoint = workingPoint;
    }

}
