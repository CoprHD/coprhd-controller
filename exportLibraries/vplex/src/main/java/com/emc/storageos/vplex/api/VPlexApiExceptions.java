/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.List;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link VPlexApiException}s
 * <p/>
 * Remember to add the English message associated to the method in VPlexApiExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface VPlexApiExceptions {

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException addStepsForDeleteVolumesFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException exportGroupDeleteFailedEG(final String storage);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException getStorageViewsFailed(final String message);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException assembleExportMasksWorkflowFailed(final String message, final String initiators);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException tooManyExistingStorageViewsFound(final String storageViews, final String initiators);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException getInitiatorPortsForArrayFailed(final String device, final String array);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException determineNetworkDeregistered(final String network);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException exportGroupDeleteFailedNull(final String storage);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException getDataObjectFailedNotFound(final String className, final String objId);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException getDataObjectFailedInactive(final String objId);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException getDataObjectFailedExc(final String objId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException addStepsForCreateVolumesFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException addStepsForCreateMirrors(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException addStepsForDetachMirror(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException addStepsForDetachAndDeleteMirror(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException addStepsForPromoteMirrors(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException authenticationFailure(final String vplexURI);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException connectionFailure(final String vplexURI);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException vplexSystemNotFound(final String vplexURI);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException vplexUriIsNull();

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException invalidStorageSystemType(final String systemType, final String vplexUri);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException updateClusterInfoFailureStatus(final String clusterName,
            final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedUpdateClusterInfo(final String clusterName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException setDetachRuleWinnerFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedSettingDetachRuleWinner(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException setDetachRuleNoAutoWinnerFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedSettingDetachRuleNoAutoWinner(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedToFindCluster(final String clusterName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException createConsistencyGroupFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCreatingConsistencyGroup(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException setCGStorageAtClustersFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedSettingCGStorageAtClusters(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException setCGVisibilityFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedSettingCGVisibility(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_CANT_FIND_REQUESTED_VOLUME)
    public VPlexApiException cantFindRequestedVolume(final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_CANT_FIND_REQUESTED_VOLUME)
    public VPlexApiException cantFindAllRequestedVolume();

    @DeclareServiceCode(ServiceCode.VPLEX_CANT_FIND_REQUESTED_VOLUME)
    public VPlexApiException cantFindRequestedVolumeNull();

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException invalidateCacheFailureStatus(final String volumeName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedInvalidatingVolumeCache(final String volumeName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindLocalDeviceForVolume(final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindDistributedDeviceForVolume(final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindMirrorForDetach(final String clusterId, final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindMirrorForAttach(final String mirrorName, final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindMirror(final String mirrorName, final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException detachMirrorFailureStatus(final String mirrorName,
            final String volumeName, final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException reattachMirrorFailureStatus(final String mirrorName,
            final String volumeName, final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedDetachingVPlexVolumeMirror(final String mirrorName,
            final String volumeName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedAttachingVPlexVolumeMirror(final String mirrorName,
            final String volumeName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException getCacheStatusFailureStatus(final String volumeName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingCacheStatus(final String volumeName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingExceptionMsgFromResponse(final String response,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingCustomDataFromResponse(final String response,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCreatingPostDataForRequest(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedSettingAttributesForResource(final String resourceName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedExtractingChildrenFromResponse(final String response,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedExtractingAttributesFromResponse(final String response,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException couldNotGenerateArrayExportMask(
            final String vplexName, final String arrayName, final String vplexCluster);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException couldNotFindValidArrayExportMask(
            final String vplexName, final String arrayName, final String vplexCluster);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException unexpectedBlockCountFormat(final String blockCount);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException unexpectedBlockSizeFormat(final String blockSize);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failureGettingVirtualVolumesOnClusterStatus(
            final String clusterId, final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingVirtualVolumesOnCluster(final String clusterId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failureGettingComponentsForLocalDeviceStatus(
            final String devicePath, final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingComponentsForLocalDevice(
            final String devicePath, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException updatelLocalDeviceComponentFailureStatus(
            final String componentPath, final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedUpdateLocalDeviceComponentInfo(
            final String componentPath, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failureGettingExtentComponentsStatus(
            final String extentPath, final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingExtentComponents(
            final String extentPath, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failureGettingDistributedDevicesStatus(final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingDistributedDevices(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failureGettingDistDeviceComponentsStatus(
            final String ddPath, final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingDistDeviceComponents(final String ddPath,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException updateDistDeviceComponentFailureStatus(
            final String componentPath, final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedUpdateDistDeviceComponentInfo(
            final String componentPath, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException moreThanOneComponentForExtent(final String extentPath);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failureGettingCGsOnClusterStatus(final String clusterId,
            final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingCGsOnCluster(final String clusterId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException didNotFindCGWithName(final String cgName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failureUpdatingCGStatus(final String cgName,
            final String clusterId, final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedUpdatingCG(final String cgName,
            final String clusterId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedToFindAllRequestedTargets();

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException couldNotFindStorageView(String viewName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException couldNotFindCluster(String clusterName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException couldNotFindInitiators(String initiatorsWWPN);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingMigrationSupportedForVolume(final String vplexURI, final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException addStepsForChangeVirtualPoolFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException addStepsForMigrateVolumesFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException timeoutWaitingForAsyncOperationToComplete(final String asyncTaskURI);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingVPLEXMgmntSvrVersionStatus(final String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedProcessingMgmntSvrVersionFromResponse(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException rollbackMigrateVolume(final String migrationId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException moreThanOneHAVarrayInExport(String varrays);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException addVolumesToCGFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedAddingVolumesToCG(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException deleteCGFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedDeleteCG(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException removeVolumesFromCGFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedRemovingVolumesFromCG(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException setRPEnabledFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException setCGAutoResumeFailureStatus(final String cgName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException deleteStorageViewFailureStatus(final String viewName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedDeleteStorageView(final String viewName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException registerInitiatorFailureStatus(final String initName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedRegisterInitiator(final String initName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException unregisterInitiatorsFailureStatus(final String status,
            final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedUnregisterInitiators(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException createStorageViewFailureStatus(final String viewName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCreateStorageView(final String viewName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException modifyViewInitiatorsFailureStatus(final String viewName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedModifyViewInitiators(final String viewName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException modifyViewTargetsFailureStatus(final String viewName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedModifyViewTargets(final String viewName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException modifyViewVolumesFailureStatus(final String viewName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedModifyViewVolumes(final String viewName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException couldNotFindAllInitiatorsToUnregister();

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException pauseMigrationsFailureStatus(
            final List<String> migrationNames, final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedPauseMigrations(final List<String> migrationNames,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException resumeMigrationsFailureStatus(
            final List<String> migrationNames, final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedResumeMigrations(final List<String> migrationNames,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException commitMigrationsFailureStatus(
            final List<String> migrationNames, final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCommitMigrations(final List<String> migrationNames,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cancelMigrationsFailureStatus(
            final List<String> migrationNames, final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCancelMigrations(final List<String> migrationNames,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException migrationFailureStatus(final String sourceName,
            final String targetName, final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedStartMigration(final String migrationName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cleanMigrationsFailureStatus(final String migrationNames,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCleanMigrations(final String migrationNames,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException removeMigrationsFailureStatus(final String migrationNames,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedRemoveMigrations(final String migrationNames,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantPauseMigrationNotInProgress(final String migrationName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantResumeMigrationNotPaused(final String migrationName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantCommitedMigrationNotCompletedSuccessfully(
            final String migrationName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantCleanMigrationNotCommitted(final String migrationName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantCancelMigrationInvalidState(final String migrationName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantRemoveMigrationInvalidState(final String migrationName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException deleteVolumeFailureStatus(final String volumeName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedDeleteVolume(final String volumeName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException expandVolumeFailureStatus(final String volumeName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedExpandVolume(final String volumeName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedExpandVolumeStatusAfterRetries(final String volumeName,
            final String retries, final String wait);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException claimVolumeFailureStatus(final String volumeWWN,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException claimedVolumeNameIsTooLong(final String volumeName,
            final String size);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedClaimVolume(final String volumeWWN,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException createExtentFailureStatus(final String status,
            final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCreateExtent(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException createLocalDeviceFailureStatus(final String status,
            final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCreateLocalDevice(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException createDistDeviceFailureStatus(final String status,
            final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCreateDistDevice(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException createVolumeFailureStatus(final String status,
            final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedCreateVolume(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException dismantleResourceFailureStatus(final String resourceName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedDismantleResource(final String resourceName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException deleteExtentFailureStatus(final String extentName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedDeleteExtent(final String extentName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException unclaimVolumeFailureStatus(final String volumeName,
            final String status, final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedUnclaimVolume(final String volumeName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException attachMirrorFailureStatus(final String status,
            final String cause);
    
    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException setRebuildSetTransferSpeeFailureStatus(final String status,
            final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedAttachMirror(final Throwable cause);
    
    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedSetTransferSize(final Throwable cause); 

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException renameResourceFailureStatus(final String status,
            final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindLocalDevice(final String deviceName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindLocalDeviceForExtent(final String extentName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindExtentForClaimedVolume(final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantDiscoverStorageVolume(final String volumeName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindDistDevice(final String deviceName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException couldNotFindComponentForDistDevice(final String deviceName,
            final String clusterId);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindExtentForLocalDevice(final String deviceName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException twoDevicesRequiredForDistVolume();

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException oneDevicesRequiredForLocalVolume();

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException clusterHasNoLoggingVolumes(final String clusterId);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException oneDeviceRequiredForMirror();

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException exportCreateAllHostsNotConnected(String hosts);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException exportCreateNoHostsConnected(String whichVarray, String hosts);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException exportCreateNoinitiatorsHaveCorrectConnectivity(final String initiators, final String varrays);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failureFindingCGWithName(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException getMigrationsFailureStatus(final String status,
            final String cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantFindMigrationWithName(final String migrationName);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failureFindingMigrationWithName(final String migrationName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_CONCURRENCY_ERROR)
    public VPlexApiException couldNotObtainConcurrencyLock(String vplexId);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedToDeserializeJsonResponse(String message);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedToGetClusterInfo(String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException errorProcessingClusterInfo(String message);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingInitiatorInfoForCluster(String clusterName, String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException errorProcessingInitiatorInformation(String message);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingPortInfo(String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException errorProcessingPortInformation(String message);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingVirtualVolumeInfo(String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException errorProcessingVirtualVolumeInformation(String message);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingConsistencyGroupInfo(String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException errorProcessingConsistencyGroupInformation(String message);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingStorageViewInfo(String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException errorProcessingStorageViewInformation(String message);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingTargetPortInfo(String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException errorProcessingTargetPortInformation(String message);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException restoreFromFullCopyFailed(String fullCopyIds, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException resyncFullCopyFailed(String fullCopyIds, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException detachFullCopyFailed(String fullCopyIds, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException systemTypeNotSupported(String systemType);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingStorageVolumeInfoForIngestion(String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingDeviceNameForStorageVolume(String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException deviceStructureIsIncompatibleForIngestion(String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingDeviceStructure(String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException backendIngestionContextLoadFailure(String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedGettingStorageVolumeInfo(String clusterName, String status);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedProcessingStorageVolumeResponse(String msg, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException couldNotFindStorageVolumeMatchingWWNOrITL(String volumeName, String storageSystemNativeId);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedToValidateExportMask(String exporURI, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedDeviceCollapse(final String deviceName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedDeviceCollapseStatus(final String deviceName, String msg, String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedSettingDeviceVisibility(final String deviceName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedSettingDeviceVisibilityStatus(final String deviceName, String msg, String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException establishVolumeFullCopyGroupRelationFailed(String fullCopyId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException failedToExecuteDrillDownCommand(String deviceName, String response);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantRenameDevice(String originalDeviceName, String newName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantRenameDeviceBackToOriginalName(String originalDeviceName, String newName, final Throwable cause);
    
    @DeclareServiceCode(ServiceCode.VPLEX_API_ERROR)
    public VPlexApiException cantUseBackendExportMaskNotAllPortsInVarray(final String maskName, final String varray, final String listOfPorts);
}
