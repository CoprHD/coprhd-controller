/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.Date;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "order_log")
public class OrderLogRestRep {
    
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

    @XmlElement(name = "date")
    public Date getDate() {
        return date;
    }
    public void setDate(Date date) {
        this.date = date;
    }

    @XmlElement(name = "level")
    public String getLevel() {
        return level;
    }
    public void setLevel(String level) {
        this.level = level;
    }

    @XmlElement(name = "message")
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

    @XmlElement(name = "phase")
    public String getPhase() {
        return phase;
    }
    public void setPhase(String phase) {
        this.phase = phase;
    }

    @XmlElement(name = "stack_trace")
    public String getStackTrace() {
        return stackTrace;
    }
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
}
