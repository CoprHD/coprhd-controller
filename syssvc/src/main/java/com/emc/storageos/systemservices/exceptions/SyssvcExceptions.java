/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create an error condition in the
 * synchronous aspect of the controller that will be associated with an HTTP
 * status of 500
 * <p/>
 * Remember to add the English message associated to the method in SyssvcExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/ ErrorHandling#ErrorHandling-DevelopersGuide
 */

@MessageBundle
public interface SyssvcExceptions {

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_COORDINATOR_ERROR)
    public CoordinatorClientException coordinatorClientError(final String parameter);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_ERROR)
    public SyssvcInternalException syssvcInternalError(final String parameter);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_INVALID_LOCK_OWNER)
    public InvalidLockOwnerException invalidLockOwnerError(final String parameter);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_LOCAL_REPO_ERROR)
    public LocalRepositoryException localRepoError(final String parameter);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_REMOTE_REPO_ERROR)
    public RemoteRepositoryException remoteRepoError(final String parameter);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SYS_CLIENT_ERROR)
    public SysClientException sysClientError(final String parameter);

    @DeclareServiceCode(ServiceCode.SYS_DATANODE_FAILCONNECT_CONTROLLER)
    public FailConnectControllerException failConnectControllerError(final String parameter);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SERVICE_NAME_NOT_FOUND)
    public SyssvcInternalException serviceNameNotFoundException(final String parameter);
}
