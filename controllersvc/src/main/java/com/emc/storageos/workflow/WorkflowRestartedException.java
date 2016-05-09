/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.workflow;

import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Thrown if a thread tries to create a Workflow that already exists and is running.
 */
public class WorkflowRestartedException extends WorkflowException {

    private static final long serialVersionUID = -8346457868124567639L;

	 public static final WorkflowExceptions exceptions = ExceptionMessagesProxy
	            .create(WorkflowExceptions.class);

    protected WorkflowRestartedException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public WorkflowRestartedException(String pattern, Object[] parameters) {
        super(ServiceCode.WORKFLOW_RESTARTED_ERROR, pattern, parameters);
    }

    @Deprecated
    public WorkflowRestartedException(ServiceCode serviceCode, String pattern, Object[] parameters) {
        super(serviceCode == ServiceCode.UNFORSEEN_ERROR ? ServiceCode.WORKFLOW_RESTARTED_ERROR : serviceCode, pattern, parameters);
    }

    @Deprecated
    public WorkflowRestartedException(String msg) {
        super(ServiceCode.WORKFLOW_RESTARTED_ERROR, msg, null);
    }
}
