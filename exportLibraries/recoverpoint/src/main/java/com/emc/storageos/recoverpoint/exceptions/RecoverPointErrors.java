/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.exceptions;

import java.net.URI;
import java.util.List;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to Recover Point Devices
 * <p/>
 * Remember to add the English message associated to the method in RecoverPointErrors.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface RecoverPointErrors {

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public ServiceError couldNotCreateProtectionOnVolumes(final List<URI> volumeURIs);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public ServiceError stepFailed(final String step);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public ServiceError rpNotSupportExportGroupInitiatorsAddOperation();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public ServiceError rpNotSupportExportGroupInitiatorsRemoveOperation();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public ServiceError methodNotSupported();
}
