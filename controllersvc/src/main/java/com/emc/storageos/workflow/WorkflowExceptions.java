/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.workflow;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface WorkflowExceptions {

    @DeclareServiceCode(ServiceCode.WORKFLOW_NOT_FOUND)
    public WorkflowException workflowNotFound(String id);

    @DeclareServiceCode(ServiceCode.WORKFLOW_IN_WRONG_STATE)
    public WorkflowException workflowRollbackInWrongState(String id, String expectedState, String actualState);

    @DeclareServiceCode(ServiceCode.WORKFLOW_CANNOT_BE_ROLLED_BACK)
    public WorkflowException workflowRollbackNotInitiated(String uri);

    @DeclareServiceCode(ServiceCode.WORKFLOW_CANNOT_BE_ROLLED_BACK)
    public WorkflowException innerWorkflowRollbackError(String uri, String messages);
    
    @DeclareServiceCode(ServiceCode.WORKFLOW_TERMINATED_DR_FAILOVER)
    public WorkflowException workflowTerminatedForFailover(String uri);
    
}
