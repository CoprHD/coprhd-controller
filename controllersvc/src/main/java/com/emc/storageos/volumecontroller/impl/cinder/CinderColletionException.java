/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.cinder;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class CinderColletionException extends BaseCollectionException {

	private static final long serialVersionUID = -8969512249106652916L;

	/**
	 * @param retryable
	 * @param code
	 * @param cause
	 * @param detailBase
	 * @param detailKey
	 * @param detailParams
	 */
	public CinderColletionException(boolean retryable, ServiceCode code,
			Throwable cause, String detailBase, String detailKey,
			Object[] detailParams) {
		super(retryable, code, cause, detailBase, detailKey, detailParams);
	}

	/* (non-Javadoc)
	 * @see com.emc.storageos.plugins.BaseCollectionException#getErrorCode()
	 */
	@Override
	public int getErrorCode() {
		return -1;
	}

}
