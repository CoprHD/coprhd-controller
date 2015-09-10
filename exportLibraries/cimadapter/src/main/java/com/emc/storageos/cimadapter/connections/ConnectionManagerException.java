/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.connections;

import com.emc.storageos.cimadapter.exceptions.CIMAdapterException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception that is thrown when a {@link ConnectionManager} API fails.
 */
public class ConnectionManagerException extends CIMAdapterException {

    // For serializable classes.
    private static final long serialVersionUID = 1L;

    protected ConnectionManagerException(final boolean retryable, final ServiceCode code, final Throwable cause, final String detailBase,
            final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

    /**
     * Constructor.
     * 
     * @param msg Exception message.
     */
    @Deprecated
    public ConnectionManagerException(String msg) {
        super(false, ServiceCode.CIM_CONNECTION_MANAGER_ERROR, null, null, msg, null);
    }

    /**
     * Constructor.
     * 
     * @param msg Exception message.
     * @param t Source throwable.
     */
    @Deprecated
    public ConnectionManagerException(String msg, Throwable t) {
        super(false, ServiceCode.CIM_CONNECTION_MANAGER_ERROR, t, null, msg, null);
    }

    /**
     * Constructor.
     * 
     * @param t Source throwable.
     */
    @Deprecated
    public ConnectionManagerException(Throwable t) {
        super(false, ServiceCode.CIM_CONNECTION_MANAGER_ERROR, t, null, t.getLocalizedMessage(), null);
    }
}
