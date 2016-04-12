/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create an error condition in the
 * synchronous aspect of the controller that will be associated with an HTTP
 * status of 503
 * <p/>
 * Remember to add the English message associated to the method in RetryableSecurityExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface RetryableSecurityExceptions {

    @DeclareServiceCode(ServiceCode.SECURITY_REQUIRED_SERVICE_UNAVAILABLE)
    public RetryableSecurityException requiredServiceUnvailable(final String serviceName);

    @DeclareServiceCode(ServiceCode.SECURITY_REQUIRED_SERVICE_UNAVAILABLE)
    public RetryableSecurityException requiredServiceUnvailable(final String serviceName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_REQUIRED_SERVICE_UNAVAILABLE)
    public RetryableSecurityException unableToNotifyTokenOriginatorForLogout(final String vdcId);

    @DeclareServiceCode(ServiceCode.SECURITY_KEYSTORE_UNAVAILABLE)
    public RetryableSecurityException keystoreUnavailable();

    @DeclareServiceCode(ServiceCode.SECURITY_KEYSTORE_UNAVAILABLE)
    public RetryableSecurityException updatingKeystoreWhileClusterIsUnstable();

    @DeclareServiceCode(ServiceCode.SECURITY_REQUIRED_SERVICE_UNAVAILABLE)
    public RetryableSecurityException failToUpdateKeyStoreDueToStandbyPause();
}
