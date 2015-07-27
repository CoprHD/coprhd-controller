/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.vipr.model.sys.logging;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class to encapsulate the data for log levels.
 */
@XmlRootElement(name = "log-levels")
public class LogLevels {
    
    private List<LogLevel> logLevels;

    @XmlElement(name = "levels")
    public List<LogLevel> getLogLevels() {
        if (logLevels == null)
            logLevels = new ArrayList<LogLevel>();
        return logLevels;
    }
    
    public void setLogLevels(List<LogLevel> logLevels) {
        this.logLevels = logLevels;
    }

    /**
     * Class to encapsulate the data for a specific log level.
     */
    @XmlRootElement(name = "log_level")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class LogLevel {
        // The Bourne node identifier.
        private String nodeId = "";
        // The name of the Bourne service.
        private String svcName = "";
        // The severity of the message.
        private LogSeverity severity;
    
        public LogLevel() {
    
        }
    
        //Constructor for Service logs
        public LogLevel(String nodeId, String svcName, String severity) {
            this.nodeId = nodeId;
            this.svcName = svcName;
            this.severity = LogSeverity.find(severity.toUpperCase());
        }
    
        /**
         * Getter for the Bourne node identifier on which the message was logged.
         *
         * @return The Bourne node identifier on which the message was logged.
         */
        @XmlElement(name = "node")
        public String getNodeId() {
            return nodeId;
        }
    
        /**
         * Setter for the Bourne node identifier on which the message was logged.
         *
         * @param nodeId The Bourne node identifier on which the message was logged.
         */
        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }
    
        /**
         * Getter for the name of the service that logged the message.
         *
         * @return The name of the service that logged the message.
         */
        @XmlElement(name = "service")
        public String getSvcName() {
            return svcName;
        }
    
        /**
         * Setter for the name of the service that logged the message.
         *
         * @param svcName The name of the service that logged the message.
         */
        public void setSvcName(String svcName) {
            this.svcName = svcName;
        }
    
        /**
         * Getter for the severity level of the message.
         *
         * @return The severity level of the message.
         */
        @XmlElement(name = "severity")
        public LogSeverity getSeverity() {
            return severity;
        }
    
        /**
         * Setter for the severity level of the message.
         *
         * @param severity The severity level of the message.
         */
        public void setSeverity(LogSeverity severity) {
            this.severity = severity;
        }
    }
}