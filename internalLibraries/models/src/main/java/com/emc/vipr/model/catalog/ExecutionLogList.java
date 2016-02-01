/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "execution_logs")
public class ExecutionLogList {

    private List<ExecutionLogRestRep> executionLogs;

    public ExecutionLogList() {
    }

    public ExecutionLogList(List<ExecutionLogRestRep> executionLogs) {
        this.executionLogs = executionLogs;
    }

    /**
     * List of execution logs
     * 
     */
    @XmlElement(name = "execution_log")
    public List<ExecutionLogRestRep> getExecutionLogs() {
        if (executionLogs == null) {
            executionLogs = new ArrayList<>();
        }
        return executionLogs;
    }

    public void setExecutionLogs(List<ExecutionLogRestRep> executionLogs) {
        this.executionLogs = executionLogs;
    }
}
