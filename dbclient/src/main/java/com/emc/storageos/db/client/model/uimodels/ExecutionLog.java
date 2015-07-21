/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.model.valid.EnumType;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.util.Date;

/**
 * General log message.
 * 
 * @author jonnymiller
 */
@Cf("ExecutionLog")
public class ExecutionLog extends ModelObject {

    public static final String DATE = "date";
    public static final String LEVEL = "level";
    public static final String MESSAGE = "message";
    public static final String STACK_TRACE = "stackTrace";
    public static final String PHASE = "phase";

    private Date date;
    private String level;
    private String message;
    private String stackTrace;
    private String phase;

    @Name(DATE)
    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
        setChanged(DATE);
    }

    @Name(MESSAGE)
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        setChanged(MESSAGE);
    }

    @Name(STACK_TRACE)
    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
        setChanged(STACK_TRACE);
    }

    public void addStackTrace(Throwable cause) {
        setStackTrace(ExceptionUtils.getFullStackTrace(cause));
    }

    @EnumType(LogLevel.class)
    @Name(LEVEL)
    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
        setChanged(LEVEL);
    }

    @EnumType(ExecutionPhase.class)
    @Name(PHASE)
    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
        setChanged(PHASE);
    }

    public String toString() {
        if (StringUtils.isBlank(stackTrace)) {
            return String.format("%s - %s - %s", date, level, message);
        }
        else {
            return String.format("%s - %s - %s\n%s", date, level, message, stackTrace);
        }
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] {getLabel(), getId() };
    }        
    
    public static enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}
