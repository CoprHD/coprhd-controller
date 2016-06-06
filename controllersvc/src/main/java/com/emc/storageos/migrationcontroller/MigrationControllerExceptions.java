package com.emc.storageos.migrationcontroller;

import java.util.List;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public interface MigrationControllerExceptions {

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException getDataObjectFailedNotFound(final String className, final String objId);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException getDataObjectFailedInactive(final String objId);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException getDataObjectFailedExc(final String objId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException addStepsForChangeVirtualPoolFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException addStepsForChangeVirtualArrayFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException getInitiatorPortsForArrayFailed(final String device, final String array);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException migrationRollbackFailure(final String volumeId, final String volumeLabel, final String migration);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException cantCancelMigrationInvalidState(final String migrationName);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException failedCancelMigrations(final List<String> migrationNames,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException rollbackMigrateVolume(final String migrationId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException cantCommitedMigrationNotCompletedSuccessfully(
            final String migrationName);
}
