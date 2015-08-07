/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.logging;

import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.vipr.model.sys.logging.LogSeverity;

/**
 * Holds the request parameters for a request made to the log service resource.
 */
@XmlRootElement
public class LogRequestInfo extends LogRequestBase {

    // A regular expression that log messages to be returned must match.
    private String msgRegex = null;

    // The requested start time.
    private Date startTime = null;

    // The request end time.
    private Date endTime = null;

    // Maximum no. of log messages to stream
    private int maxCount = 0;

    {
        if (getSeverity() == null) {
            setSeverity(LogSeverity.TRACE);
        }
    }

    // Response stream maximum byte size
    private long maxBytes = 0;

    private boolean dryRun = false;

    // Empty constructor
    public LogRequestInfo() {

    }

    /**
     * Constructor.
     * 
     * @param nodeIds The list of Bourne node ids.
     * @param logNames The list of log file names.
     * @param severity The minimum desired severity level.
     * @param startTime The log start time.
     * @param endTime The log end time.
     * @param msgRegex The log message regular expression.
     */
    public LogRequestInfo(List<String> nodeIds, List<String> logNames,
            LogSeverity severity, Date startTime, Date endTime,
            String msgRegex, int maxCount) {
        super(nodeIds, logNames, severity);

        this.startTime = startTime;
        this.endTime = endTime;
        this.msgRegex = msgRegex;
        this.maxCount = maxCount;
    }

    /**
     * Copy constructor
     */
    public LogRequestInfo(LogRequestInfo logRequestInfo) {
        super(logRequestInfo);
        startTime = logRequestInfo.getStartTime();
        endTime = logRequestInfo.getEndTime();
        msgRegex = logRequestInfo.getMsgRegex();
        maxCount = logRequestInfo.getMaxCount();
    }

    /**
     * Getter for the log message regular expression.
     * 
     * @return The log message regular expression or null if not set.
     */
    public String getMsgRegex() {
        return msgRegex;
    }

    /**
     * Setter for the log message regular expression.
     */
    public void setMsgRegex(String msgRegex) {
        this.msgRegex = msgRegex;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
