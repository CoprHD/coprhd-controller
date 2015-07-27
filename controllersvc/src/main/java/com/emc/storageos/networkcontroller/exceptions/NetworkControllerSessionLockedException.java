/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class NetworkControllerSessionLockedException extends NetworkDeviceControllerException {

	private static final long serialVersionUID = -6905306922124567639L;

	protected NetworkControllerSessionLockedException(final ServiceCode code,
			final Throwable cause, final String detailBase, final String detailKey,
			final Object[] detailParams) {
		super(code, cause, detailBase, detailKey, detailParams);
	}
}
