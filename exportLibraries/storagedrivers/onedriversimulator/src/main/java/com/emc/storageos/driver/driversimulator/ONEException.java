package com.emc.storageos.driver.driversimulator;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class ONEException extends InternalException {

	protected ONEException(boolean retryable, ServiceCode code, Throwable cause, String detailBase, String detailKey,
			Object[] detailParams) {
		super(retryable, code, cause, detailBase, detailKey, detailParams);
		// TODO Auto-generated constructor stub
	}

}
