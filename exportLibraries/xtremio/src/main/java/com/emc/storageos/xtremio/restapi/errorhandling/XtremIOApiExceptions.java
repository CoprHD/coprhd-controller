/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface XtremIOApiExceptions {

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    XtremIOApiException authenticationFailure(String xtremIOUri);

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    XtremIOApiException resourceNotFound(String xtremIOUri);

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    XtremIOApiException internalError(String xtremIOUri, String message);

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    XtremIOApiException internaljsonParserError();

    @DeclareServiceCode(ServiceCode.XTREMIO_DISCOVERY_ERROR)
    XtremIOApiException discoveryFailed(String xtremIO);

    @DeclareServiceCode(ServiceCode.XTREMIO_DISCOVERY_ERROR)
    XtremIOApiException scanFailed(String xtremIO);

    @DeclareServiceCode(ServiceCode.XTREMIO_DISCOVERY_ERROR)
    XtremIOApiException moreThanOneClusterNotSupported(String xtremIO);

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    XtremIOApiException operationNotSupportedForVersion(String operationName);

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    XtremIOApiException noMgmtConnectionFound(String serialNumber);

}
