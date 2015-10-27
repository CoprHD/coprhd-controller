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

}
