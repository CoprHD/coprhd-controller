/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.workflow;

import java.io.Serializable;

public class WorkflowStatusUpdateMessage implements Serializable {
    String state;
    String message;

    public WorkflowStatusUpdateMessage(Workflow.StepState s, String m) {
        state = s.name();
        message = m;
    }
}
