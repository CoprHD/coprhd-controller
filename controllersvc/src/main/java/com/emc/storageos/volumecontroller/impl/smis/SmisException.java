/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This exception is used to indicate an error while waiting for an asynchronous
 * SMIS job to complete.
 *
 * @see SmisCommandHelper#invokeMethodSynchronously(com.emc.storageos.db.client.model.StorageSystem,
 *      javax.cim.CIMObjectPath, String, javax.cim.CIMArgument[],
 *      javax.cim.CIMArgument[])
 * @author elalih
 */
public class SmisException extends DeviceControllerException {

    private static final long serialVersionUID = -690567868124567639L;

    /** Holds the methods used to create SMIS related exceptions */
    public static final SmisExceptions exceptions = ExceptionMessagesProxy.create(SmisExceptions.class);

    /** Holds the methods used to create SMIS related error conditions */
    public static final SmisErrors errors = ExceptionMessagesProxy.create(SmisErrors.class);

    private SmisException(final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
	public SmisException(String pattern, Object[] parameters) {
		super(ServiceCode.SMIS_COMMAND_ERROR, pattern, parameters);
	}

    @Deprecated
	public SmisException(ServiceCode serviceCode, String pattern, Object[] parameters) {
		super(serviceCode == ServiceCode.UNFORSEEN_ERROR ? ServiceCode.SMIS_COMMAND_ERROR : serviceCode, pattern, parameters);
	}

    @Deprecated
	public SmisException() {
		super(ServiceCode.SMIS_COMMAND_ERROR, "No details available for this error", null);
	}

    @Deprecated
	public SmisException(String msg) {
		super(ServiceCode.SMIS_COMMAND_ERROR, msg, null);
	}

    @Deprecated
	public SmisException(Throwable cause) {
		super(ServiceCode.SMIS_COMMAND_ERROR, cause, "Caused by: {0}",
				new Object[] { cause.getMessage() });
	}

    @Deprecated
	public SmisException(String msg, Throwable cause) {
		super(ServiceCode.SMIS_COMMAND_ERROR, cause, "{0}. Caused by: {1}",
				new Object[] { msg, cause.getMessage() });
	}
}
