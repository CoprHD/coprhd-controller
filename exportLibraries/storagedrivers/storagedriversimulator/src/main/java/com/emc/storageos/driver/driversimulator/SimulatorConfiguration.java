/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.driversimulator;

public class SimulatorConfiguration {
    private Boolean simulateAsynchronousResponses = false;
    private Boolean simulateFailures = false;
    private Integer maxAsynchronousLookups = 5;
    
    public SimulatorConfiguration() {
    }

    public void setSimulateAsynchronousResponses(Boolean simAsynchronous) {
        simulateAsynchronousResponses = simAsynchronous;
    }
    
    public Boolean getSimulateAsynchronousResponses() {
        return simulateAsynchronousResponses;
    }

    public void setSimulateFailures(Boolean simFailures) {
        simulateFailures = simFailures;
    }

    public Boolean getSimulateFailures() {
        return simulateFailures;
    }

    public void setMaxAsynchronousLookups(Integer maxAsyncLookups) {
        maxAsynchronousLookups = maxAsyncLookups;
    }
    
    public Integer getMaxAsynchronousLookups() {
        return maxAsynchronousLookups;
    }    
}
