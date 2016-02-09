/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to Isilon Devices
 * <p/>
 * Remember to add the English message associated to the method in IsilonErrors.properties and use the annotation {@link DeclareServiceCode}
 * to set the service code associated to this error condition. You may need to create a new service code if there is no an existing one
 * suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface IsilonErrors {
    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public ServiceError doExpandFSFailed(final URI id);
    
    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public ServiceError unableToUpdateFileSystem(final URI id);

    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public ServiceError unableToExpandFileSystem();

    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public ServiceError unableToCreateFileShare();

    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public ServiceError unableToGetPhysicalInventory(URI storageId);

    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public ServiceError jobFailed(final String operationName);

    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public ServiceError unSupportedOperation(final String oprationName);

}
