/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.exceptions;

import java.net.URI;
import java.sql.Timestamp;
import java.util.Set;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link RecoverPointException}s
 * <p/>
 * Remember to add the English message associated to the method in RecoverPointExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface RecoverPointExceptions {

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException errorCreatingServerURL(final String host, final int port, final Throwable e);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedConnectingForMonitoring(final URI systemId);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException noUsernamePasswordSpecified(final String address);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException deletingRPVolume(final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException noInitiatorsFoundOnRPAs();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException duplicateProtectionSystem(final String name, final URI id);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException didNotFindProductionCopyWWNs();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cannotCleanupCG(final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException exceptionGettingSettingsCG(final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException exceptionGettingSplittersVolume(final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException exceptionGettingArrays(final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException invalidCrendentialsPassedIn(final String username, final String password);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException noRecoverPointEndpoint();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToPingMgmtIP(final String mgmtIPAddress, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException credentialFailureForAddress(final String mgmtIPAddress, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToFindExpectedBookmarks();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException exceptionLookingForBookmarks(final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToFindBookmarkOrAPIT();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToEnableCopy(final String cgCopyName, final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToDisableCopy(final String cgCopyName, final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToFailoverCopy(final String cgCopyName, final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToFailoverCopy(final String cgCopyName, final String cgName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToSetCopyAsProduction(final String cgCopyName, final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToResumeProtectionAfterRecover();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException exceptionWaitingForStateChangeAfterRestore();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException timeoutWaitingForStateChangeAfterRestore();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedWaitingForImageForCopyToDisable(final String cgCopyName, final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException wrongSnapshotImageEnabled(final String bookmarkName, final String foundName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException expectingAPITMountFoundBookmark(final String bookmarkName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException wrongTimestampEnabled(final Timestamp timestamp);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException stateChangeNeverCompleted();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cantCheckLinkState(final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException notAllowedToEnableImageAccessToCGException(final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException notAllowedToEnableImageAccessToCG(final String cgName, final String cgCopyName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cannotDetermineMgmtIPSite(final String siteName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_LICENSE_ERROR)
    public RecoverPointLicenseException siteNotLicensed(final String siteName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException operationNotSupported(final String operation);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException couldNotFindSiteAndVolumeIDForJournal(final String wwn,
            final String name, final String siteName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException couldNotFindSiteAndVolumeIDForVolume(final String wwn,
            final String name, final String siteName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToCreateConsistencyGroup(final String cgName, Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToAddReplicationSetToConsistencyGroup(final String cgName, Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToAddReplicationSetCgDoesNotExist(final String cgName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToAddReplicationSetInvalidCopySpecified(final String copyName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToCreateConsistencyGroupCGExists(final String cgName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException noWWNsFoundInRequest();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException couldNotMapWWNsToAGroup(final Set<String> wwns);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cannotRestoreVolumesFromDifferentSites(final Set<String> wwns);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cannotRestoreVolumesInConsistencyGroup(final Set<String> wwns);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failureRestoringVolumes();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failureGettingInitiatorWWNs();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failureGettingProtectionInfoForVolume(final String volumeWWN);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failureGettingProtectionInfoForVolume(final String volumeWWN, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToDeleteCopy(final String cgCopyName, final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cantCallDeleteCopyUsingProductionVolume(final String copyName,
            final String cgName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cantCallDeleteCGUsingProductionCGCopy(final String cgName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToDeleteConsistencyGroup(final String cgName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cannotFindJournal(final String journalWWN);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToAddJournal(final String journalWWN,
            final String copyName, final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToDeleteJournal(final String journalWWN,
            final String copyName, final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cannotFindReplicationSet(final String volumeWWN);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cannotFindReplicationSet(final String volumeWWN,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToDeleteReplicationSet(final String volumeWWN,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToGetStatistics(final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToGetRPSiteID(final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToRecreateReplicationSet(final String volumeWWNs,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToCreateBookmark();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToImageAccessBookmark();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToCreateSnapshot();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToDisableAccessBookmark();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cannotActivateSnapshotNoTargetVolume();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException noProtectionSetAssignedToVolume(final URI volumeId);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException nonsourceVolumeSpecified();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException anotherOperationInProgress(final String rpLabel, final String setLabel);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException notAllObjectsCouldBeRetrieved(final URI id);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException databaseExceptionActivateSnapshot(final URI protectionDevice);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException databaseExceptionDeactivateSnapshot(final URI protectionDevice);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedDisableAccessOnRP();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedEnableAccessOnRP();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException noAssociatedRPSitesFound(final String address);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException versionNotSupported(final String version,
            final String minimumSupportedVersion);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException verifyVersionFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException invalidUnlock(final String lockedName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cgCannotBeCreatedInvalidVolumeSizes(String sourceStorageSystemType,
            String sourceVolumeSize, String targetStorageSystemType, String targetVolumeSizes);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException getInitiatorPortsForArrayFailed(String rpSystem, String targetStorage);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException didNotFindLocalRemoteCopyWWNs();

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToDisableProtection(final long id, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToEnableProtection(final long id, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToPauseProtection(final long id, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToResumeProtection(final long id, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToLookupConsistencyGroup(final String cgName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException discoveryFailure(final String string);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cgDeleteStepInvalidParam(final String string);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException cgLinksFailedToBecomeActive(final String cgName);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToAddJournalVolumeToConsistencyGroup(final String cgName, Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToUpdateCgLinkPolicy(final String cgName, Throwable cause);

    @DeclareServiceCode(ServiceCode.RECOVER_POINT_ERROR)
    public RecoverPointException failedToLookupConsistencyGroups(final Throwable cause);
}
