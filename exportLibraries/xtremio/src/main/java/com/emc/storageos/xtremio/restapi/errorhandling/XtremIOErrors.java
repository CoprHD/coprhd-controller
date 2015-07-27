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
package com.emc.storageos.xtremio.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;

import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface XtremIOErrors {
	@DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError createVolumeFailure(final String errMsg);
	
	@DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError deleteVolumeFailure(final String errMsg);
	
	@DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError expandVolumeFailure(final Throwable cause);
	
	@DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError createSnapshotFailure(final Throwable cause); 
}
