/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to Data Domain Devices
 * <p/>
 * Remember to add the English message associated to the method in DataDomainApiErrors.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface DataDomainApiErrors {

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError doExpandFSFailed(final String msg);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_INVALID_OPERATION)
    public ServiceError operationNotSupported();

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError doCreateSnapshotExportFailed();

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError operationFailedProviderInaccessible(final String op);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError doShrinkFSFailed(Long currSize, Long newSize);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError doFailedToGetCurrSize();

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError doDeleteSnapshotFailed();

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError doDeleteSnapshotFailedNoFSName();

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError doDeleteSnapshotFailedNoSnapName();

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError exportUpdateFailedRepeatedClients();

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    public ServiceError exportUpdateFailedAddingExistingClient();

}
