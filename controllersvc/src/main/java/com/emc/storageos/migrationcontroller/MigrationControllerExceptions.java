package com.emc.storageos.migrationcontroller;

import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.vplex.api.VPlexApiException;

public interface MigrationControllerExceptions {

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public MigrationControllerException getDataObjectFailedNotFound(final String className, final String objId);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public VPlexApiException getDataObjectFailedInactive(final String objId);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public VPlexApiException getDataObjectFailedExc(final String objId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public VPlexApiException addStepsForChangeVirtualPoolFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.MIGRATION_ERROR)
    public RecoverPointException getInitiatorPortsForArrayFailed(String rpSystem, String targetStorage);
}
