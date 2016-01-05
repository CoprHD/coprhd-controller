/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.glance.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to Glance Devices
 * <p/>
 * Remember to add the English message associated to the method in
 * Glance.properties and use the annotation {@link DeclareServiceCode}
 * to set the service code associated to this error condition. You may need to
 * create a new service code if there is no an existing one suitable for your
 * error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section
 * in the Error Handling Wiki page:
 */

@MessageBundle
public interface GlanceErrors {
	
	@DeclareServiceCode(ServiceCode.GLANCE_OPERATION_FAILED)
    public ServiceError operationFailed(final String methodName, final String cause);
	
	@DeclareServiceCode(ServiceCode.GLANCE_JOB_FAILED)
    public ServiceError jobFailed(final String cause);

}
