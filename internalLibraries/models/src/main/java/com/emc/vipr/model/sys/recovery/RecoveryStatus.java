/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.vipr.model.sys.recovery;

import java.util.Date;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Recovery status is a class used by REST API to represent the recovery status of cluster
 */
@XmlRootElement(name = "recovery_status")
public class RecoveryStatus {
    /**
     * The status of Node recovery
     */
    public enum Status {
        NOT_STARTED,  // have not started yet
        INIT,         // triggering recovery
        PREPARING,    // preparing recovery
        REPAIRING,    // repairing db inconsistency
        SYNCING,      // new node is syncing data
        FAILED,       // failed
        DONE,         // success
        CANCELLED     // node recovery was cancelled
    }

    /**
     * The error code of recovery failure
     */
    public enum ErrorCode {
        REPAIR_FAILED, // Db repair failed
        SYNC_FAILED,   // Db rebuild failed
        NEW_NODE_FAILURE, // Alive nodes get unavailable during node recovery
        INTERNAL_ERROR // Internal error
    }

    private Status status = Status.NOT_STARTED;
    private Date startTime;
    private Date endTime;
    private ErrorCode errorCode;

    @XmlElement(name = "status")
    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @XmlElement(name = "start_time")
    public Date getStartTime() {
        return this.startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    @XmlElement(name = "end_time")
    public Date getEndTime() {
        return this.endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    @XmlElement(name = "error_code")
    public ErrorCode getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Status:");
        sb.append(getStatus());
        sb.append(", StartTime:");
        sb.append(getStartTime());
        sb.append(", EndTime:");
        sb.append(getEndTime());
        sb.append(", ErrorCode:");
        sb.append(getErrorCode());
        return sb.toString();
    }
}
