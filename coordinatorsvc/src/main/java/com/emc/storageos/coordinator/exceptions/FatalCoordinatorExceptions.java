/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create an error condition in the
 * synchronous aspect of the controller that will be associated with an HTTP
 * status of 500
 * <p/>
 * Remember to add the English message associated to the method in FatalCoodinatorExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/ ErrorHandling#ErrorHandling-DevelopersGuide
 */
@MessageBundle
public interface FatalCoordinatorExceptions {

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException errorConnectingCoordinatorService(
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToDecodeLicense(final Throwable e);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToDecodeDataFromCoordinator(
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToConnectToEndpoint(final Throwable lastError);

    @DeclareServiceCode(ServiceCode.IO_ERROR)
    public FatalCoordinatorException unableToCreateServerIDDirectories(
            final String dirAbsolutePath);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException endPointUnavailable();

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException failedToBuildZKConnector(final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException failedToDeserialize(final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException failedToSerialize(final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToRemoveConfiguration(
            final String configuration, final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToListAllConfigurationForKind(
            final String kind, final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToFindConfigurationForKind(final String kind,
            final String configurationId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToFindTheState(final String key,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToGetWorkPool(final String poolName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToGetLock(final String lockName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToGetPersistentLock(final String lockName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException clientNameCannotBeNull();

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException invalidKey();

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException invalidProperties(final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException failedToConnectToServiceRegistrationEndpoint(
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException
            unableToPersistTheConfiguration(final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException
            unableToPersistTheState(final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException failedToStartDistributedQueue();

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException failedToStartDistributedQueue(final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException failedToStartDistributedSemaphore(
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException exceedingLimit(final String of, final Number limit);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException dataManagerPathOutOfBounds(final String path, final String basePath);

    @DeclareServiceCode(ServiceCode.COORDINATOR_DECODING_ERROR)
    public DecodingException decodingError(final String parameter);

    @DeclareServiceCode(ServiceCode.COORDINATOR_INVALID_REPO_INFO)
    public InvalidRepositoryInfoException invalidRepoInfoError(final String parameter);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_INVALID_SOFTWARE_VERSION)
    public InvalidSoftwareVersionException invalidSoftwareVersion(final String parameter);

    @DeclareServiceCode(ServiceCode.COORDINATOR_NOTCONNECTABLE_ERROR)
    public NotConnectableException notConnectableError(final String parameter);
    
    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToRemoveTheState(final String key,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToCreateInstanceOfTargetInfo(final String className, final Throwable cause);
    
    @DeclareServiceCode(ServiceCode.COORDINATOR_ERROR)
    public FatalCoordinatorException unableToDeletePath(String path, final Throwable cause);
}
