/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.logging;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class LogRequest {
    private Date startTime;
    private Date endTime;
    private int logLevel;
    private long maxCount; // Maximum number of log messages
    private long maxBytes;// maximum byte size of log messages
    // The list of ids for the Bourne nodes from which to collect log data.
    private List<String> nodeIds = new ArrayList<>();
    // A list of the service names from which to collect log data.
    private List<String> baseNames = new ArrayList<>();
    // Log pattern match
    private String regex;

    private boolean dryRun;

    public LogRequest() {

    }

    /**
     * Constructor for log level requests.
     * 
     * @param nodeIds The list of Bourne node ids.
     * @param logNames The list of log file names.
     * @param severity The severity level to set.
     */
    public LogRequest(List<String> nodeIds, List<String> logNames,
            int severity) {
        if (nodeIds != null) {
            this.nodeIds = nodeIds;
        }

        if (logNames != null) {
            this.baseNames = logNames;
        }
        this.logLevel = severity;
    }

    public static class Builder {
        private Date startTime = null;
        private Date endTime = null;
        private int logLevel = LogSeverity.MAX_LEVEL;
        private long maxCount = 0;
        private long maxBytes = 0;
        private List<String> nodeIds = new ArrayList<>();
        private List<String> baseNames = new ArrayList<>();
        private String regex = null;

        public Builder() {
        }

        public Builder startTime(Date startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Date endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder logLevel(int logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder maxCont(long maxCount) {
            this.maxCount = maxCount;
            return this;
        }

        public Builder maxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
            return this;
        }

        public Builder nodeIds(List<String> nodeIds) {
            this.nodeIds = nodeIds;
            return this;
        }

        public Builder baseNames(List<String> baseNames) {
            this.baseNames = baseNames;
            return this;
        }

        public Builder regex(String regex) {
            this.regex = regex;
            return this;
        }

        public LogRequest build() {
            return new LogRequest(this);
        }

    }

    private LogRequest(Builder builder) {
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.logLevel = builder.logLevel;
        this.maxCount = builder.maxCount;
        this.maxBytes = builder.maxBytes;
        this.nodeIds = builder.nodeIds;
        this.baseNames = builder.baseNames;
        this.regex = builder.regex;
    }

    @XmlElement(name = "severity")
    public int getLogLevel() {
        return logLevel;
    }

    @XmlElement
    public long getMaxCount() {
        return maxCount;
    }

    @XmlElement
    public long getMaxBytes() {
        return maxBytes;
    }

    @XmlElement(name = "logNames")
    public List<String> getBaseNames() {
        if (baseNames == null) {
            baseNames = new ArrayList<>();
        }
        return baseNames;
    }

    /**
     * Lines limit reached
     * 
     * @param c
     * @return
     */
    public boolean isOverLimit(int c) {
        return (c > maxCount);
    }

    @XmlElement(name = "nodeIds")
    public List<String> getNodeIds() {
        if (nodeIds == null) {
            nodeIds = new ArrayList<>();
        }
        return nodeIds;
    }

    @XmlElement
    public Date getStartTime() {
        return startTime;
    }

    public long getStartTimeLong() {
        if (startTime != null) {
            return startTime.getTime();
        }
        return -1L;
    }

    @XmlElement
    public Date getEndTime() {
        return endTime;
    }

    @XmlElement
    public String getRegex() {
        return regex;
    }

    public void setNodeIds(List<String> nids) {
        this.nodeIds = nids;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    public void setMaxCount(long maxCount) {
        this.maxCount = maxCount;
    }

    public void setBaseNames(List<String> baseNames) {
        this.baseNames = baseNames;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public boolean isDryRun() {
        return this.dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("nodes ids=" + this.nodeIds).append(" startTime=" + this.startTime).
                append(" EndTime=" + this.endTime).append(" Logging level=" + this.logLevel)
                .append(" maxCount=" + this.maxCount).append(" maxByte=" + this.maxBytes)
                .append(" Regex=" + this.regex).append(" baseName=" + this.baseNames);
        return sb.toString();
    }

}
