/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import com.emc.storageos.driver.scaleio.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.driver.scaleio.errorhandling.annotations.MessageBundle;

/**
 * This interface holds all the methods used to create an error condition that
 * will be associated with an HTTP status of Method Not Allowed (405)
 * <p/>
 * Remember to add the English message associated to the method in MethodNotAllowedExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface MethodNotAllowedExceptions {

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupported();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedWithReason(String reason);

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException maximumNumberSnapshotsReached();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForVplexVolumes();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForSubtenants();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException rotateKeyCertInMultiVdcsIsNotAllowed();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException vPoolDoesntSupportProtocol(String reason);

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException noEnoughAliveNodesForRejoin();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException noVacancyNodesForRejoin();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForRP();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForVNXE();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForExtremeIO();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForScaleIO();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForXIV();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForOpenstack();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForVMAX();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForVNX();

    @DeclareServiceCode(ServiceCode.API_METHOD_NOT_SUPPORTED)
    MethodNotAllowedException notSupportedForHDS();
}
