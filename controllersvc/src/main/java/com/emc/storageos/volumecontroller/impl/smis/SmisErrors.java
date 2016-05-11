/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.smis;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to SMIS Devices
 * <p/>
 * Remember to add the English message associated to the method in SmisErrors.properties and use the annotation {@link DeclareServiceCode}
 * to set the service code associated to this error condition. You may need to create a new service code if there is no an existing one
 * suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface SmisErrors {
    @DeclareServiceCode(ServiceCode.STORAGE_PROVIDER_UNAVAILABLE)
    public ServiceError unableToCallStorageProvider(final String cause);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError methodFailed(final String methodName, final String cause);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError jobFailed(final String cause);

    @DeclareServiceCode(ServiceCode.VOLUME_CAN_NOT_BE_EXPANDED)
    public ServiceError volumeCannotBeExpanded();

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError noConsistencyGroupProvided();

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError noVolumeProvided();

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError noStorageSystemProvided();

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError noConsistencyGroupWithGivenName();

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError unableToFindSynchPath(String objectName);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError noBlockSnapshotsFound();

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError noProtocolControllerCreated();

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError dissolveActiveRestoreSessionFailure(String volume, String snap);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError resyncActiveRestoreSessionFailure(String volume);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError resumeSessionFailure(String volume, String snap);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError establishAfterSwapFailure(String source, String target);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError volumeExpandIsNotSupported(String nativeGuid);
    
    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError errorSettingRecoverPointTag(String setting);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError anExistingSGAlreadyHasTheInitiators(String maskURI, String initiators);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public ServiceError swapOperationNotAllowedDueToActiveCopySessions();
}
