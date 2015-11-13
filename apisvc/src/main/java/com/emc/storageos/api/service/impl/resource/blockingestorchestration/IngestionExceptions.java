/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create an error condition that
 * will be associated with a Unmanaged Volume Operations
 * <p/>
 * Remember to add the English message associated to the method in UnmanagedVolumeExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface IngestionExceptions {

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException generalException(String message);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException generalVolumeException(String unManagedVolume, String message);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumePartiallyIngested(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeAlreadyIngested(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeNotIngestable(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException couldNotCreateVolume(String message);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeIsInactive(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeIsAnSrdfTarget(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeWithoutSRDFProtection(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException srdfVpoolRemoteProtectionCopyModeMismatch(String unManagedVolume, String copyMode);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException srdfVolumeRemoteProtectionCopyModeMismatch(String unManagedVolume, String copyMode);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException srdfVolumeRemoteProtectionMismatch(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeIsRecoverpointEnabled(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeAddedToConsistencyGroup(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeVplexConsistencyGroupCouldNotBeIdentified(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeVpoolConsistencyGroupMismatch(String vpool, String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException cannotIngestNonVplexVolumeIntoVplexVpool(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeIsExported(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeHasReplicas(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeAndVpoolProtocolMismatch(String unManagedVolume, String vpool, String protocols);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeHasInvalidHostIoLimits(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException systemResourceLimitsExceeded(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException poolResourceLimitsExceeded(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeIsNotVisible(String unManagedVolume, String taskStatus);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeMasksNotIngested(String unManagedVolume, String messages);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException snapshotVpoolSpecifiesSrdf(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeVolumeTypeNotSet(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeRDFGroupMismatch(String unManagedVolume, String umvRDFGroupName, String projectName,
            String validGroupNames);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeRDFGroupMissing(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedSRDFTargetVolumeVArrayMismatch(String unManagedVolume, String sourceVarray);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedSRDFSourceVolumeVArrayMismatch(String unManagedVolume, String targetVarray);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeVpoolTieringPolicyMismatch(String unManagedVolume, String vpool);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException varrayIsInvalidForVplexVolume(String unManagedVolume, String reason);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unmanagedVolumeHasNoStoragePool(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException hostHasNoInitiators();

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException hostHasNoZoning(String initiators);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException hostZoningHasFewerPorts(String hostName, String portCount, String minPaths);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException hostZoningHasMorePorts(String hostName, String portCount, String maxPaths);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException hostZoningHasDifferentPortCount(String initiator, String hostName, String portCount, String pathsPerInit);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException inconsistentZoningAcrossHosts(String messages);
    
    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException failedToIngestVplexBackend(String message);
    
    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException failedToGetStorageVolumeInfoForDevice(String supportingDeviceName, String reason);
    
    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException validationException(String reason);
    
    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException exportedVolumeIsMissingWwn(String unManagedVolume);
    
    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException vplexBackendVolumeHasNoParent(String unManagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unManagedProtectionSetNotFound(String nativeGuid);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException rpIngestingNonVolumeObject(String nativeGuid);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException noManagedTargetVolumeFound(String nativeGuid, String rpManagedTargetVolumeIdStr);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException noUnManagedTargetVolumeFound(String nativeGuid, String rpUnManagedTargetVolumeIdStr);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException noManagedSourceVolumeFound(String nativeGuid, String rpManagedSourceVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException noUnManagedSourceVolumeFound(String nativeGuid);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException noUnManagedSourceVolumeFound2(String nativeGuid, String rpUnManagedSourceVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException noVolumesFoundInProtectionSet(String label);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException noUnManagedExportMaskFound(String nativeGuid);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException rpObjectNotSet(String fieldName, URI objectId);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException rpObjectNotFound(URI objectId);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException noUnManagedVolumesFound(String cgName);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException invalidSourceRPVirtualPool(String volLabel, String vpoolLabel);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException invalidRPVirtualPool(String volLabel, String vpoolLabel);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unManagedProtectionSetNotHealthy(String cgName, String unmanagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unManagedProtectionSetNotAsync(String cgName, String unmanagedVolume);

    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionException unManagedProtectionSetNotSync(String cgName, String unmanagedVolume);
}
