/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.exceptions;

import com.datastax.driver.core.exceptions.ConnectionException;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.netflix.astyanax.connectionpool.exceptions.OperationException;

/**
 * This interface holds all the methods used to create an error condition in the
 * synchronous aspect of the controller that will be associated with an HTTP
 * status of 503
 * <p/>
 * Remember to add the English message associated to the method in RetryableDatabaseExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface RetryableDatabaseExceptions {

    // Database operation failed
    @DeclareServiceCode(ServiceCode.DBSVC_CONNECTION_ERROR)
    RetryableDatabaseException operationFailed(DriverException e);
    
 // TODO this will be removed after replace Astyanax
    @DeclareServiceCode(ServiceCode.DBSVC_CONNECTION_ERROR)
    RetryableDatabaseException operationFailed(OperationException e);

    // Database connection failed
    @DeclareServiceCode(ServiceCode.DBSVC_CONNECTION_ERROR)
    RetryableDatabaseException connectionFailed(DriverException e);
    
    // TODO this will be removed after replace Astyanax
    @DeclareServiceCode(ServiceCode.DBSVC_CONNECTION_ERROR)
    RetryableDatabaseException connectionFailed(com.netflix.astyanax.connectionpool.exceptions.ConnectionException e);

    // Database connection failed, Overload connectionFailed(ConnectionException);
    // Netflix astynanx ConnectionException is not serializable and thus
    // could not be used with RMI.
    @DeclareServiceCode(ServiceCode.DBSVC_CONNECTION_ERROR)
    RetryableDatabaseException connectionFailed();

    // Dummy DbClient not started
    @DeclareServiceCode(ServiceCode.DBSVC_DUMMY_ERROR)
    RetryableDatabaseException dummyClientNotStarted();

    // I/O exception in DBclient
    @DeclareServiceCode(ServiceCode.DBSVC_DUMMY_ERROR)
    RetryableDatabaseException dummyClientFailed();

}
