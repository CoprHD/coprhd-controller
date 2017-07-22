/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to VMAX Devices
 * <p/>
 * Remember to add the English message associated to the method in VMAXErrors.properties and use the annotation {@link DeclareServiceCode}
 * to
 * set the service code associated to this error condition. You may need to create a new service code if there is no an existing one
 * suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */

@MessageBundle
public interface VMAXErrors {

    @DeclareServiceCode(ServiceCode.UNISPHERE_PROVIDER_UNAVAILABLE)
    public ServiceError unableToCallStorageProvider(final String cause);
}
