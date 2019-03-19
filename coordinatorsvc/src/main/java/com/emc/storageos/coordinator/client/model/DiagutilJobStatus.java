/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;


import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo.*;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

/**
 * 
 * Contains information about the status of Diagutils Job
 *
 */
public class DiagutilJobStatus implements CoordinatorSerializable {
    private static final Logger log = LoggerFactory.getLogger(DiagutilJobStatus.class);
    private static final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

    private String startTime;
    private String nodeId;
    private DiagutilStatus status;
    private DiagutilStatusDesc description;
    private String errCode;
    private String location;
    private String endTime;
    private String jobType;

    public DiagutilJobStatus() {
    }

    /**
     * A constructor for diagutils Job status
     * 
     * @param startTime 
     *          start time of the diagutils Job
     * @param nodeId
     *          id of the ViPR node
     * @param status
     *          the diagutils job status 
     * @param desc
     *          diagutils job status description
     * @param err
     *          error code pertaining to diagutils job
     * @param dir
     *          directory containing diagutils package
     * 
     * @param endTime
     *          end time of the diagutils Job
     *          
     * @param jobType
     *          The diagUtil Job Type                  
     */
    public DiagutilJobStatus(String startTime, String nodeId, DiagutilStatus status, DiagutilStatusDesc desc, String err, String dir, String endTime, String jobType) {
        this.startTime = startTime;
        this.nodeId = nodeId;
        this.status = status;
        this.description = desc;
        this.errCode = err;
        this.location = dir;
        this.endTime = endTime;
        this.jobType = jobType;
    }
    
    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    
    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public DiagutilStatus getStatus() {
        return status;
    }

    public void setStatus(DiagutilStatus status) {
        this.status = status;
    }

    public DiagutilStatusDesc getDescription() {
        return description;
    }

    public void setDescription(DiagutilStatusDesc description) {
        this.description = description;
    }

    public String getErrCode() {
        return errCode;
    }

    public void setErrCode(String errCode) {
        this.errCode = errCode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
    
    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }
    

    @Override
    @JsonIgnore
    public String encodeAsString() {
        return toString();
    }

    @Override
    @JsonIgnore
    public DiagutilJobStatus decodeFromString(String infoStr) throws FatalCoordinatorException {
        try {
            return mapper.readValue(infoStr, DiagutilJobStatus.class);
        } catch (IOException e) {
            log.error("Failed to decode data string", e);
            throw CoordinatorException.fatals.decodingError(e.getMessage());
        }
    }

    @Override
    @JsonIgnore
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return null;
    }

    @Override
    @JsonIgnore
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (IOException e) {
            log.error("Failed to serialize this object", e);
        }
        return null;
    }
    
    /**
     * Returns an object containing diagutils info
     * 
     * @return diagutilInfo
     *             Object containing diagutil info such as status, node etc
     */
    @JsonIgnore
    public DiagutilInfo toDiagutilInfo() {
        DiagutilInfo diagutilInfo = new DiagutilInfo();
        diagutilInfo.setStatus(status);
        diagutilInfo.setStartTimeStr(startTime);
        diagutilInfo.setNodeId(nodeId);
        diagutilInfo.setDesc(description);
        diagutilInfo.setLocation(location);
        diagutilInfo.setErrcode(errCode);
        diagutilInfo.setEndTimeStr(endTime);
        diagutilInfo.setJobType(jobType);
        return diagutilInfo;
    }
}
