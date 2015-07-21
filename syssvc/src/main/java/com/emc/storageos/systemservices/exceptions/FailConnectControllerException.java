/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.exceptions;


import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception class is used by data node when it cannot connect to controller's cluster
 *
 */
public class FailConnectControllerException extends SyssvcException {

    private static final long serialVersionUID = 8966096971814607360L;
    
    protected FailConnectControllerException(final ServiceCode code, final Throwable cause, final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
