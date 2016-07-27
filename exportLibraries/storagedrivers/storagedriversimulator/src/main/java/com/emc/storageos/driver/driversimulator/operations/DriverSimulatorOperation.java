/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.driversimulator.operations;

import com.emc.storageos.storagedriver.DriverTask;

public interface DriverSimulatorOperation {
    
    public String getType();
    
    public DriverTask getDriverTask();
    
    public Integer getLookupCount();
    
    public void incrementLookupCount();
    
    public void updateOnAsynchronousSuccess();
    
    public DriverTask doSuccess(String msg);

    public DriverTask doFailure(String msg);
    
    public String getSuccessMessage(Object... args);
    
    public String getFailureMessage(Object... args);
}
