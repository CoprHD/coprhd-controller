/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.model.sys.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.*;
import java.io.Serializable;

@XmlRootElement(name = "backup_operation_status")
public class BackupOperationStatus implements Serializable {
    private static final long serialVersionUID = -1127851063451833657L;

    private static final Logger log = LoggerFactory.getLogger(BackupOperationStatus.class);
    private OperationStatus lastManualCreation;
    private OperationStatus lastScheduledCreation;
    private OperationStatus lastSuccessfulCreation;
    private OperationStatus lastUpload;
    private long nextScheduledCreation = 0;

    public BackupOperationStatus() {
    }

    @XmlElement(name = "last_manual_creation")
    public OperationStatus getLastManualCreation() {
        return lastManualCreation;
    }

    public void setLastManualCreation(OperationStatus lastManualCreation) {
        this.lastManualCreation = lastManualCreation;
    }

    public void setLastManualCreation(String opName, long opTime, OpMessage opStatus) {
        this.lastManualCreation = new OperationStatus();
        this.lastManualCreation.opName = opName;
        this.lastManualCreation.opTime = opTime;
        this.lastManualCreation.opMessage = opStatus;
    }

    @XmlElement(name = "last_scheduled_creation")
    public OperationStatus getLastScheduledCreation() {
        return lastScheduledCreation;
    }

    public void setLastScheduledCreation(OperationStatus lastScheduledCreation) {
        this.lastScheduledCreation = lastScheduledCreation;
    }

    public void setLastScheduledCreation(String opName, long opTime, OpMessage opStatus) {
        this.lastScheduledCreation = new OperationStatus();
        this.lastScheduledCreation.opName = opName;
        this.lastScheduledCreation.opTime = opTime;
        this.lastScheduledCreation.opMessage = opStatus;
    }

    @XmlElement(name = "next_scheduled_creation")
    public long getNextScheduledCreation() {
        return nextScheduledCreation;
    }

    public void setNextScheduledCreation(long nextScheduledCreation) {
        this.nextScheduledCreation = nextScheduledCreation;
    }

    @XmlElement(name = "last_successful_creation")
    public OperationStatus getLastSuccessfulCreation() {
        return lastSuccessfulCreation;
    }

    public void setLastSuccessfulCreation(OperationStatus lastSuccessfulCreation) {
        this.lastSuccessfulCreation = lastSuccessfulCreation;
    }

    public void setLastSuccessfulCreation(String opName, long opTime, OpMessage opType) {
        this.lastSuccessfulCreation = new OperationStatus();
        this.lastSuccessfulCreation.opName = opName;
        this.lastSuccessfulCreation.opTime = opTime;
        this.lastSuccessfulCreation.opMessage = opType;
    }

    @XmlElement(name = "last_upload")
    public OperationStatus getLastUpload() {
        return lastUpload;
    }

    public void setLastUpload(OperationStatus lastUpload) {
        this.lastUpload = lastUpload;
    }

    public void setLastUpload(String opName, long opTime, OpMessage opStatus) {
        this.lastUpload = new OperationStatus();
        this.lastUpload.opName = opName;
        this.lastUpload.opTime = opTime;
        this.lastUpload.opMessage = opStatus;
    }

    /**
     * Class to abstract the status of an operation
     */
    @XmlRootElement(name = "operation_status")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class OperationStatus {
        private String opName = "";
        private long opTime = 0;
        private OpMessage opMessage = OpMessage.OP_NONE;

        public OperationStatus() {
        }

        public OperationStatus(String opName, long opTime, OpMessage opMessage) {
            this.opName = opName;
            this.opTime = opTime;
            this.opMessage = opMessage;
        }

        @XmlElement(name = "operation_name")
        public String getOperationName() {
            return this.opName;
        }

        public void setOperationName(String opName) {
            this.opName = opName;
        }

        @XmlElement(name = "operation_time")
        public long getOperationTime() {
            return this.opTime;
        }

        public void setOperationTime(long operationTime) {
            this.opTime = operationTime;
        }

        @XmlElement(name = "operation_msg")
        public OpMessage getOperationMessage() {
            return this.opMessage;
        }

        public void setOperationMessage(OpMessage operationMessage) {
            this.opMessage = operationMessage;
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("OperationName:");
            sb.append(getOperationName());
            sb.append(", OperationTime:");
            sb.append(getOperationTime());
            sb.append(", OperationMessage:");
            sb.append(getOperationMessage().getValue());
            return sb.toString();
        }
    }

    @XmlType(name = "opMessage")
    public enum OpMessage {
        OP_NONE("none"),
        //operation status
        OP_SUCCESS("success"),
        OP_FAILED("failed"),
        //operation type
        OP_MANUAL("manual"),
        OP_SCHEDULED("scheduled");

        private String message = "";

        OpMessage(String msg) {
            this.message = msg;
        }

        public String getValue() {
            return message;
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("LastSuccessfulCreation(");
        sb.append(getLastSuccessfulCreation());
        sb.append("), LastManualCreation(");
        sb.append(getLastManualCreation());
        sb.append("), LastScheduledCreation(");
        sb.append(getLastScheduledCreation());
        sb.append("), NextScheduledCreation(");
        sb.append(getNextScheduledCreation());
        sb.append("), LastUpload(");
        sb.append(getLastUpload());
        sb.append(")");
        return sb.toString();
    }
}
