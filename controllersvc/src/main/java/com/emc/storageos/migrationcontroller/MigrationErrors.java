package com.emc.storageos.migrationcontroller;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public interface MigrationErrors {
    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError jobFailedOp(final String opName);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError jobFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError addStepsForCreateVolumesFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError createCGFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError setCGVisibilityFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError addVolumesToCGFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError createVirtualVolumesFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError createVirtualVolumesRollbackFailed(final String stepId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError deleteVirtualVolumesFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError exportGroupCreateFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError createStorageViewFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError exportGroupDeleteFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError exportGroupAddVolumesFailed(final String volList,
            final String exportGroup, final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError exportGroupRemoveVolumesFailed(final String volList,
            final String exportGroup, final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError exportGroupAddInitiatorsFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError exportMaskDeleteFailed(final String exportMaskNames, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError zoneAddInitiatorStepFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError storageViewAddInitiatorFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError storageViewAddStoragePortFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError exportGroupRemoveInitiatorsFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError zoneRemoveInitiatorStepFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError storageViewRemoveInitiatorFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError storageViewRemoveVolumeFailed(final String exportMaskName, final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError storageViewRemoveStoragePortFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError migrateVirtualVolume(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError commitMigrationFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError rollbackCommitMigration(final String opName);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError importVolumeFailedException(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError createVirtualVolumeFromImportStepFailed(final String opName);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError expandVolumeNativelyFailed(final String vplexVol,
            final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError expandVirtualVolumeFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError expandVolumeUsingMigrationFailed(final String vplexVol,
            final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError createConsistencyGroupFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError deleteConsistencyGroupFailed(final String cgUri,
            final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError deleteCGFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError updateConsistencyGroupFailed(final String cgUri,
            final String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError removeVolumesFromCGFailed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError deleteStorageViewFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError fullCopyVolumesFailed(final String vplexUri,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError migrationJobFailed(final String reason);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError restoreVolumeFailed(final String snapshotId, Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError cacheInvalidateJobFailed(final String reason);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError createMirrorsFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError attachContinuousCopyFailed(final String sourceVolumeURI, Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError deactivateMirrorFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError detachContinuousCopyFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError detachMirrorFailed(final String opName, Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError deleteMirrorFailed(final String opName, Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError promoteMirrorFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError upgradeLocalToDistributedFailedException(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError rebuildSetTransferSpeed(final String opName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError upgradeLocalToDistributedFailed(final String opName);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError waitOnRebuildFailed(final String volumeName);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError waitOnRebuildTimedOut(final String volumeName);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError waitOnRebuildInvalid(final String volumeName);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError waitOnRebuildException(final String volumeName, Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError exportHasExistingVolumeWithRequestedHLU(String blockObjectId, String hlu, final String opName);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError unsupportedConsistencyGroupOpError(final String op, final String cg);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError rollbackDeleteCGFailed(final String op, Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError createBackendExportMaskDeleted(String maskURI, String deviceURI);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public ServiceError operateMigrationFailed(final String opName,
            final Throwable cause);
}
