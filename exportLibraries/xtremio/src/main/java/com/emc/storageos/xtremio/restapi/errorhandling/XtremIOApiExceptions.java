/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
    XtremIOApiException moreThanOneClusterNotSupported(String xtremIO);
    
    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    XtremIOApiException operationNotSupportedForVersion(String operationName);

}
