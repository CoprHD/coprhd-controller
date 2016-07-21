/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.driversimulator.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.storagedriver.DriverTask;

abstract public class BaseDriverSimulatorOperation implements DriverSimulatorOperation {

    protected DriverTask _task;
    private Integer _lookupCount = 0;
    private String _type;
    private static final Logger _log = LoggerFactory.getLogger(BaseDriverSimulatorOperation.class);
    
    public BaseDriverSimulatorOperation(String type) {
        _type = type;
    }
    
    @Override
    public String getType() {
        return _type;
    }
    
    @Override
    public DriverTask getDriverTask() {
        return _task;
    }
    
    @Override
    public Integer getLookupCount() {
        return _lookupCount;
    }
    
    @Override
    public void incrementLookupCount() {
        _lookupCount++;
    }
    
    @Override
    public DriverTask doSuccess(String msg) {
        _task.setStatus(DriverTask.TaskStatus.READY);
        _log.info(msg);
        _task.setMessage(msg);
        return _task;
    }
    
    @Override
    public DriverTask doFailure(String msg) {
        _task.setStatus(DriverTask.TaskStatus.FAILED);
        _log.info(msg);
        _task.setMessage(msg);
        return _task;
    }
}
