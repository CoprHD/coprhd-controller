/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to VPLEX Devices
 * <p/>
 * Remember to add the English message associated to the method in VPlexErrors.properties and use the annotation {@link DeclareServiceCode}
 * to set the service code associated to this error condition. You may need to create a new service code if there is no an existing one
 * suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface VPlexErrors {

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError jobFailedOp(final String opName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError jobFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError addStepsForCreateVolumesFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError createCGFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError setCGVisibilityFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError addVolumesToCGFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError createVirtualVolumesFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError createVirtualVolumesRollbackFailed(final String stepId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError deleteVirtualVolumesFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError exportGroupCreateFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError createStorageViewFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError exportGroupDeleteFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError exportGroupAddVolumesFailed(final String volList,
            final String exportGroup, final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError exportGroupRemoveVolumesFailed(final String volList,
            final String exportGroup, final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError exportGroupAddInitiatorsFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError exportMaskDeleteFailed(final String exportMaskNames, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError zoneAddInitiatorStepFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError storageViewAddInitiatorFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError storageViewAddStoragePortFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError exportGroupRemoveInitiatorsFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError zoneRemoveInitiatorStepFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError storageViewRemoveInitiatorFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError storageViewRemoveVolumeFailed(final String exportMaskName, final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError storageViewRemoveStoragePortFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError migrateVirtualVolume(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError commitMigrationFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError rollbackCommitMigration(final String opName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError importVolumeFailedException(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError createVirtualVolumeFromImportStepFailed(final String opName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError expandVolumeNativelyFailed(final String vplexVol,
            final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError expandVirtualVolumeFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError expandVolumeUsingMigrationFailed(final String vplexVol,
            final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError createConsistencyGroupFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError deleteConsistencyGroupFailed(final String cgUri,
            final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError deleteCGFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError updateConsistencyGroupFailed(final String cgUri,
            final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError removeVolumesFromCGFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError deleteStorageViewFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError fullCopyVolumesFailed(final String vplexUri,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError migrationJobFailed(final String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError restoreVolumeFailed(final String snapshotId, Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError cacheInvalidateJobFailed(final String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError createMirrorsFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError attachContinuousCopyFailed(final String sourceVolumeURI, Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError deactivateMirrorFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError detachContinuousCopyFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError detachMirrorFailed(final String opName, Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError deleteMirrorFailed(final String opName, Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError promoteMirrorFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError upgradeLocalToDistributedFailedException(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError upgradeLocalToDistributedFailed(final String opName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError waitOnRebuildFailed(final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError waitOnRebuildTimedOut(final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError waitOnRebuildInvalid(final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError waitOnRebuildException(final String volumeName, Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError exportHasExistingVolumeWithRequestedHLU(String blockObjectId, String hlu, final String opName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError unsupportedConsistencyGroupOpError(final String op, final String cg);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError rollbackDeleteCGFailed(final String op, Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError createBackendExportMaskDeleted(String maskURI, String deviceURI);
    
    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public ServiceError operateMigrationFailed(final String opName,
            final Throwable cause);
}
