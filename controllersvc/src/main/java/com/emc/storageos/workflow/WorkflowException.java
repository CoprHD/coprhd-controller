/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.workflow;

import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class WorkflowException extends DeviceControllerException {

    private static final long serialVersionUID = -8346457868124567639L;

    public static final WorkflowExceptions exceptions = ExceptionMessagesProxy
            .create(WorkflowExceptions.class);

    protected WorkflowException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public WorkflowException(String pattern, Object[] parameters) {
        super(ServiceCode.WORKFLOW_ERROR, pattern, parameters);
    }

    @Deprecated
    public WorkflowException(ServiceCode serviceCode, String pattern, Object[] parameters) {
        super(serviceCode == ServiceCode.UNFORSEEN_ERROR ? ServiceCode.WORKFLOW_ERROR : serviceCode, pattern, parameters);
    }

    @Deprecated
    public WorkflowException() {
        super(ServiceCode.WORKFLOW_ERROR, "No details available for this error", null);
    }

    @Deprecated
    public WorkflowException(String msg) {
        super(ServiceCode.WORKFLOW_ERROR, msg, null);
    }

    @Deprecated
    public WorkflowException(Throwable cause) {
        super(ServiceCode.WORKFLOW_ERROR, cause, "Caused by: {0}",
                new Object[] { cause.getMessage() });
    }

    @Deprecated
    public WorkflowException(String msg, Throwable cause) {
        super(ServiceCode.WORKFLOW_ERROR, cause, "{0}. Caused by: {1}",
                new Object[] { msg, cause.getMessage() });
    }
}
