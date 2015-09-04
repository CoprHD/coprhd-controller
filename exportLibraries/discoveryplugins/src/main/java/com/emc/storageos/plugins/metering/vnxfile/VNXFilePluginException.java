/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.plugins.metering.vnxfile;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 *
 * @TODO define error codes.
 */
public class VNXFilePluginException extends BaseCollectionException {

    public static final int ERRORCODE_ILLEGALARGUMENTEXCEPTION = 0;
    public static final int ERRORCODE_ILLEGALACCESSEXCEPTION = 1;
    public static final int ERRORCODE_INVOCATIONTARGETEXCEPTION = 2;
    public static final int ERRORCODE_INVALID_RESPONSE = 3;

    protected VNXFilePluginException(final boolean retryable, final ServiceCode serviceCode, final int errorCode,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, serviceCode, cause, detailBase, detailKey, detailParams);
        _errorCode = errorCode;
    }

    /**
     * Constructor.
     *
     * @param errorCode
     *            : Integer Constant for the error condition.
     * @param cause
     *            : The actual exception which has caused the
     *            VNXFilePluginException.
     * @param message
     *            : String we want to print in log file when an exception
     *            occurs.
     */
    @Deprecated
    public VNXFilePluginException(final int errorCode, final Throwable cause,
            final String message) {
        super(message, cause);
        _errorCode = errorCode;
    }

    /**
     * Constructor.
     *
     * @param message
     *            : String we want to print in log file when an exception
     *            occurs.
     * @param errorCode
     *            : Integer Constant for the error condition.
     *
     */
    @Deprecated
    public VNXFilePluginException(final String message, final int errorCode) {
        super(message);
        _errorCode = errorCode;
    }

    /**
     * Constructor.
     *
     * @param message
     *            : String we want to print in log file when an exception
     *            occurs.
     * @param cause
     *            : The actual exception which has caused the
     *            VNXFilePluginException.
     */
    @Deprecated
    public VNXFilePluginException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Getter for errorCode describing the error condition.
     *
     * @return int.
     */
    public int getErrorCode() {
        return _errorCode;
    }
}
