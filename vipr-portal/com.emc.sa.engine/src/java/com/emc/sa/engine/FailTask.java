/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

/**
 * An ExecutionTask which can be used to fail the currently running order. 
 */
public class FailTask extends ExecutionTask<Void> {
    
    private static final String DETAIL_SUFFIX = ".detail";
    private static final String MESSAGE_SUFFIX = ".message";
    
    private final String failMessage;
    private final Exception exception;
    
    public FailTask(String taskNameKey, Object[] detailArgs, Object[] failMessageArgs) {
        this(taskNameKey, null, detailArgs, failMessageArgs);
    }
    
    public FailTask(String taskNameKey, Exception exception, Object[] detailArgs, Object[] failMessageArgs) {
        setName(taskNameKey);
        setDetail(taskNameKey+DETAIL_SUFFIX, detailArgs);
        this.failMessage = getMessage(taskNameKey+MESSAGE_SUFFIX, failMessageArgs);
        if (exception == null) {
            exception = stateException("failTask.stateException", getMessage(taskNameKey), this.failMessage);
        }
        this.exception = exception;
    }
    
    public void execute() throws Exception {
        logError(failMessage);
        throw exception;
    }

}
