/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.smis;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;



/**
 * To-Do: define errorCodes.
 */
public class SMIPluginException extends BaseCollectionException {
    public static final int ERRORCODE_START_WBEMEXCEPTION          = 0;
    public static final int ERRORCODE_PARSERCONFIGURATIONEXCEPTION = 1;
    public static final int ERRORCODE_XML_PARSER_ERROR             = 2;
    public static final int ERRORCODE_WBEMEXCEPTION                = 3;
    public static final int ERRORCODE_ILLEGALARGUMENTEXCEPTION     = 4;
    public static final int ERRORCODE_ILLEGALACCESSEXCEPTION       = 5;
    public static final int ERRORCODE_INVOCATIONTARGETEXCEPTION    = 6;
    public static final int ERRORCODE_CASSANDRAINJECTIONERROR      = 7;
	public static final int ERRORCODE_OPERATIONFAILED              = 8;
    public static final int ERRORCODE_PROVIDER_NOT_SUPPORTED       = 9;
    public static final int ERRORCODE_NO_WBEMCLIENT                = 10;
    public static final int ERRORCODE_FIRMWARE_NOT_SUPPORTED       = 11;

    protected SMIPluginException(final boolean retryable,  final ServiceCode serviceCode, final int errorCode,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, serviceCode, cause, detailBase, detailKey, detailParams);
        _errorCode = errorCode;
    }

    /**
     * Constructor.
     *
     * @param message
     *            : String we want to print in log file when an exception
     *            occurs.
     */
    public SMIPluginException (String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param errorCode
     *            : Integer Constant for the error condition.
     * @param cause
     *            : The actual exception which has caused the SMIPluginException
     * @param message
     *            : String we want to print in log file when an exception
     *            occurs.
     */
    @Deprecated
    public SMIPluginException ( int errorCode, Throwable cause, String message ) {
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
    public SMIPluginException ( final String message, int errorCode ) {
        super(message);
        _errorCode = errorCode;
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
