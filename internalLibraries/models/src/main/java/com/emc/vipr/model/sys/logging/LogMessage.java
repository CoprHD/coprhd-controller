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

import com.emc.vipr.model.sys.logging.LogSeverity;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Class to encapsulate the data for a log message. Implements the Comparable
 * interface for the purpose of sorting log messages by the log message
 * timestamp.
 */
@XmlRootElement(name = "log_info")
@XmlAccessorType(XmlAccessType.NONE)
public class LogMessage {
    // The log message
    @JsonProperty("message")
    private String message = "";
    
    // The Bourne node identifier.
    @JsonProperty("node")
    private String nodeId;
    
    // The line number in the class. 
    @JsonProperty("line")
    private String lineNumber;
    
    // The class generating the log message.
    @JsonProperty("class")
    private String className;
    
    // The name of the Bourne service.
    @JsonProperty("service")
    private String svcName;
    
    // The thread in which the message was logged.  
    @JsonProperty("thread")
    private String thread;
    
    // The severity of the message.
    @JsonProperty("severity")
    private LogSeverity severity;
    
    // Message time in MS
    @JsonProperty("time_ms")
    private long timeMS;
    
    // Message formatted time
    @JsonProperty("time")
    private String time;
    
    @JsonProperty("_facility")
    private String facility;

    // length of original log message read, for measuring purpose
    @JsonIgnore
    private int length;
	
    private static ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal
            <SimpleDateFormat>() {            
                @Override 
                protected SimpleDateFormat initialValue() {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return dateFormat;
                }
            };
            
    public LogMessage() {

    }

    //Constructor for Service logs
    public LogMessage(String nodeId, String svcName, long timeMS, String thread,
                      String severity, String className, String lineNumber, String message) {

        this.message = message;
        this.nodeId = nodeId;
        this.lineNumber = lineNumber;
        this.className = className;
        this.svcName = svcName;
        this.thread = thread;
        this.severity = LogSeverity.find(severity.toUpperCase());
        this.timeMS = timeMS;
        setTimeStr();
    }

    //Constructor for Sys logs
    public LogMessage(String nodeId, long timeMS, String facility,
                      String severity, String svcName, String message) {
        this.message = message;
        this.nodeId = nodeId;
        this.svcName = svcName;
        this.severity = LogSeverity.find(severity.toUpperCase());
        this.timeMS = timeMS;
        setTimeStr();
        this.facility = facility;
    }
    
    //Constructor for error logs
    public LogMessage(String errMsg, Throwable t) {
        this.message = errMsg;
        this.svcName = LogConstants.INTERNAL_ERROR;
        this.severity = LogSeverity.ERROR;
        if (t != null) {
            this.className = t.getClass().toString();
        }
        this.timeMS = new Date().getTime();
        setTimeStr();
    }

    private void setTimeStr() {
        time = timeMS > 0 ? dateFormat.get().format(new Date(timeMS)) : null;
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
     * Getter for the log message timestamp.
     *
     * @return The log message timestamp.
     */
    @XmlElement(name = "time")
    public String getTime() {
        if (time == null) {
            setTimeStr();
        }
        return time;
    }

    /**
     * Setter for the time string.  
     *
     * @param time The log message time string.
     */
    public void setTime(String time) {
        this.time = time;
    }
    
    /**
     * Getter for the log message thread.
     *
     * @return The log message thread.
     */
    @XmlElement(name = "thread")
    public String getThread() {
        return thread;
    }

    /**
     * Setter for the log message thread.
     *
     * @param thread The log message thread.
     */
    public void setThread(String thread) {
        this.thread = thread;
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

    /**
     * Getter for the log message class name.
     *
     * @return The log message class name.
     */
    @XmlElement(name = "class")
    public String getClassName() {
        return className;
    }

    /**
     * Setter for the log message class name.
     *
     * @param className The log message class name.
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * Getter for the log message class line number
     *
     * @return The log message class line number.
     */
    @XmlElement(name = "line")
    public String getLineNumber() {
        return lineNumber;
    }

    /**
     * Setter for the log message class line number.
     *
     * @param lineNumber The log message class lineNumber.
     */
    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * Getter for the log message text.
     *
     * @return The log message text.
     */
    @XmlElement(name = "message")
    public String getMessage() {
        return message;
    }

    /**
     * Setter for the log message text.
     *
     * @param message The log message text.
     */
    public void setMessage(String message) {
        this.message = message;
    }

    @XmlElement(name = "time_ms")
    public long getTimeMS() {
        return timeMS;
    }

    public void setTimeMS(long timeMS) {
        this.timeMS = timeMS;
    }

    @XmlElement(name = "_facility")
    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }
    
    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }
	
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (className != null && !className.isEmpty()) {
            //service logs
            sb.append(getTime());
            sb.append(LogConstants.GAP);
            sb.append(nodeId);
            sb.append(LogConstants.GAP);
            sb.append(svcName);
            sb.append(LogConstants.GAP);
            sb.append(LogConstants.OPEN_SQUARE);
            sb.append(thread);
            sb.append(LogConstants.CLOSE_SQUARE);
            sb.append(LogConstants.GAP);
            sb.append(severity);
            sb.append(LogConstants.GAP);
            sb.append(className);
            sb.append(LogConstants.GAP);
            sb.append(LogConstants.OPEN_ROUND);
            sb.append(LogConstants.LINE);
            sb.append(LogConstants.GAP);
            sb.append(lineNumber);
            sb.append(LogConstants.CLOSE_ROUND);
            sb.append(LogConstants.GAP);
            sb.append(message);
        } else {
            //system logs
            sb.append(getTime());
            sb.append(LogConstants.GAP);
            sb.append(nodeId);
            sb.append(LogConstants.GAP);
            sb.append(LogConstants.OPEN_SQUARE);
            sb.append(facility);
            sb.append(LogConstants.CLOSE_SQUARE);
            sb.append(LogConstants.GAP);
            sb.append(severity);
            sb.append(LogConstants.GAP);
            sb.append(svcName);
            sb.append(LogConstants.COLON);
            sb.append(LogConstants.GAP);
            sb.append(message);
        }
        return sb.toString();
    }
}
