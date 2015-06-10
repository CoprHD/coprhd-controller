/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlType;
import java.util.Date;

@XmlType
public class ExecutionLogInfo {
    
    /**
     * Date for this log message
     */
    private Date date;              
    
    /**
     * Level for this log message
     */
    private String level;           
    
    /**
     * Log message
     */
    private String message;
    
    /**
     * Stacktrace for error log messages
     */
    private String stackTrace;      
    
    /**
     * Execution phase for this message
     */
    private String phase;          

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
}
