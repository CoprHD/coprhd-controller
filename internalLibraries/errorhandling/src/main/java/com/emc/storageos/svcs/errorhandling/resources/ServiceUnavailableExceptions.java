/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.resources;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;

import java.net.URI;

/**
 * This interface holds all the methods used to create an error condition that
 * will be associated with an HTTP status of Service Unavailable (503)
 * <p/>
 * Remember to add the English message associated to the method in ServiceUnavailableExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface ServiceUnavailableExceptions {

    // Dummy used for testing
    @DeclareServiceCode(ServiceCode.API_SERVICE_UNAVAILABLE)
    ServiceUnavailableException dummy();

    @DeclareServiceCode(ServiceCode.API_SERVICE_UNAVAILABLE)
    public ServiceUnavailableException cannotDeactivateStorageSystemWhileInDiscover(URI id);

    @DeclareServiceCode(ServiceCode.SYS_CLUSTER_STATE_NOT_STABLE)
    public ServiceUnavailableException clusterStateNotStable();

    @DeclareServiceCode(ServiceCode.SYS_CLUSTER_STATE_NOT_STABLE)
    public ServiceUnavailableException siteClusterStateNotStable(String siteName, String state);

    @DeclareServiceCode(ServiceCode.SYS_SERVICE_BUSY)
    public ServiceUnavailableException logServiceIsBusy();

    @DeclareServiceCode(ServiceCode.SYS_SERVICE_BUSY)
    public ServiceUnavailableException sendEventBusy();

    @DeclareServiceCode(ServiceCode.API_VERSION_OF_IMAGE_UNKNOWN_SO_FAR)
    public ServiceUnavailableException versionOfTheImageIsUnknownSoFar();

    @DeclareServiceCode(ServiceCode.SYS_SERVICE_BUSY)
    public ServiceUnavailableException postLicenseBusy();

    @DeclareServiceCode(ServiceCode.SYS_SERVICE_BUSY)
    public ServiceUnavailableException dataNodesNotFound();

    @DeclareServiceCode(ServiceCode.OBJ_SYSTABLE_NOT_CREATED_YET)
    public ServiceUnavailableException objSystemNotInitializedYet();

    @DeclareServiceCode(ServiceCode.SYS_CLUSTER_STATE_NOT_STABLE)
    public ServiceUnavailableException vdcNotStable(String vdcId);

    @DeclareServiceCode(ServiceCode.SYS_CLUSTER_STATE_NOT_STABLE)
    public ServiceUnavailableException vdcOngingJob(String vdcId, String state);

    @DeclareServiceCode(ServiceCode.SYS_CLUSTER_STATE_NOT_STABLE)
    public ServiceUnavailableException siteOnGoingJob(String name, String state);

    @DeclareServiceCode(ServiceCode.SYS_CLUSTER_STATE_NOT_STABLE)
    public ServiceUnavailableException sitePaused(String name);
}
