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
package com.emc.storageos.datadomain.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

import java.net.URI;

/**
 * This interface holds all the methods used to create {@link VPlexApiException}s
 * <p/>
 * Remember to add the English message associated to the method in VPlexApiExceptions.properties and use the annotation
 * {@link com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode} to set the service code associated to this error condition.
 * You may need to create a new service code if there is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface DataDomainResourceNotFoundExceptions {

    @DeclareServiceCode(ServiceCode.DATADOMAIN_RESOURCE_NOT_FOUND)
    DataDomainResourceNotFoundException ResourceNotFound(String dataDomainURI, String msg);

}
