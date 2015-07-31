/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class ComputeSystemControllerTimeoutException extends ComputeSystemControllerException {

    private static final long serialVersionUID = 8944383000280743104L;

    protected ComputeSystemControllerTimeoutException(ServiceCode code, Throwable cause, String detailBase,
            String detailKey, Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
        // TODO Auto-generated constructor stub
    }

}
