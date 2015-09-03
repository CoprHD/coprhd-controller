/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_execution_windows")
public class ExecutionWindowBulkRep extends BulkRestRep {
    
    private List<ExecutionWindowRestRep> executionWindows;
    
    public ExecutionWindowBulkRep() {
        
    }

    /**
     * List of execution windows
     * @valid none
     * @return List of execution windows
     */
    @XmlElement(name = "execution_window")
    public List<ExecutionWindowRestRep> getExecutionWindows() {
        if (executionWindows == null) {
            executionWindows = new ArrayList<>();
        }
        return executionWindows;
    }

    public void setExecutionWindows(List<ExecutionWindowRestRep> executionWindows) {
        this.executionWindows = executionWindows;
    }
    
    public ExecutionWindowBulkRep(List<ExecutionWindowRestRep> executionWindows) {
        this.executionWindows = executionWindows;
    }
}
