/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import com.emc.storageos.driver.scaleio.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.driver.scaleio.errorhandling.annotations.MessageBundle;

/**
 * This interface holds all the methods used to create an error condition that
 * will be associated with an HTTP status of Unauthorized (401)
 * <p/>
 * Remember to add the English message associated to the method in UnauthorizedExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface UnauthorizedExceptions {
    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException bindSearchGenericException();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException userSearchFailed();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException managerBindFailed();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException ldapCommunicationException();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException invalidProxyToken(final String user);

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException unauthenticatedRequestUnsignedInternalRequest();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException unauthenticatedRequestUnsignedInterVDCRequest();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException unauthenticatedRequestUseHTTPS();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException tokenNotFoundOrInvalidTokenProvided();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException methodNotAllowedOnThisNode();

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_TOKEN_ENCODING_ERROR)
    UnauthorizedException unableToEncodeToken(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_TOKEN_ENCODING_ERROR)
    UnauthorizedException unableToDecodeToken(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_TOKEN_ENCODING_ERROR)
    UnauthorizedException unableToDecodeTokenTheSignatureDoesNotValidate();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException maxNumberOfTokenExceededForUser();

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_SERVICE_ENCODING_ERROR)
    UnauthorizedException unableToVerifyTheServiceSignature();

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_SERVICE_ENCODING_ERROR)
    UnauthorizedException unableToGenerateTheServiceSignature();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException noTokenFoundForUserFromForeignVDC();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException exceedingErrorLoginLimit(final Integer invLogins, final Integer clearTimeMins);

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    UnauthorizedException requestNotAuthorized();

}
