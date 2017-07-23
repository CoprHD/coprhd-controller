/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link VMAXException}s
 * <p/>
 * Remember to add the English message associated to the method in VMAXExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */

@MessageBundle
public interface VMAXExceptions {

    @DeclareServiceCode(ServiceCode.UNISPHERE_PROVIDER_UNAVAILABLE)
    VMAXException authenticationFailure(String storageSystemForDisplay);

    @DeclareServiceCode(ServiceCode.UNISPHERE_API_ERROR)
    VMAXException resourceNotFound(String storageSystemForDisplay);

    @DeclareServiceCode(ServiceCode.VMAX_NDM_FAILURE)
    VMAXException invalidResponseFromUnisphere(final String message);

    @DeclareServiceCode(ServiceCode.UNISPHERE_API_ERROR)
    VMAXException internalError(String uri, String message);

    @DeclareServiceCode(ServiceCode.VMAX_NDM_FAILURE)
    public VMAXException unsupportedVersion(final String version, final String minimumVersion);

    @DeclareServiceCode(ServiceCode.VMAX_NDM_FAILURE)
    public VMAXException scanFailed(String ip, Throwable t);

    @DeclareServiceCode(ServiceCode.VMAX_NDM_FAILURE)
    public VMAXException discoveryNotSupported();
}
