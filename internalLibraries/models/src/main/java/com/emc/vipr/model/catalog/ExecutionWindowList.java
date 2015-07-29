/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "execution_windows")
public class ExecutionWindowList {

    private List<NamedRelatedResourceRep> executionWindows;

    public ExecutionWindowList() {
    }

    public ExecutionWindowList(List<NamedRelatedResourceRep> executionWindows) {
        this.executionWindows = executionWindows;
    }

    /**
     * List of execution windows
     * 
     * @valid none
     */
    @XmlElement(name = "execution_windows")
    public List<NamedRelatedResourceRep> getExecutionWindows() {
        if (executionWindows == null) {
            executionWindows = new ArrayList<>();
        }
        return executionWindows;
    }

    public void setExecutionWindows(List<NamedRelatedResourceRep> executionWindows) {
        this.executionWindows = executionWindows;
    }
}
