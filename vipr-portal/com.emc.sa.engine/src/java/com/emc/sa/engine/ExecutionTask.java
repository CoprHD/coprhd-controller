/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.engine;

import org.apache.log4j.Logger;

public abstract class ExecutionTask<T> {
    
    private Logger log;
    private String name;
    private String detail;
    
    public ExecutionTask() {
        this.name = getLocalizedName();
        try {
            provideDetailArgs();            
        }
        catch (Throwable t) {
            log.debug("Unable to set default detail message for "+this.getClass().getCanonicalName());
        }
    }

    public ExecutionTask(String nameKey) {
        this.name = getMessage(nameKey);
        try {
            provideDetailArgs();            
        }
        catch (Throwable t) {
            log.debug("Unable to set default detail message for "+this.getClass().getCanonicalName());
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String nameKey, Object... args) {
        this.name = getMessage(nameKey, args);
    }
    
    public void provideNameArgs(Object... args) {
        this.name = getMessage(getNameKey(), args);
    }

    public String getDetail() {
        return detail;
    }
    
    public void setDetail(String detailKey, Object... args) {
        this.detail = getMessage(detailKey, args);
    }

    public void provideDetailArgs(Object... args) {
        this.detail = getMessage(getDetailKey(), args);
    }
    
    public T executeTask() throws Exception {
        execute();
        return null;
    }
    
    public void execute() throws Exception {
    }

    protected IllegalStateException stateException(String messageKey, Object... args) {
        return new IllegalStateException(getMessage(messageKey, args));
    }

    protected final Logger getLog() {
        if (log == null) {
            log = Logger.getLogger(getClass());
        }
        return log;
    }

    protected void logDebug(String messageKey, Object... args) {
        getExecutionContext().logDebug(messageKey, args);
    }

    protected void logInfo(String messageKey, Object... args) {
        getExecutionContext().logInfo(messageKey, args);
    }

    protected void logWarn(String messageKey, Object... args) {
        getExecutionContext().logWarn(messageKey, args);
    }

    protected void logError(String messageKey, Object... args) {
        getExecutionContext().logError(messageKey, args);
    }

    protected void logError(Throwable cause, String messageKey, Object... args) {
        getExecutionContext().logError(messageKey, args);
    }

    protected ExecutionContext getExecutionContext() {
        return ExecutionUtils.currentContext();
    }
    
    protected String getMessage(String messageKey, Object...args) {
        return ExecutionUtils.getMessage(messageKey, args);
    }

    protected void debug(Throwable t, String messageKey, Object... args) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(getMessage(messageKey, args), t);
        }
    }

    protected void info(Throwable t, String messageKey, Object... args) {
        if (getLog().isInfoEnabled()) {
            getLog().info(getMessage(messageKey, args), t);
        }
    }

    protected void warn(Throwable t, String messageKey, Object... args) {
        getLog().warn(getMessage(messageKey, args), t);
    }

    protected void error(Throwable t, String messageKey, Object... args) {
        getLog().error(getMessage(messageKey, args), t);
    }

    protected void debug(String messageKey, Object... args) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(getMessage(messageKey, args));
        }
    }

    protected void info(String messageKey, Object... args) {
        if (getLog().isInfoEnabled()) {
            getLog().info(getMessage(messageKey, args));
        }
    }

    protected void warn(String messageKey, Object... args) {
        getLog().warn(getMessage(messageKey, args));
    }

    protected void error(String messageKey, Object... args) {
        getLog().error(getMessage(messageKey, args));
    }

    protected void debug(Throwable t) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(t, t);
        }
    }

    protected void info(Throwable t) {
        if (getLog().isInfoEnabled()) {
            getLog().info(t, t);
        }
    }

    protected void warn(Throwable t) {
        getLog().warn(t, t);
    }

    protected void error(Throwable t) {
        getLog().error(t, t);
    }

    public String getLocalizedName() {
        return getMessage(getNameKey());
    }
    
    protected String getNameKey() {
        return String.format("%s.title", getClass().getSimpleName());
    }
    
    protected String getDetailKey() {
        return String.format("%s.detail", getClass().getSimpleName());
    }
    
}
