/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.hds;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to HDS Devices
 * <p/>
 * Remember to add the English message associated to the method in HDSErrors.properties and use the annotation {@link DeclareServiceCode} to
 * set the service code associated to this error condition. You may need to create a new service code if there is no an existing one
 * suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface HDSErrors {

    @DeclareServiceCode(ServiceCode.HDS_COMMAND_ERROR)
    public ServiceError methodFailed(final String methodName, final String cause);

    @DeclareServiceCode(ServiceCode.HDS_COMMAND_ERROR)
    public ServiceError jobFailed(final String cause);
}
