/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.io.IOException;
import java.util.Date;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.storageos.model.db.DbConsistencyStatusRestRep.Status;

public class DbConsistencyStatus implements CoordinatorSerializable {
    private static Logger log = LoggerFactory.getLogger(UpgradeFailureInfo.class);
    private static final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

    private Status status;
    private Date startTime;
    private Date endTime;
    private int progress;
    private String workingPoint;
    private DbConsistencyStatus previous;
    private int inconsistencyCount;
    private int checkedCount;

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

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getWorkingPoint() {
        return workingPoint;
    }

    public void setWorkingPoint(String workingPoint) {
        this.workingPoint = workingPoint;
    }

    public int getInconsistencyCount() {
        return inconsistencyCount;
    }

    public void setInconsistencyCount(int inconsistencyCount) {
        this.inconsistencyCount = inconsistencyCount;
    }
    
    public int getCheckedCount() {
        return checkedCount;
    }

    public void setCheckedCount(int checkedCount) {
        this.checkedCount = checkedCount;
    }
    
    @Override
    @JsonIgnore
    public String encodeAsString() {
        return toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    @JsonIgnore
    public DbConsistencyStatus decodeFromString(String infoStr) throws FatalCoordinatorException {
        try {
            mapper.readerForUpdating(this).readValue(infoStr);
            return this;
        } catch (IOException e) {
            log.error("Failed to decode data string", e);
            throw CoordinatorException.fatals.decodingError(e.getMessage());
        }
    }

    @Override
    @JsonIgnore
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        throw new UnsupportedOperationException("");
    }
    
    @Override
    @JsonIgnore
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            log.error("Failed to serialize this object", e);
            throw CoordinatorException.fatals.failedToSerialize(e);
        }
    }
    
    @JsonIgnore
    public boolean isFinished() {
        return this.status==Status.SUCCESS || this.status==Status.FAILED;
    }
    
    @JsonIgnore
    public boolean isCancelled() {
        return this.status == Status.CANCEL;
    }

    @JsonIgnore
    public void moveToPrevious() {
        previous = new DbConsistencyStatus();
        previous.setStartTime(this.getStartTime());
        previous.setEndTime(this.getEndTime());
        previous.setStatus(this.getStatus());
        previous.setProgress(this.getProgress());
        previous.setWorkingPoint(this.getWorkingPoint());
        previous.setInconsistencyCount(this.getInconsistencyCount());
        previous.setCheckedCount(this.getCheckedCount());
        init();
    }
    
    @JsonIgnore
    public void init() {
        this.startTime = new Date();
        this.status = Status.IN_PROGRESS;
        this.progress = 0;
        this.inconsistencyCount = 0;
        this.checkedCount = 0;
    }
    
    @JsonIgnore
    public void movePreviousBack() {
        if (this.previous == null) {
            return;
        }
        this.startTime = this.previous.getStartTime();
        this.endTime = this.previous.getEndTime();
        this.status = this.previous.getStatus();
        this.progress = this.previous.getProgress();
        this.workingPoint = this.previous.getWorkingPoint();
        this.inconsistencyCount = this.previous.getInconsistencyCount();
        this.checkedCount = this.previous.getCheckedCount();
        this.previous = null;
    }
    
    @JsonIgnore
    public void update(int total, String workingPoint, int inconsistencyCount) {
        this.checkedCount++;
        this.progress = (int)(this.checkedCount/total*100);
        this.workingPoint = workingPoint;
        this.inconsistencyCount = inconsistencyCount;
    }
    
    @JsonIgnore
    public void markResult(Status status) {
        this.endTime = new Date();
        this.status = status;
    }
}