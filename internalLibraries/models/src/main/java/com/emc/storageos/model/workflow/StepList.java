/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.workflow;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "workflow_steps")
public class StepList {
    private List<WorkflowStepRestRep> steps;

    public StepList() {
    }

    public StepList(List<WorkflowStepRestRep> steps) {
        this.steps = steps;
    }

    /**
     * A list of Workflow Steps.
     * 
     * @valid none
     */
    @XmlElement(name = "workflow_step")
    public List<WorkflowStepRestRep> getSteps() {
        if (steps == null) {
            steps = new ArrayList<WorkflowStepRestRep>();
        }
        return steps;
    }

    public void setSteps(List<WorkflowStepRestRep> steps) {
        this.steps = steps;
    }
}
