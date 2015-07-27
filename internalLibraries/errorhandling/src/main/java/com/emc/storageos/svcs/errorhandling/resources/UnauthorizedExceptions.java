/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.svcs.errorhandling.resources;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;

/**
 * This interface holds all the methods used to create an error condition that
 * will be associated with an HTTP status of Unauthorized (401)
 * <p/>
 * Remember to add the English message associated to the method in
 * UnauthorizedExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error
 * condition. You may need to create a new service code if there is no an
 * existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section
 * in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface UnauthorizedExceptions {
    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException bindSearchGenericException();
    
    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException userSearchFailed();
    
    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException managerBindFailed();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException ldapCommunicationException();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException invalidProxyToken(final String user);

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException unauthenticatedRequestUnsignedInternalRequest();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException unauthenticatedRequestUnsignedInterVDCRequest();    
    
    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException unauthenticatedRequestUseHTTPS();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException tokenNotFoundOrInvalidTokenProvided();
    
    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException methodNotAllowedOnThisNode();

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_TOKEN_ENCODING_ERROR)
    public UnauthorizedException unableToEncodeToken(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_TOKEN_ENCODING_ERROR)
    public UnauthorizedException unableToDecodeToken(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_TOKEN_ENCODING_ERROR)
    public UnauthorizedException unableToDecodeTokenTheSignatureDoesNotValidate();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException maxNumberOfTokenExceededForUser();
    
    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_SERVICE_ENCODING_ERROR)
    public UnauthorizedException unableToVerifyTheServiceSignature();

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_SERVICE_ENCODING_ERROR)
    public UnauthorizedException unableToGenerateTheServiceSignature();
    
    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException noTokenFoundForUserFromForeignVDC();

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException exceedingErrorLoginLimit(final Integer invLogins, final Integer clearTimeMins);

    @DeclareServiceCode(ServiceCode.SECURITY_UNAUTHORIZED_OPERATION)
    public UnauthorizedException requestNotAuthorized();

}
