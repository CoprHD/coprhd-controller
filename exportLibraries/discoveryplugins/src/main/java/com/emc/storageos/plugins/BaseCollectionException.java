/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.plugins;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * BaseCollectionException should be used by ICommunicationInterfaces to
 * indicate a discovery failure. It should be extend for specific error
 * conditions, and each subclass should set the errorCode to a well-known error
 * code that the Exception corresponds to.
 * 
 */
public abstract class BaseCollectionException extends DiscoveryException {
    /**
     * An error code representing the error that occurred.
     * 
     */
    protected int _errorCode;

    private static final long serialVersionUID = 1L;

    /**
     * CommunicationInterface implementations should derive exceptions from
     * BaseCollectionException and throw them to indicate failures. The derived
     * exception should specify a value for errorCode and return that value in
     * getErrorCode.
     * 
     * @return a well-known error core associated with the exception
     */
    public abstract int getErrorCode();

    protected BaseCollectionException(final boolean retryable, final ServiceCode code, final Throwable cause, final String detailBase,
            final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

    /**
     * Default Constructor
     */
    @Deprecated
    public BaseCollectionException() {
        super(false, ServiceCode.DISCOVERY_ERROR, null, null, null, null);
    }

    /**
     * Constructor accepting a message and a causing Exception
     * 
     * @param message
     *            Message associated with the Exception
     * @param cause
     *            Exception that caused this to occur
     */
    @Deprecated
    public BaseCollectionException(String message, Throwable cause) {
        super(false, ServiceCode.DISCOVERY_ERROR, cause, null, message, null);
    }

    /**
     * Constructor accepting a message
     * 
     * @param message
     *            Message associated with the Exception
     */
    @Deprecated
    public BaseCollectionException(String message) {
        super(false, ServiceCode.DISCOVERY_ERROR, null, null, message, null);
    }

    /**
     * Constructor accepting a causing Exception
     * 
     * @param cause
     *            Exception that caused this to occur
     */
    @Deprecated
    public BaseCollectionException(Throwable cause) {
        super(false, ServiceCode.DISCOVERY_ERROR, cause, null, null, null);
    }
}
