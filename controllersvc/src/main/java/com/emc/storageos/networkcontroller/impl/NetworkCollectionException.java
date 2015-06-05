/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * The collection exception that is specific to Network Systems.
 *
 * @author elalih
 *
 */
public class NetworkCollectionException extends BaseCollectionException {
	/**
	 * Not used yet anywhere. Just following the existing framework
	 */
	public static final int ERRORCODE_NETWORKOPERATIONFAILED  = 99;

    protected NetworkCollectionException(final boolean retryable,  final ServiceCode serviceCode,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, serviceCode, cause, detailBase, detailKey, detailParams);
    }

	@Deprecated
	public NetworkCollectionException() {
		super();
        _errorCode = ERRORCODE_NETWORKOPERATIONFAILED;
	}

	@Deprecated
	public NetworkCollectionException(String message, Throwable cause) {
		super(message, cause);
	}

	@Deprecated
	public NetworkCollectionException(String message) {
		super(message);
	}

	@Deprecated
	public NetworkCollectionException(Throwable cause) {
		super(cause);
	}

	@Override
	public int getErrorCode() {
		return _errorCode;
	}

}
