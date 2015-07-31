/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.exceptions;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.AsyncTask;

/**
 * This interface holds all the methods used to create an error condition in the synchronous aspect of the controller that will be
 * associated with an
 * HTTP status of 500
 * <p/>
 * Remember to add the English message associated to the method in FatalClientControllerExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface FatalClientControllerExceptions {

    @DeclareServiceCode(ServiceCode.CONTROLLER_CLIENT_UNABLE_TO_SCHEDULE_JOB)
    public FatalClientControllerException unableToScheduleDiscoverJobs(final AsyncTask[] tasks,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_CLIENT_UNABLE_TO_LOCATE_DEVICE_CONTROLLER)
    public FatalClientControllerException unableToLookupStorageDeviceIsNull();

    @DeclareServiceCode(ServiceCode.CONTROLLER_CLIENT_UNABLE_TO_LOCATE_DEVICE_CONTROLLER)
    public FatalClientControllerException unableToLocateDeviceController(final String controllerType);

    @DeclareServiceCode(ServiceCode.CONTROLLER_CLIENT_UNABLE_TO_SCHEDULE_JOB)
    public FatalClientControllerException unableToScanSMISProviders(final AsyncTask[] tasks,
            final String type, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_CLIENT_UNABLE_TO_MONITOR_JOB)
    public FatalClientControllerException unableToMonitorSMISProvider(final AsyncTask task,
            final String deviceType, final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_UNABLE_TO_QUEUE_JOB)
    public FatalClientControllerException unableToQueueJob(URI uri, final Throwable cause);

    @DeclareServiceCode(ServiceCode.COORDINATOR_UNABLE_TO_QUEUE_JOB)
    public FatalClientControllerException unableToQueueJob(URI uri);

}
