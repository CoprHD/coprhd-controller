/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
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
        if (logLevels == null) {
            logLevels = new ArrayList<LogLevel>();
        }
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
        // The ViPR node name.
        private String nodeName = "";
        // The name of the Bourne service.
        private String svcName = "";
        // The severity of the message.
        private LogSeverity severity;

        public LogLevel() {

        }
    
        //Constructor for Service logs
        public LogLevel(String nodeId, String nodeName, String svcName, String severity) {
            this.nodeId = nodeId;
            this.nodeName = nodeName;
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
         * Getter for the ViPR node name on which the message was logged.
         *
         * @return The ViPR node name on which the message was logged.
         */
        @XmlElement(name = "node_name")
        public String getNodeName() {
            return nodeName;
        }

        /**
         * Setter for the ViPR node name on which the message was logged.
         *
         * @param nodeName The ViPR node name on which the message was logged.
         */
        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
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
