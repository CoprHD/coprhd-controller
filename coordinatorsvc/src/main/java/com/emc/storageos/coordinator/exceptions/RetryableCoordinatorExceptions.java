/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create an error condition in the
 * synchronous aspect of the controller that will be associated with an HTTP
 * status of 503
 * <p/>
 * Remember to add the English message associated to the method in RetryableCoordinatorExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/ ErrorHandling#ErrorHandling-DevelopersGuide
 */
@MessageBundle
public interface RetryableCoordinatorExceptions {

    @DeclareServiceCode(ServiceCode.COORDINATOR_QUEUE_TOO_BUSY)
    public RetryableCoordinatorException queueTooBusy();

    @DeclareServiceCode(ServiceCode.COORDINATOR_SVC_NOT_FOUND)
    public RetryableCoordinatorException unableToLocateService(final String name,
            final String version, final String tag, final String endpointKey);

    @DeclareServiceCode(ServiceCode.COORDINATOR_SVC_NOT_FOUND)
    public RetryableCoordinatorException unableToLocateServiceNoEndpoint(
            final String name, final String version, final String tag,
            final String endpointKey);

    @DeclareServiceCode(ServiceCode.COORDINATOR_SVC_NOT_FOUND)
    public RetryableCoordinatorException unsupportedEndPointSchema(final String scheme);

    @DeclareServiceCode(ServiceCode.COORDINATOR_SVC_NOT_FOUND)
    public RetryableCoordinatorException cannotFindNode(final String nodeFullPath,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_SVC_NOT_FOUND)
    public RetryableCoordinatorException errorWhileFindingNode(final String nodeFullPath,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_SVC_NOT_FOUND)
    public RetryableCoordinatorException cannotLocateService(final String fullPath);

    @DeclareServiceCode(ServiceCode.COORDINATOR_SVC_NOT_FOUND)
    public RetryableCoordinatorException unableToAddWork(final String workId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_SVC_NOT_FOUND)
    public RetryableCoordinatorException unableToRemoveWork(final String workId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_SVC_NOT_FOUND)
    public RetryableCoordinatorException unableToStartWork(final String workItem,
            final Throwable cause);
    
    @DeclareServiceCode(ServiceCode.COORDINATOR_SITE_NOT_FOUND)
    public RetryableCoordinatorException cannotFindSite(final String siteId);
}
