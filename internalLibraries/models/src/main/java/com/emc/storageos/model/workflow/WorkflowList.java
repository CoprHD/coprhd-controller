/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.workflow;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "workflows")
public class WorkflowList {
    private List<WorkflowRestRep> workflows;

    public WorkflowList() {
    }

    public WorkflowList(List<WorkflowRestRep> workflows) {
        this.workflows = workflows;
    }

    @XmlElement(name = "workflow")
    public List<WorkflowRestRep> getWorkflows() {
        if (workflows == null) {
            workflows = new ArrayList<WorkflowRestRep>();
        }
        return workflows;
    }

    public void setWorkflows(List<WorkflowRestRep> workflows) {
        this.workflows = workflows;
    }
}
