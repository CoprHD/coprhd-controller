/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("WorkflowStepData")
public class WorkflowStepData extends DataObject {
    /** Workflow to which this WorklowStep belongs. */
    private URI workflowId;
    /** A unique step id identifying this step, or the unique step data. */
    private String stepId;
    /** Serialized data. */
    private byte[] data;
    
    @Name("workflow")
    @RelationIndex(cf="WorkflowRelationIndex", type=Workflow.class)
    public URI getWorkflowId() {
        return workflowId;
    }
    public void setWorkflowId(URI workflowId) {
        this.workflowId = workflowId;
        setChanged("workflow");
    }

    @Name("stepId")
    @AlternateId("WorkflowStepDataIndex")
    public String getStepId() {
        return stepId;
    }
    public void setStepId(String stepId) {
        this.stepId = stepId;
        setChanged("stepId");
    }

    @Name("data")
    public byte[] getData() {
        return data;
    }
    public void setData(byte[] data) {
        this.data = data;
        setChanged("data");
    }
    

}
