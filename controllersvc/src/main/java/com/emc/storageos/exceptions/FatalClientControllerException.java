/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class FatalClientControllerException extends ClientControllerException {
    private static final long serialVersionUID = 4952552554053446146L;

    protected FatalClientControllerException(ServiceCode code, Throwable cause, String detailBase,
            String detailKey, Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
