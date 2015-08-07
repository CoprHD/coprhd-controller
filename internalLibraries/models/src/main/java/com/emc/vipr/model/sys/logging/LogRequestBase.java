/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.logging;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlElement;

import com.emc.vipr.model.sys.logging.LogSeverity;

/**
 * The base request made to the log service resource.
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
public class LogRequestBase {

    // The list of ids for the Bourne nodes from which to collect log data.
    private List<String> nodeIds;

    // A list of the log file names from which to collect log data.
    private List<String> logNames;

    // The minimum severity level for the log messages to be returned. Messages
    // at or below (i.e., more severe), this severity level are to be returned.
    private LogSeverity severity;

    // Empty constructor
    protected LogRequestBase() {

    }

    /**
     * Constructor for log level requests.
     * 
     * @param nodeIds The list of Bourne node ids.
     * @param logNames The list of log file names.
     * @param severity The severity level to set.
     */
    protected LogRequestBase(List<String> nodeIds, List<String> logNames,
            LogSeverity severity) {
        if (nodeIds != null) {
            this.nodeIds = nodeIds;
        }

        if (logNames != null) {
            this.logNames = logNames;
        }

        this.severity = severity;
    }

    /**
     * Copy constructor
     */
    protected LogRequestBase(LogRequestBase logRequestInfo) {
        nodeIds = logRequestInfo.getNodeIds();
        logNames = logRequestInfo.getLogNames();
        severity = logRequestInfo.getSeverity();
    }

    /**
     * Getter for the list of Bourne node ids.
     * 
     * @return The list of Bourne node ids.
     */
    @XmlElement(name = "nodeIds")
    public List<String> getNodeIds() {
        if (nodeIds == null) {
            nodeIds = new ArrayList<String>();
        }
        return nodeIds;
    }

    /**
     * Setter for the list of Bourne node ids.
     * 
     * @param nodeIds new list of nodes.
     */
    public void setNodeIds(List<String> nodeIds) {
        this.nodeIds = nodeIds;
    }

    /**
     * Getter for names of the logs.
     * 
     * @return The names of the logs.
     */
    @XmlElement(name = "logNames")
    public List<String> getLogNames() {
        if (logNames == null) {
            logNames = new ArrayList<String>();
        }
        return logNames;
    }

    /**
     * Setter for names of the logs.
     */
    public void setLogNames(List<String> logNames) {
        this.logNames = logNames;
    }

    /**
     * Getter for the log severity level.
     * 
     * @return The log severity level.
     */
    @XmlElement(name = "severity")
    public LogSeverity getSeverity() {
        return severity;
    }

    /**
     * Setter for the log severity level.
     */
    public void setSeverity(LogSeverity severity) {
        this.severity = severity;
    }
}
