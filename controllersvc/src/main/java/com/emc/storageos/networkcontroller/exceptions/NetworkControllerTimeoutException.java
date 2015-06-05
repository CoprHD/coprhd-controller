/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.networkcontroller.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class NetworkControllerTimeoutException extends NetworkDeviceControllerException {

	private static final long serialVersionUID = -690567868124567639L;

	protected NetworkControllerTimeoutException(final ServiceCode code, final Throwable cause,
			final String detailBase, final String detailKey, final Object[] detailParams) {
		super(code, cause, detailBase, detailKey, detailParams);
	}
}
