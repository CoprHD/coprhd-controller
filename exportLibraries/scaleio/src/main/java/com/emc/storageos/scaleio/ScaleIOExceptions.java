/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.scaleio;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ScaleIOException}s
 * <p/>
 * Remember to add the English message associated to the method in ScaleIOExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface ScaleIOExceptions {

    @DeclareServiceCode(ServiceCode.SCALEIO_SCAN_FAILED)
    public ScaleIOException scanFailed(Throwable t);
    
    @DeclareServiceCode(ServiceCode.SCALEIO_SCAN_FAILED)
    public ScaleIOException discoveryFailed(Throwable t);
    
    @DeclareServiceCode(ServiceCode.SCALEIO_SCAN_FAILED)
    public ScaleIOException scaleioCliNotSupported();

    @DeclareServiceCode(ServiceCode.SCALEIO_CLI_NEEDS_TO_SPECIFY_MDM_CREDS)
    public ScaleIOException missingMDMCredentials();

    @DeclareServiceCode(ServiceCode.SCALEIO_CLI_INIT_WAS_NOT_CALLED)
    public ScaleIOException initWasNotCalled();
    
    @DeclareServiceCode(ServiceCode.SCALEIO_API_FAILURE)
    public ScaleIOException authenticationFailure(String uri) ;
    
    @DeclareServiceCode(ServiceCode.SCALEIO_API_FAILURE)
    public ScaleIOException resourceNotFound(String uri);
    
    @DeclareServiceCode(ServiceCode.SCALEIO_API_FAILURE)
    public ScaleIOException internalError(String uri, String error);
    
    @DeclareServiceCode(ServiceCode.SCALEIO_API_FAILURE)
    public ScaleIOException noActiveStorageProvider(String systemName);
}
