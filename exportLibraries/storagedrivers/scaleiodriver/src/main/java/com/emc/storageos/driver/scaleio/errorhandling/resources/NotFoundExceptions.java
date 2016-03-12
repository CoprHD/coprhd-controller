/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import com.emc.storageos.driver.scaleio.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.driver.scaleio.errorhandling.annotations.MessageBundle;

import java.net.URI;

/**
 * This interface holds all the methods used to create an error condition that
 * will be associated with an HTTP status of Not Found (404)
 * <p/>
 * Remember to add the English message associated to the method in NotFoundExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface NotFoundExceptions {
    @DeclareServiceCode(ServiceCode.API_URL_ENTITY_NOT_FOUND)
    NotFoundException unableToFindEntityInURL(final URI value);

    @DeclareServiceCode(ServiceCode.API_URL_ENTITY_NOT_FOUND)
    NotFoundException unableToFindUserScopeOfSystem();

    @DeclareServiceCode(ServiceCode.API_URL_ENTITY_INACTIVE)
    NotFoundException entityInURLIsInactive(final URI value);

    @DeclareServiceCode(ServiceCode.API_URL_ENTITY_NOT_FOUND)
    NotFoundException invalidParameterObjectHasNoSuchShare(URI id,
                                                           String shareName);

    @DeclareServiceCode(ServiceCode.API_URL_ENTITY_NOT_FOUND)
    NotFoundException openstackTenantNotFound(String openstackTenantId);
}
