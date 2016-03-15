/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.rackhd;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link RackHdException}s
 * <p/>
 * Remember to add the English message associated to the method in RackHdExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface RackHdExceptions {

    @DeclareServiceCode(ServiceCode.RACKHD_API_FAILURE)
    public RackHdException authenticationFailure(String uri) ;

    @DeclareServiceCode(ServiceCode.RACKHD_API_FAILURE)
    public RackHdException resourceNotFound(String uri);

    @DeclareServiceCode(ServiceCode.RACKHD_API_FAILURE)
    public RackHdException internalError(String uri, String error);

    @DeclareServiceCode(ServiceCode.RACKHD_API_FAILURE)
    public RackHdException noActiveStorageProvider(String systemName);
}
