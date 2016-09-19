/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link com.emc.storageos.svcs.errorhandling.model.ServiceError}s
 * related to VPLEX Devices
 * <p/>
 * Remember to add the English message associated to the method in VPlexErrors.properties and use the annotation
 * {@link com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode} to set the service code associated to this error condition.
 * You may need to create a new service code if there is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface DeviceDataCollectionErrors {

    @DeclareServiceCode(ServiceCode.CONTROLLER_DATA_COLLECTION_ERROR)
    public ServiceError failedToEnqueue(final String jobType, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_DATA_COLLECTION_ERROR)
    public ServiceError scanFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_DATA_COLLECTION_ERROR)
    public ServiceError scanLockFailed();
}
