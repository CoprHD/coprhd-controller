/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.resources;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;

/**
 * This interface holds all the methods used to create an error condition that will be associated
 * with an HTTP status of Bad Request (400)
 * <p/>
 * Remember to add the English message associated to the method in BadRequestExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/ Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface BadRequestExceptions {

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException modificationOfGroupAttributeNotAllowed(final String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException authnProviderCouldNotBeValidated(final String errorMsg);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException autoTieringNotEnabledOnStorageSystem(final URI systemId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException blockSourceAlreadyHasContinuousCopiesOfSameName(List<String> dupList);

    @DeclareServiceCode(ServiceCode.API_CANNOT_REGISTER)
    public BadRequestException cannotAddStorageSystemTypeToStorageProvider(String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotCreateRPVolumesInCG(final String volumeName, final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotCreateSnapshotOfVplexCG();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException snapshotsNotSupportedForRPCGs();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotCreateVolumeAsConsistencyGroupHasSnapshots(String label, URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeactivateStorageSystem();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeactivateObjectIngestionTask();

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    public BadRequestException cannotDeleteProviderWithManagedStorageSystems(final URI systemId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDiscoverUnmanagedResourcesForUnsupportedSystem();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotExpandMirrorsUsingMetaVolumes();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotHaveFcAndIscsiInitiators();

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    public BadRequestException cannotImportExportedVolumeToVplex(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    public BadRequestException cannotImportConsistencyGroupVolumeToVplex(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotMixLdapAndLdapsUrls();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotPauseContinuousCopyWithSyncState(URI mid, String syncState,
            URI vid);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotPauseContinuousCopyWhileResynchronizing(URI mid,
            String syncState, URI vid);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotResumeContinuousCopyWithSyncState(URI mid, String syncState,
            URI vid);

    @DeclareServiceCode(ServiceCode.API_CANNOT_REGISTER)
    public BadRequestException cannotRegisterSystemWithType(final String systemType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotRemoveAllValues(final String fieldName, final String objectName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotUnassignNetworkInUse(URI networkId, URI associatedId,
            String associatedType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException changeInVNXVirtualPoolNotSupported(final URI vpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException changePropertyIsNotAllowed(final String property);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException changesNotSupportedFor(final String propertyTryingToBeChanged,
            final String unsupportedFor);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException changeToVirtualPoolNotSupported(final URI vpoolId,
            final String notSuppReasonBuff);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException changeToComputeVirtualPoolNotSupported(final String vpool,
            final String notSuppReasonBuff);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException changeToVirtualPoolNotSupportedForNonCGVolume(
            final URI volumeId, String vpoolLabel);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException configEmailError();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException consistencyGroupNotCreated();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException consistencyGroupStillBeingCreated(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException currentAndRequestedVArrayAreTheSame();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException domainAlreadyExists(String domain);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException duplicateChildForParent(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException duplicateEntityWithField(String entityName, String fieldName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException duplicateLabel(String entityName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException duplicateNamespace(String entityName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException endpointsCannotBeAdded(final String endpoints);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException endpointsCannotBeRemoved(final String endpoints);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException endpointsCannotBeUpdatedActiveExport(final String endpoint);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterUnManagedFsListEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterForUnManagedVolumeQuery(String exportType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterProjectEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVirtualArrayEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVirtualPoolEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException endTimeBeforeStartTime(final String startTime, final String endTime);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INACTIVE)
    public BadRequestException entityInRequestIsInactive(final URI value);

    @DeclareServiceCode(ServiceCode.API_EXCEEDING_LIMIT)
    public BadRequestException exceedingLimit(final String of, final Number limit);

    @DeclareServiceCode(ServiceCode.API_EXCEEDING_ASSIGNMENT_LIMIT)
    public BadRequestException exceedingRoleAssignmentLimit(final Number limit, final Number value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException initiatorExportGroupInitiatorsBelongToSameHost();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceURLBadSyntax();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException expansionNotSupportedForMetaVolumesWithMirrors();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException expansionNotSupportedForHitachThickVolumes();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException expansionNotSupportedForHitachiVolumesNotExported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException failedToCreateAuthenticationHandlerDomainsCannotBeNull(final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException failedToCreateAuthenticationHandlerManagerUserDNPasswordAreRequired(
            final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException failedToCreateAuthenticationHandlerSearchBaseCannotBeNull(
            final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException failedToCreateAuthenticationHandlerSearchFilterCannotBeNull(
            final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException failedToCreateAuthenticationHandlerServerURLsAreRequired(final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException fileSizeExceedsLimit(final long maxSize);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fileSystemHasExistingExport();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotHasExistingExport();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotHasNoExport(final URI value);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException getNodeDataForESRSFailure(final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ZONE)
    public BadRequestException illegalZoneName(final String zoneName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException illegalZoneMember(final String zoneMemeber);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ZONE)
    public BadRequestException nameZoneLongerThanAllowed(String zoneName, final int zoneNameAllowedLength);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException illegalWWN(final String wwn);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException illegalWWNAlias(final String alias);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_FSNAME)
    public BadRequestException invalidFileshareName(final String fsName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_QDNAME)
    public BadRequestException invalidQuotaDirName(final String fsName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException initiatorHostsInSameOS();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException initiatorNotConnectedToStorage(String initiator, String storage);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException errorVerifyingInitiatorConnectivity(String initiator,
            String storage, String error);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException initiatorNotCreatedManuallyAndCannotBeDeleted();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException initiatorInClusterWithAutoExportDisabled();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException initiatorPortNotValid();

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_QUOTA)
    public BadRequestException insufficientQuotaForVirtualPool(final String label, final String type);

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_QUOTA)
    public BadRequestException insufficientQuotaForProject(final String label, final String type);

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_QUOTA)
    public BadRequestException insufficientQuotaForTenant(final String label, final String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidACL(String ace);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidACLTypeEmptyNotAllowed();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidACLTypeMultipleNotAllowed();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidAutoTieringPolicy();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidDriveType(final String systemType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidBlockObjectToExport(String label, String simpleName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException invalidDate(final String date);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidEndpointExpectedFC(String endpoint);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidEndpointExpectedNonFC(String endpoint);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidEntryACLEntryMissingTenant();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidEntryForProjectACL();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidEntryForCatalogServiceACL();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    public BadRequestException invalidEntryForRoleAssignment(final String role);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    public BadRequestException invalidEntryForRoleAssignmentDetails(final String role,
            final String details);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    public BadRequestException invalidRoleAssignments(final String details);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    public BadRequestException invalidEntryForRoleAssignmentSubjectIdAndGroupAreNull();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    public BadRequestException invalidEntryForRoleAssignmentSubjectIdsCannotBeALocalUsers(
            final String localUsers);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException invalidEntryForUsageACL(final String tenant);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidField(final String field, final String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidField(final String field, final String value,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidHighAvailability(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidInput(final Integer line, final Integer column);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidIpProtocol();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidIscsiInitiatorPort();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameter(final String parameterName,
            final String parameterValue);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameter(final String parameterName,
            final String parameterValue, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    public BadRequestException invalidParameterAboveMaximum(String string, long size, long minimum,
            String unit);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterAssignedPoolNotInMatchedPools(String poolStr);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    public BadRequestException invalidParameterBelowMinimum(String string, long size, long minimum,
            String unit);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterBlockCopyDoesNotBelongToVolume(URI pid, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterBlockMaximumCopiesForVolumeExceeded(
            Integer maxNativeContinuousCopies, String sourceVolumeName, URI sourceVolumeURI, Integer currentMirrorCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterBlockMaximumCopiesForVolumeExceededForSourceAndHA(
            Integer sourceVpoolMaxCC, Integer haVpoolMaxCC, String sourceVolumeName, String sourceVpoolName, String haVpoolName,
            Integer currentSourceMirrorCount, Integer currentHAMirrorCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterBlockMaximumCopiesForVolumeExceededForSource(
            Integer sourceVpoolMaxCC, String sourceVolumeName, String sourceVpoolName, Integer currentSourceMirrorCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterBlockMaximumCopiesForVolumeExceededForHA(
            Integer haVpoolMaxCC, String sourceVolumeName, String haVpoolName, Integer currentHAMirrorCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterBlockSnapshotCannotBeExportedWhenInactive(
            String label, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterCannotDeactivateRegisteredNetwork(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterCannotDeactivateRegisteredNetworkSystem(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterCannotDeleteDiscoveredNetwork(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterCannotRegisterUnmanagedNetwork(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterClusterAssignedToDifferentProject(String label,
            String label2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterClusterInDifferentTenantToProject(String label,
            String label2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterClusterNotInDataCenter(String label, String label2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterClusterNotInHostProject(String label);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupAlreadyContainsVolume(URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupCannotAddProtectedVolume(
            URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupNotForVplexStorageSystem(
            URI consistencyGroup);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupProvidedButVirtualPoolHasNoMultiVolumeConsistency(
            URI consistencyGroup, URI vpool);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupStorageySystemMismatch(URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterIBMXIVConsistencyGroupVolumeNotInPool(URI volumeURI, URI poolURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupStorageSystemMismatchVirtualArray(URI cgURI, URI varrayURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupVirtualArrayMismatch(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterUserTenantNamespaceMismatch();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupVirtualPoolMustSpecifyMultiVolumeConsistency(
            URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupVolumeHasIncorrectHighAvailability(
            URI cg, String expected);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupVolumeMismatch(URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterEndpointsNotFound(Collection<String> endpoints);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterExportGroupAlreadyHasLun(Integer lun);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidHostConnection();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidVCenterConnection(String message);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidNotAVCenter(String hostname, String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterExportGroupHostAssignedToDifferentProject(
            String hostName, String name);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterExportGroupHostAssignedToDifferentTenant(
            String hostName, String label);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterExportGroupInitiatorNotInHost(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterFileSystemHasNoVirtualPool(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterFileSystemNoSuchExport();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterHighAvailabilityType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterHighAvailabilityVirtualArrayRequiredForType(
            String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterHighAvailabilityVirtualPoolNotValidForVirtualArray(
            String haNhVirtualPoolId, String haVirtualArrayId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterHostNotInCluster(String hostName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterInitiatorBelongsToDeregisteredNetwork(
            Object initiator, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterInitiatorIsDeregistered(Object initiator);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterIpInterfaceIsDeregistered(Object ipInterface);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterInvalidIP(String fieldName, String ip);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterInvalidIPV4(String fieldName, String ip);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterInvalidIPV6(String fieldName, String ip);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterLengthTooLong(String fieldName, String value,
            long maximum);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterNetworkMustBeUnassignedFromVirtualArray(
            String label, String label2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterNetworkUsedByFCZoneReference(String network,
            String zoneName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterNoActiveInitiatorPortsFound(
            List<String> invalidNetworkPorts);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterNoMatchingPoolsExistToAssignPools(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterNoOperationForTaskId(String taskId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterNoOperationForTaskId(final URI taskId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterNoStorageFoundForVolume(URI id, URI id2, URI id3);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterNoStoragePool(URI storagePoolURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterNoSuchVirtualPoolOfType(URI id, String string);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterOnlyClustersForExportType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterOnlyHostsForExportType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterOnlyHostsOrInitiatorsForExportType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterOnlyInitiatorsForExportType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    public BadRequestException invalidParameterPercentageExpected(String parameter, int percent);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterProjectQuotaInvalidatesTenantQuota(Long quota);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    public BadRequestException invalidParameterRangeLessThanMinimum(String parameter, long value,
            long minimum);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException invalidParameterSearchMissingParameter(String resourceType,
            String missing);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterSearchProjectNotSupported(String name);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterSearchStringTooShort(String field, String value,
            String name, int minimum);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterSMISProviderAlreadyRegistered(String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterStorageProviderAlreadyRegistered(String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterSMISProviderListIsEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterSMISProviderNotConnected(String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterProviderStorageSystemAlreadyExists(String parameter,
            String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterStoragePoolHasNoTiers(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterStorageSystemNamespace(String namespace);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterProtectionSystemNamespace(String namespace);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterSystemTypeforAutoTiering();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterTenantsQuotaExceedsProject(long quotaGb,
            long totalProjects);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterTenantsQuotaExceedsSubtenants(long quotaGb,
            long totalSubtenants);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterTenantsQuotaInvalidatesParent(Long quota);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterURIInvalid(String fieldName, URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterURIWrongType(String fieldName, URI value, String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidUrl(String fieldName, String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterCannotUpdateComputeImageUrl();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteComputeWhileInUse();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterComputeImageIsNotAvailable(URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterHostHasNoComputeElement();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException osInstallNetworkNotSet();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException osInstallNetworkNotValid(final String network);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException hostPasswordNotSet();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException computeElementHasNoUuid();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException osInstallAlreadyInProgress();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidHostName(String hostName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidHostNamesAreNotUnique();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidNodeNamesAreNotUnique(String nodeName, String prop1, String prop2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidNodeShortNamesAreNotUnique(String shortName, String prop1, String prop2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidNodeNameIsIdOfAnotherNode(String name, String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterHostAlreadyHasOs(String os);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterValueWithExpected(String fieldName, Object value,
            Collection<Object> expected);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterValueWithExpected(String fieldName, Object value,
            Object... expected);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterValueWithExpected(String fieldName, Object value,
            String... expected);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterValueExceedsArrayLimit(String fieldName, Integer value,
            Integer limit);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVirtualArrayAndVirtualPoolDoNotApplyForType(
            String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVirtualPoolAndVirtualArrayNotApplicableForHighAvailabilityType(
            String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVirtualPoolHighAvailabilityMismatch(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVirtualPoolNotValidForArray(String haNhVpoolId,
            String haNhId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVolumeExportMismatch(URI volUri, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVolumeExportProjectsMismatch(URI blockProject,
            URI uri);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVolumeExportVirtualArrayMismatch(URI id,
            URI virtualArray, URI virtualArray2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVolumeHasNoContinuousCopies(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVolumeMirrorMismatch(URI mid, URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException blockObjectHasNoConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVolumeNotOnSystem(URI id, URI srcStorageSystemURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterVolumeSnapshotAlreadyExists(String name, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidPermissionType(String permission);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidUserType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidPermissionForACL(String permission);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException missingValueInACE(String opName, String inputParamName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidPermission(String permission);
    
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidNFSPermission(String permission);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException bothUserAndGroupInACLFound(String user, String group);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException shareACLAlreadyExists(String opType, String acl);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException nfsACLAlreadyExists(String opType, String acl);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException multipleACLsWithUserOrGroupFound(String opType, String userOrGroup);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException multipleDomainsFound(String opType, String domain1, String domain2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException shareACLNotFoundFound(String opType, String acl);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException nfsACLNotFound(String opType, String acl);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidProjectConflict(URI projectId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidProtocolsForVirtualPool(String type, Set<String> requested,
            String... valid);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidSecurityType(String security);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException keyCertificateVerificationFailed();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException keyCertificateVerificationFailed(final Throwable e);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException failedToLoadCertificateFromString(final String cert,
            final Throwable e);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException failedToStoreCertificateInKeyStore(final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException failedToLoadCertificateFromString(final String... certs);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException apiEndpointNotMatchCertificate(final String apiEndpoint);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException failedToLoadKeyFromString();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException failedToLoadKeyFromString(final Throwable e);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException trustStoreUpdatePartialSuccess(
            final List<String> failedParse, final List<String> expired, final List<String> notInTrustStore);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException invalidSeverityInURI(final String severity, final String severities);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidSystemType(final String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_TIME_FORMAT)
    public BadRequestException invalidTimeBucket(final String time);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_TIME_FORMAT)
    public BadRequestException invalidTimeBucket(final String time, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException invalidURI(final String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException invalidURI(final String value, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException invalidURI(final URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidVirtualPoolSpecifiedVMAXThin();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidVolumeForProtectionSet();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidVolumeParamsAllOrNoneShouldSpecifyLun(
            List<VolumeParam> volumes);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidVolumeSize(long size, long maximum);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidWwnForFcInitiatorNode();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidNodeForiScsiPort();

    @DeclareServiceCode(ServiceCode.API_INVALID_PROTECTION_VPOOLS)
    public BadRequestException invalidProtectionVirtualPools();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidWwnForFcInitiatorPort();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException leastVolumeSize(String sizeInGB);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException licenseIsNotValid(final String cause);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException licenseTextIsEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException mandatorySystemTypeRaidLevels();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException mirrorDoesNotBelongToVolume(final URI mid, final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException missingParameterSystemTypeforAutoTiering();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException networkCanOnlyBeAssociatedWithASingleVirtualArray(URI id);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    public BadRequestException networkSystemExistsAtIPAddress(final String ipAddress);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    public BadRequestException networkSystemSMISProviderExistsAtIPAddress(final String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException newSizeShouldBeLargerThanOldSize(String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException ipInterfaceNotCreatedManuallyAndCannotBeDeleted();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException noIntiatorsConnectedToVolumes();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noMatchingRecoverPointProtectionPools(final URI varrayId,
            final URI vPoolId, final Set<String> varrayIds);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noMatchingRecoverPointStoragePoolsForVpoolAndVarrays(
            final URI vpoolId, final Set<String> varrayId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noMatchingStoragePoolsForVpoolAndVarray(final URI vpoolId,
            final URI varrayId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noMatchingStoragePoolsForVpoolAndVarrayForClones(final URI vpoolId,
            final URI varrayId, final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noStoragePoolsForVpoolInVarray(final URI varrayId, final URI vpoolId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noMatchingStoragePoolsForContinuousCopiesVpoolForVplex(final String varrayLabel, final String vpoolLabel,
            final String storageSystem);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException noRoleSpecifiedInAssignmentEntry();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noStorageFoundForVolume();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noStorageFoundForVolumeMigration(final URI vPoolId,
            final URI vArrayId, final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notAnInstanceOf(final String clazzName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_NOT_FOUND)
    public BadRequestException noTenantDefinedForUser(final String username);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noVolumesToSnap();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException noVolumesSpecifiedInRequest();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException noVolumesToBeAddedRemovedFromCG();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noVPlexSystemsAssociatedWithStorageSystem(final URI systemId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException numberOfInstalledExceedsMax();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException onlyNameAndMaxResourceCanBeUpdatedForSystemWithType(
            final String systemType);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_INGESTED_VOLUME_OPERATION)
    public BadRequestException operationNotPermittedOnIngestedVolume(final String operation,
            final String volumeName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterForSearchCouldNotBeCombinedWithAnyOtherParameter(
            String name, String first, String second);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException parameterForSearchCouldOnlyBeCombinedWithOtherParameter(
            String resourceType, String first, String second);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException parameterForSearchHasInvalidSearchValueWithSuggestions(
            final String resourceTypename, final String parameter, final String value,
            Object[] validValues);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException parameterIsNotOneOfAllowedValues(final String parameter,
            final String allowedValues);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException parameterIsNotValid(final String parameter);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException parameterIsNotValid(final String parameter, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException parameterIsNotValidURI(final String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException parameterIsNotValidURI(final URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException parameterIsNotValidURI(final URI value, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException parameterIsNullOrEmpty(final String parameter);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterIsOnlyApplicableTo(final String parameterName,
            final String applicableTo);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterValueIsNotValid(final String parameterName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterValueCannotBeUpdated(final String parameterName,
            final String reason);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException failedToChangeThePassword();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException failedToValidateThePassword();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    public BadRequestException parameterMustBeGreaterThan(final String parameter,
            final Number greaterThan);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterNotSupportedFor(final String parameter,
            final String unsupportedFor);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    public BadRequestException parameterNotWithinRange(final String parameter, Number value,
            final Number min, final Number max, String units);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterOnlySupportedForVmaxAndVnxBlock(final String propertyName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterOnlySupportedForVmax(final String propertyName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException vArrayUnSupportedForGivenVPool(final URI vPool, final URI vArray);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterTooShortOrEmpty(final String parameterName,
            final Number minNumberOfCharacters);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parentTenantIsNotRoot();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException poolNotBelongingToSystem(final URI port, final URI system);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException computeElementNotBelongingToSystem(final URI ce, final URI system);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException computeElementNotFound(final URI ce);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException portNotBelongingToSystem(final URI port, final URI system);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException propertyIsNotValid(final String property);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException propertyIsNullOrEmpty();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException propertyValueDoesNotMatchAllowedValues(final String property,
            final String allowedValues);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException propertyValueLengthExceedMaxLength(final String property,
            final int maxLen);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException propertyValueLengthLessThanMinLength(final String property,
            final int minLen);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException propertyValueTypeIsInvalid(final String property, final String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException upgradeCheckFrequencyNotPositive();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectedVolumesNotSupported();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException protectionForRpClusters();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException protectionFullCopyAlreadyActive(URI fullCopyId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionNoCopyCorrespondingToVirtualArray(URI varray);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidCopyMode(String copyMode);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException duplicateRemoteSettingsDetected(URI varray);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionNotSpecifiedInVirtualPool();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException protectionOnlyFullCopyVolumesCanBeActivated();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException protectionOnlyFullCopyVolumesCanBeDetached();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException protectionSystemMappingError(final String varray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException protectionUnableToGetSynchronizationProgress();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionVirtualPoolArrayMissing();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionVirtualPoolDoesNotSupportExpandingMirrors(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionVirtualPoolDoesNotSupportHighAvailabilityAndRecoverPoint(
            URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionVirtualPoolJournalSizeInvalid(String type,
            String journalSize);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException protectionVolumeNotFullCopy(URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException protectionVolumeNotFullCopyOfVolume(URI fullCopyId,
            URI sourceVolumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException protectionVolumeInvalidTargetOfVolume(URI copyId,
            URI sourceVolumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException providedVirtualPoolNotCorrectType();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException recoverPointProtectionSystemError();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException requestedVolumeIsNotVplexVolume(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException requiredParameterMissingOrEmpty(final String field);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    public BadRequestException resourceAlreadyExistsWithProperty(final String clazz, final URI id,
            final String property, final String value);

    @DeclareServiceCode(ServiceCode.API_ALREADY_REGISTERED)
    public BadRequestException resourceAlreadyRegistered(final String resourceType,
            final URI resourceId);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DELETE)
    public BadRequestException resourceCannotBeDeleted(final String resource);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException resourcedoesNotBelongToClusterTenantOrg(String resource);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException resourcedoesNotBelongToHostTenantOrg(String resource);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException resourceEmptyConfiguration(final String resource);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    public BadRequestException resourceExistsWithSameName(final String resourceType);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    public BadRequestException resourceHasActiveReferences(final String clazz, final URI resourceId);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    public BadRequestException resourceInClusterWithAutoExportDisabled(final String clazz, final URI resourceId);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    public BadRequestException resourceHasActiveReferencesWithType(final String clazz,
            final URI resourceId, final String depType);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_CANNOT_BE_DELETE_DUE_TO_UNREACHABLE_VDC)
    public BadRequestException resourceCannotBeDeleteDueToUnreachableVdc();

    @DeclareServiceCode(ServiceCode.API_NOT_REGISTERED)
    public BadRequestException resourceNotRegistered(final String resourceType, final URI resourceId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException sameVirtualArrayAndHighAvailabilityArray();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException sameVolumesInAddRemoveList();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException searchFilterMustContainEqualTo();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException searchFilterMustContainPercentU();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException snapshotExportPermissionReadOnly();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException snapshotSMBSharePermissionReadOnly();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotNotActivated(String snapshot);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotEstablishGroupRelationForInactiveSnapshot(final String snapshot);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotNullSettingsInstance(String snapshot);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotParentHasActiveMirrors(String parentLabel, int mirrorCount);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotParentForVPlexHasActiveMirrors(String parentLabel, String vplexVolumelabel, String vplexVolumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException storageSystemNotConnectedToCorrectVPlex(URI tgtStorageSystemURI,
            URI vplexSystemURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException targetAndSourceStorageCannotBeSame();

    @DeclareServiceCode(ServiceCode.API_BAD_HEADERS)
    public BadRequestException theMediaTypeHasNoMarshallerDefined(final String mediaType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException theParametersAreNotValid(final String parameter);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException theParametersAreNotValid(final String parameter,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException theURIIsNotOfType(final URI id, final String name);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException theURIsOfParametersAreNotValid(final String parameter,
            final Set<String> value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException thinVolumePreallocationPercentageOnlyApplicableToThin();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException thinVolumePreallocationPercentageOnlyApplicableToVMAX();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unableToCreateMarshallerForMediaType(final String mediaType);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DELETE)
    public BadRequestException unableToDeactivate(URI id);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DELETE)
    public BadRequestException unableToDeactivateDueToDependencies(URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unableToDeleteNetworkContainsEndpoints();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException executionWindowAlreadyExists(String name, String tenantId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException unableToEncodeString(final String parameter, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_NOT_FOUND)
    public BadRequestException unableToFindEntity(final URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_NOT_FOUND)
    public BadRequestException unableToFindTenant(final URI value);

    @DeclareServiceCode(ServiceCode.DBSVC_ENTITY_NOT_FOUND)
    public BadRequestException unableToFindSMISProvidersForIds(List<URI> ids);

    @DeclareServiceCode(ServiceCode.DBSVC_ENTITY_NOT_FOUND)
    public BadRequestException unableToFindStorageProvidersForIds(List<URI> ids);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException unableToFindSuitablePoolForProtectionVArray(final URI varrayId);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    public BadRequestException unableToUpdateDiscoveredNetworkForStoragePort();

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    public BadRequestException unableToUpdateStorageSystem(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException unexpectedClass(final String expectedClazz, final String actualClazz);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException unexpectedValueForProperty(final String propertyName,
            final String expectedPropertyValue, final String actualPropertyValue);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException uniqueLunsOrNoLunValue();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException unknownParameter(final String operationName,
            final String parameterName);

    @DeclareServiceCode(ServiceCode.API_UNKNOWN_RP_CONFIGURATION)
    public BadRequestException unknownRPConfiguration();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException unsupportedSystemType(final String systemType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException updateVirtualPoolOnlyAllowedToChange();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException updatingFileSystemExportNotAllowed(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException updatingSnapshotExportNotAllowed(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException userMappingDuplicatedInAnotherTenant(final String userMapping);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException userMappingDuplicatedInAnotherTenantExtended(
            final String userMapping, final String tenantId);

    @DeclareServiceCode(ServiceCode.API_BAD_VERSION)
    public BadRequestException versionNotExist(String string);

    @DeclareServiceCode(ServiceCode.API_BAD_VERSION)
    public BadRequestException versionIsInstalled(final String version);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException versionIsNotAvailableForUpgrade(final String version);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException versionIsNotRemovable(final String version);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    public BadRequestException versionIsNotUpgradable(final String target, final String current);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException virtualPoolDoesNotSupportExpandable();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException virtualPoolDoesNotSupportHighAvailabilityAndRecoverPoint();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException orderServiceNotFound(final String serviceId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException orderServiceDescriptorNotFound(final String serviceId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException virtualPoolNotForFileBlockStorage(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException virtualPoolDoesNotSupportSRDFAsyncVolumes(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException virtualPoolSupportsVmaxVnxblockWithRaid();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldRequired(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldBelowMin(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldAboveMax(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldBelowMinStorgeSize(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldAboveMaxStorageSize(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldBelowMinLength(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldBeyondMaxLength(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldNonNumeric(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldNonText(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldNonBoolean(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException serviceFieldNonInteger(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException executionWindowLengthBelowMin(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException executionWindowLengthAboveMax(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException baseServiceNotFound(final String baseServiceId);

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    public BadRequestException vplexPlacementError(final URI uri);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    public BadRequestException vPoolChangeNotValid(URI srcS, URI tgt);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    public BadRequestException vPoolUpdateNotAllowed(final String associate);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException wrongHighAvailabilityVArrayInVPool(final String varray);

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    public BadRequestException noClientProvided();

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    public BadRequestException noEndPointsForNetwork();

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    public BadRequestException noIpNetworksFound();

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    public BadRequestException noIpNetworksFoundForStorageSystem();

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    public BadRequestException multipleIpNetworksFound();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noStorageForPrimaryVolumesForVplexVolumeCopies();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noStorageForHaVolumesForVplexVolumeCopies();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noStoragePortForNetwork(final String network);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException hostsNetworksNotRegistered(final String network);

    @DeclareServiceCode(ServiceCode.API_INVALID_VOLUME_TYPE)
    public BadRequestException notSupportedSnapshotVolumeType(final String volumeType);

    @DeclareServiceCode(ServiceCode.API_BAD_ATTACHMENT)
    public BadRequestException attachmentLogsSizeError(final long currentSize, final long logsZie,
            final long maxSize, final String queryParams);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException connectEMCNotConfigured();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException eventsNotAllowedOnNonControlNode();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException isilonSnapshotRestoreNotSupported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException hostProjectChangeNotAllowed(String name);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException clusterProjectChangeNotAllowed(String name);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException maxNativeSnapshotsIsZero(String virtualPool);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteVolumeBlockSnapShotExists(String dependencies);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotStopSRDFBlockSnapShotExists(String volumeLabel);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException principalSearchFailed(final String userName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException searchForPrincipalsFailedForThisTenant(
            final String commaSeperatedPrincipals);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException inventoryDeleteNotSupportedonExportedVolumes(final String nativeGuid);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException notSupportedForInternalVolumes();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException inventoryDeleteNotSupportedOnSnapshots(final String nativeGuid);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException consistencyGroupAddVolumeThatIsInDifferentProject(final String name,
            final String expected, final String actual);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotReleaseFileSystemExportExists(String dependencies);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotReleaseFileSystemSharesExists(String dependencies);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotReleaseFileSystemSnapshotExists(Integer snapCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotReleaseFileSystemWithTasksPending();

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_PERMISSIONS)
    public BadRequestException cannotReleaseFileSystemRootTenantLacksVPoolACL(String virtualPool);

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_PERMISSIONS)
    public BadRequestException unauthorizedAccessToNonPublicResource();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException missingPersonalityAttribute(String volumeId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noMatchingSRDFPools(final URI varrayId, final URI vPoolId,
            final Set<String> varrayIds);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException unableToFindSuitablePoolForTargetVArray(final URI varrayId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException srdfNoSolutionsFoundError();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteSRDFVolumes(final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotCreateSRDFVolumes(final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException srdfVolumeMissingPersonalityAttribute(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterRPNotSupportedWithSRDF();

    // inactiveRemoteVArrayDetected
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException inactiveRemoteVArrayDetected(final URI vArray);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException parameterVPLEXNotSupportedWithSRDF();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException minPathsGreaterThanMaxPaths();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException pathsPerInitiatorGreaterThanMaxPaths();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException maxPathsRequired();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException parameterMaxResourcesMissing();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException sameVirtualArrayInAddRemoveList();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException virtualArrayHasPortFromOtherVPLEXCluster(final String portId,
            final String varrayId);

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    public BadRequestException noStoragePortFoundForVArray(final String varrayId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidParameterForVarrayNetwork(final String varrayId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException consistencyGroupBelongsToTarget(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException consistencyGroupbelongstoSRDF(URI id);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    public BadRequestException deleteOnlyAllowedOnEmptyCGs(final String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException notAllowedOnSRDFConsistencyGroups();

    @DeclareServiceCode(ServiceCode.SYS_IMAGE_DOWNLOAD_FAILED)
    public BadRequestException downloadFailed(final String version, final String url);

    @DeclareServiceCode(ServiceCode.SYS_IMAGE_DOWNLOAD_FAILED)
    public BadRequestException invalidImageUrl(final String version, final String url);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotChangePortVarraysExportExists(final String portId,
            final String varrayId, final String exportId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotChangePoolVarraysVolumeExists(final String poolId,
            final String varrayId, final String volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException failoverCopiesParamCanOnlyBeOne();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException swapCopiesParamCanOnlyBeOne();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException failOverCancelCopiesParamCanOnlyBeOne();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidCopyType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidSRDFCopyMode(String copyMode);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotChangeSRDFCopyMode(String volumeNativeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidCopyModeOp(String newCopyMode, String vpoolCopyMode);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidCopyIDCombination(String type);

    @DeclareServiceCode(ServiceCode.API_INVALID_ACTION_FOR_VPLEX_MIRRORS)
    public BadRequestException actionNotApplicableForVplexVolumeMirrors(final String actionName);

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    public BadRequestException unableToFindSuitableStorageSystemsforSRDF(final String grpname1);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException unsupportedConfigurationWithMultipleAsyncModes();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException groupNameCannotExceedEightCharactersoronlyAlphaNumericAllowed();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotRemovePoolWithResources(final Set<String> poolIds);

    @DeclareServiceCode(ServiceCode.OBJ_PROJECT_INVALID)
    public BadRequestException invalidObjProject(URI projectId);

    @DeclareServiceCode(ServiceCode.OBJ_PROJECT_NOT_FOUND_FOR_NAMESPACE)
    public BadRequestException objProjectNotFoundForNamespace(String namespace);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_NOT_FOUND_FOR_NAMESPACE)
    public BadRequestException objVpoolNotFoundForNamespace(String namespace);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_INVALID)
    public BadRequestException invalidObjVpool(URI vpoolId);

    @DeclareServiceCode(ServiceCode.DATASERVICE_INVALID_VARRAY)
    public BadRequestException invalidDataServiceVarray(URI vArrayId);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_NOT_COMPATIBLE)
    public BadRequestException objVpoolNotCompatible(URI vpoolId);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_EMPTY)
    public BadRequestException objVpoolEmpty(URI vpoolId);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_LISTS_NOT_MUTUALLY_EXCLUSIVE)
    public BadRequestException objVpoolListsNotMutuallyExclusive(List<String> allowed, List<String> disallowed);

    @DeclareServiceCode(ServiceCode.OBJ_BUCKET_EXISTS)
    public BadRequestException objBucketExists(URI bucketId);

    @DeclareServiceCode(ServiceCode.OBJ_BUCKETNAME_INVALID)
    public BadRequestException invalidObjBucketName(String bucketName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException objBucketIsNotEmpty(String bucketName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException objBucketIsHidden(String bucketName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException virtualToNonVirtualProtectionNotSupported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException nonVirtualToVirtualProtectionNotSupported();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeExpandNotSupportForRPVPlex();

    @DeclareServiceCode(ServiceCode.OBJ_BUCKETNAME_INVALID)
    public BadRequestException objBucketNameEmpty();

    @DeclareServiceCode(ServiceCode.OBJ_NOT_BUCKT_OWNER)
    public BadRequestException invalidBucketOwner(String ownerName, String bucketName);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_TYPE_INVALID)
    public BadRequestException objInvalidVpoolType(String vpoolType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotReduceVpoolMaxPaths(final String exportGroupId, final String exportMask);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException noTokenProvided();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotChangeVpoolPathsPerInitiator(final String exportGroupId, final String exportMask);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotRemoveVArrayWithPools(final Set<String> varrayIds);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotRemoveVArrayWithVPoolResources(final Set<String> varrayIds);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException snapshotNotAllowedOnSRDFAsyncVolume(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_INVALID_VARRAY_NETWORK_CONFIGURATION)
    public BadRequestException invalidVarrayNetworkConfiguration(final String varrayLabel, final String vplexLabel);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterConsistencyGroupVolumeHasIncorrectVArray(URI cg, URI expected);

    @DeclareServiceCode(ServiceCode.API_NO_DOWNLOAD_IN_PROGRESS)
    public BadRequestException noDownloadInProgress();

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    public BadRequestException computeSystemExistsAtIPAddress(final String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterCannotDeactivateRegisteredComputeSystem(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterOsInstallNetworkDoesNotExist(final String network);

    @DeclareServiceCode(ServiceCode.API_INVALID_VPOOL_FOR_INGESTION)
    public BadRequestException virtualPoolIsForVplex(final String vpoolLabel, final String highAvailabilityParam);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException multiVolumeConsistencyMustBeEnabledWithRP();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException consistencyGroupMissingForRpProtection();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException consistencyGroupMustBeEmptyOrContainRpVolumes(final URI cgUri);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException consistencyGroupIsNotCompatibleWithRequest(final URI cgUri, final String compatibleTypes,
            final String requestedTypes);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidVpoolUsedForRpConsistencyGroup(final URI cgUri, final URI correctVpoolUri);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException vPoolSourceVarraysNotCompatibleForCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException vPoolTargetVarraysNotCompatibleForCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotAddVolumesToSwappedCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotMixMetroPointAndNonMetroPointVolumes(final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException srdfVolumeVPoolChangeNotSupported(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException srdfVolumeVPoolChangeToNonSRDFVPoolNotSupported(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException srdfInternalError(Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException expandSupportedOnlyOnSource(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException vmaxAllowedOnlyAsSrdfTargets();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteSRDFTargetWithActiveSource(URI target, URI source);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotIsNotForConsistencyGroup(final String snapshotName, final String cgName);

    @DeclareServiceCode(ServiceCode.API_INVALID_MAX_CONTINUOUS_COPIES)
    public BadRequestException invalidMaxContinuousCopiesForVplex(final Integer maxCopies);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cantDeleteVPlexHaVPool(final String vpoolName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_REQUIRED)
    public BadRequestException invalidMirrorVpoolForVplexDistributedVpool();

    @DeclareServiceCode(ServiceCode.API_INVALID_HIGH_AVAILABILITY_FOR_MIRROR_VPOOL)
    public BadRequestException invalidHighAvailabilityForMirrorVpool(final String mirrorVpoolName, final String mirrorVpoolHA,
            final String vpoolHA, final String correctMirrorVpoolHA);

    @DeclareServiceCode(ServiceCode.API_INVALID_VARARY_CONTINUOUS_COPIES_VPOOL)
    public BadRequestException noVarrayForMirrorVpoolWithExpectedVplex(final String vPoolName, final String vplexSystemName,
            final String vplexCluster);

    @DeclareServiceCode(ServiceCode.API_INVALID_VARARY_CONTINUOUS_COPIES_VPOOL)
    public BadRequestException noMirrorVpoolForVplexVolume(final String volumeName);

    @DeclareServiceCode(ServiceCode.API_VPOOL_IN_USE_AS_CONTINUOUS_COPIES_VPOOL)
    public BadRequestException virtualPoolIsSetAsContinuousCopiesVpool(final String vPoolName, final String vPoolNames);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantChangeConnectionStatusOfLocalVDC();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException badVdcId(final String vdcId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterTenantNamespaceIsEmpty();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException tenantNamespaceMappingConflict(final String tenantId, final String namespace);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeNotInVirtualPool(final String volumeName, final String vpoolName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotUpdateProviderIP(String providerKey);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotFindSolutionForRP(String string);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidMetroPointConfiguration();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterProtectionTypeIsEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException objUserExists(final String user);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException objNamespaceExists(final String namespace);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException objNamespaceNameEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException objNamespaceNotEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException objNamespaceNotBound(final String namespace);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException objNamespaceConcurrentlyModified();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException objNamespaceTenantAlreadyBound();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unableToFindNamespaceForTenant(final String userName, final String tenantId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException objNamespaceZonesMisconfiguration();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeNotExpandable(final String volumeName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unsupportedUnManagedVolumeDiscovery(final String systemSerialNumber);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotCreateProtectionForConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionVirtualPoolRemoteCopyModeInvalid(final String remoteCopyMode);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionVirtualPoolRPOTypeInvalid(final String rpoType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionVirtualPoolRPOValueInvalid(final String rpoValue);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionVirtualPoolRPOTypeNotSpecified(final Long rpoValue);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException protectionVirtualPoolRPOValueNotSpecified(final String rpoType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notValidRPSourceVolume(String volname);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException newCertificateMustBeSpecified();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyNotSupportedFromSnapshot(final String systemType, final URI snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyNotSupportedOnArray(final URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyNotSupportedForConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notEnoughComputeElementsInPool();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidUpdatingSPT(final String templateName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException incompatibleSPT(final String templateName, final String varrayName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noComputeSystemsFoundForVarray();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException mustHaveAtLeastOneChange(final String changeClass);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException objBaseUrlConflicts(final String enteredBaseUrl, final String existingBaseUrl);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noRepGroup();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noCosForRepGroup(final String repGroup, final String tenantId, final String zone);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException accessDenied();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    public BadRequestException noRPConnectedVPlexStorageSystemsForTarget(final String vpool,
            final String varray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeactivateStorageSystemActiveRpVolumes();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotExportNotSupported(final String systemType, final URI snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException objNoNamespaceForTenant(URI tenantId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noSecretKeyForUser(URI user);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumesShouldBelongToSameVpool();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException requestedVolumeCountExceedsLimitsForCG(final int count,
            final int maxCount, final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteAuthProviderWithTenants(final int numTenants, final List<URI> tenantIDs);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteAuthProviderWithVdcRoles(final int num, final List<String> users);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteAuthProviderWithTenantRoles(final String tenantName, final int num, final List<String> users);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotExportInitiatorWithNoCompute(final String exportGroup, final String port);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unableToSetUserScopeConfig(final String scope);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException updatingCompletedApproval();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidVarrayForVplex(final String vplex, final String varray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantChangeClusterForLocalVolumeInCG();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidInterval(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordIntervalNotInRange(int min, int max);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidLength(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidLowercaseNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidUppercaseNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidNumericNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidSpecialNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidRepeating(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidChangeNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidDictionary();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidHistory(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordExpired(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidOldPassword();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException passwordInvalidExpireDays(int min, int max);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotCreatePortForSystem(final String systemType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException metroPointConfigurationNotSupported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException metroPointTargetVarrayConfigurationNotSupported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException sptIsNotValidForVarrays(final String spt);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotRemoveVarraysFromCVP(final String computeVirtualPool);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidFloatParameterBelowMinimum(String string, float value, int minimum,
            String unit);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidFloatParameterAboveMaximum(String string, float value, int maximum,
            String unit);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotRemoveVCP(String vcpId);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DEREGISTER)
    public BadRequestException unableToDeregisterProvisionedComputeSystem(final String resource, final String hosts);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DELETE)
    public BadRequestException unableToDeactivateProvisionedComputeSystem(final String resource, final String hosts);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DEREGISTER)
    public BadRequestException unableToDeregisterProvisionedComputeElement(final String resource, final String host);

    @DeclareServiceCode(ServiceCode.API_INTERNAL_SERVER_ERROR)
    public BadRequestException unableToLockBladeReservation();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException vcenterOperationFailed();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notAllowedWhenCGHasSnapshots();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notAllowedWhenCGHasMirrors();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notAllowedInvalidBackendSystem();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notAllowedOnRPConsistencyGroups();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidParameterVolumeAlreadyInAConsistencyGroup(
            final URI cgUri, final URI currentCgUri);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidParameterSourceVolumeNotInGivenConsistencyGroup(
            final URI sourceVolumeUri, final URI cgUri);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumesWithMultipleReplicasCannotBeAddedToConsistencyGroup(
            final String volumeName, final String replicaType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumesWithReplicaCannotBeAdded(
            final String volumeName, final String replicaType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeWithDifferentNumberOfReplicasCannotBeAdded(
            final String volumeName, final String replicaType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantChangeVarrayForVplexVolumeInAppConsistenctCG();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantChangeVpoolForVplexVolumeInAppConsistenctCG();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    public BadRequestException missingParameterSystemTypeforHostIOLimits();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterSystemTypeforHostIOLimits();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterValueforHostIOLimitIOPs();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidParameterValueforHostIOLimitBandwidth();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException exportNotFound(String operatioName, String exportDetails);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException fileSystemNotExported(String operatioName, String id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException exportExists(String operatioName, String exportDetails);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException missingInputTypeFound(String type, String opName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException missingUserOrGroupInACE(String opName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidAnon(String anon);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException unableToProcessRequest(String msg);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidFileExportXML(String msg);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException sameSecurityFlavorInMultipleExportsFound(String msg, String opName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException haVpoolForVpoolUpdateHasInvalidHAVpool(final String haVpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException haVpoolForVpoolUpdateIsInactive(final String haVpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException haVpoolForVpoolUpdateDoesNotExist(final String haVpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException haVpoolForNewHAVpoolForVpoolUpdateDoesNotExist(final String haVpoolId, final String newHaVpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantChangeVpoolNotAllCGVolumes();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantChangeVpoolVolumeIsNotInCG();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException targetVPoolDoesNotSpecifyUniqueSystem();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException targetHAVPoolDoesNotSpecifyUniqueSystem();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidConfigType(final String configType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidScopeFomart(final String scope);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException systemDefaultConfigCouldNotBeModifiedOrDeactivated(final URI configId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException sourceNotExported(final URI sourceId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidParameterRemovePreexistingInitiator(final String maskName, final String initiatorPort);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException failedToFindVDC(String name);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeactivateStoragePool();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeactivateStoragePort();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notAllowedAddVolumeToCGWithIngestedVolumes();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException mixedVolumesinCGForVarrayChange();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantChangeVarrayNotAllCGVolumes();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantChangeVarrayVolumeIsNotInCG();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException nonSRDFVolumeCannotbeAddedToSRDFCG();

    @DeclareServiceCode(ServiceCode.API_INVALID_OBJECT)
    public BadRequestException invalidVplexMirror(String mirrorName, String mirrorId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDetachStorageForHost(String reason);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotUpdateHost(String reason);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException clusterContainsNoCompatibleHostsForVcenter();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeForVarrayChangeHasSnaps(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeForVpoolChangeHasSnaps(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeForVpoolChangeHasMirrors(final String volumeId, final String volumeLabel);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeForVarrayChangeHasMirrors(final String volumeId, final String volumeLabel);

    @DeclareServiceCode(ServiceCode.API_TASK_EXECUTION_IN_PROGRESS)
    public BadRequestException cannotExecuteOperationWhilePendingTask(final String pendingVolumes);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException rpClusterVarrayNoClusterId(String label);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException rpClusterVarrayInvalidClusterId(String label);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException rpClusterVarrayInvalidVarray(String label, String clusterId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cgContainsTooManyVolumesForVPoolChange(final String cgId, final int cgVolumes, final int maxCgVolumes);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cgContainsTooManyVolumesForVArrayChange(final String cgId, final int cgVolumes, final int maxCgVolumes);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException vpoolNotAssignedToVarrayForVarrayChange(final String vpoolId, final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException storageSystemsNotConnectedForAddVolumes(final String listOfArrays);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidVplexCgName(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException subDirNotFound(final String msg);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException userMappingAttributeIsEmpty();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException userMappingNotAllowed(final String user);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteOrEditUserGroup(final int numResources, final Set<URI> resourceIDs);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotRenameUserGroup(final String name);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException authnProviderGroupObjectClassesAndMemberAttributesIsEmpty(
            final String id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException authnProviderGroupObjectClassesAndMemberAttributesRequired(
            final String param);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidStructureForIngestedVolume(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_DUPLICATE_EXPORT_GROUP_NAME_SAME_PROJECT_AND_VARRAY)
    public BadRequestException duplicateExportGroupProjectAndVarray(final String egName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException incompatibleGeoVersions(final String version, final String feature);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException operationNotSupportedForSystemType(final String operation, final String storageSystemType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException userGroupExistsAlready(final String name);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteAuthnProviderWithUserGroup(final int numResources, final Set<URI> resourceIDs);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidFullCopySource(final String copySourceId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantCreateFullCopyForVPlexSnapshot();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidFullCopyCountForVolumesInConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidMirrorCountForVolumesInConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyRestoreNotSupportedForSnapshot();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyResyncNotSupportedForSnapshot();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyOperationNotAllowedOnEmptyCG(final String cgId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyOperationNotAllowedNotAFullCopy(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyOperationNotAllowedSourceNotInCG(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantDeleteFullCopyNotDetached(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantStopSRDFFullCopyNotDetached(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantCreateNewVolumesInCGActiveFullCopies(final String cgId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantUpdateCGActiveFullCopies(final String cgId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException detachedFullCopyCannotBeActivated(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException detachedFullCopyCannotBeRestored(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException inactiveFullCopyCannotBeRestored(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyCannotBeRestored(final String fullCopyId, final String state);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException detachedFullCopyCannotBeResynchronized(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException inactiveFullCopyCannotBeResynchronized(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyCannotBeResynchronized(final String fullCopyId, final String state);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotEstablishGroupRelationForDetachedFullCopy(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotEstablishGroupRelationForInactiveFullCopy(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotCheckProgressFullCopyDetached(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeForVarrayChangeHasFullCopies(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeForVpoolChangeHasFullCopies(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeForRPVpoolChangeHasFullCopies(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeForSRDFVpoolChangeHasFullCopies(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noFullCopiesForVMAX3VolumeWithActiveSnapshot(final String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noSnapshotsForVMAX3VolumeWithActiveFullCopy();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notSupportedSnapshotWithMixedArrays(URI cgUri);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyExpansionNotAllowed(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException overlappingAttributesNotAllowed(final String userGroupName,
            final Set<String> overlappingUserGroups);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantCreateFullCopyOfVNXFullCopy();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantCreateFullCopyOfVPlexFullCopyUsingVNX();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException maxFullCopySessionLimitExceeded(final URI volume, final int maxStillAllowed);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyNotSupportedByBackendSystem(final URI volume);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noAuthnProviderFound(String userId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException invalidPrincipals(String details);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException storagePoolsRequireVplexForProtection(final String personality, final String vpoolName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException migrationCantBePaused(String migrationName, String status);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException migrationCantBeResumed(String migrationName, String status);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException migrationCantBeCancelled(String migrationName, String status);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException migrationHasntStarted(String migrationId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidQuotaRequestForObjectStorage(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException virtualPoolNotForObjectStorage(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException insufficientRetentionForVirtualPool(final String label, final String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException consistencyGroupContainsNoVolumes(final URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException targetVirtualArrayDoesNotMatch(final URI consistencyGroup, final URI virtualArray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noValidSrdfTargetVolume(final URI volumeId, final URI virtualArray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException volumeMustBeSRDFProtected(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException srdfCgContainsNoSourceVolumes(final URI consistencyGroupId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException consistencyGroupMustBeSRDFProtected(final URI consistencyGroupId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException consistencyGroupMustBeRPProtected(final URI consistencyGroupId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotRemoveTenant(final String resource, final String name, final Set<String> tenants);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unsupportedNumberOfPrivileges(final URI tenantId, final List<String> privileges);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unsupportedPrivilege(final URI tenantId, final String privilege);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDetachResourceFromTenant(final String resource);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException resourceCannotBelongToProject(final String resource);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException tenantDoesNotShareTheVcenter(final String tenantName, final String vCenterName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException rpBlockApiImplPrepareVolumeException(final String volume);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException invalidEntryForProjectVNAS();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException failedToAssignVNasToProject(final String assignVnasError);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException failedToDeleteVNasAssignedProject();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noVNasServersAssociatedToProject(final String project);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException vNasServersNotAssociatedToProject();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException oneOrMorevNasServersNotAssociatedToProject();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteOrUpdateImageServerWhileInUse();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noExistingVolumesInCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noSourceVolumesInCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException noProtectionSystemAssociatedWithTheCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unableToFindSuitableJournalRecommendation();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unableToFindJournalRecommendation(final String rpSiteName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException unableToFindTheSpecifiedCopy(final String copy);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotNotAllowedWhenCGAcrossMultipleSystems();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyNotAllowedWhenCGAcrossMultipleSystems();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantAddMixVolumesToIngestedCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notAllVolumesAddedToIngestedCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyNotAllowedForIngestedCG(final String uri);

    @DeclareServiceCode(ServiceCode.API_DELETION_IN_PROGRESS)
    public BadRequestException deletionInProgress(final String dataObjectType, final String dataObjectName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException imageNotPresentOnComputeImageServer(final String computeImage, final String computeImageServer);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException noImageServerAssociatedToComputeSystem(final String computeSystem);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException clientIpNotExist();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotShareVcenterWithMultipleTenants(final String vcenterName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotRemoveDatacenterTenant(final String dataCenterName, final String vcenterName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotEditVcenterOrUpdateACL(final String vcenterName, final long refreshInterval);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cgReferencesInvalidProtectionSystem(final URI cgUri, final URI protectionSystemUri);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException notEnoughPortsForMaxpath(final URI storageURI, final Integer portCount, final Integer maxPaths);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException pathParameterPortsDoNotIncludeArray(final URI arrayURI);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotOverrideVpoolPathsBecauseExistingExports(final String message);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotAddSRDFProtectionToPartialCG(String msg);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cannotDeleteImageServer();

    @DeclareServiceCode(ServiceCode.API_PRECONDITION_FAILED)
    public BadRequestException cannotAddImageWithoutImageServer();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantUpdateCGWithMixedBlockObjects(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantUpdateCGWithReplicaFromMultipleSystems(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantExposeNonVPLEXSnapshot(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantExposeInactiveSnapshot(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantExposeUnsynchronizedSnapshot(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantExposeSnapshotAlreadyExposed(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cantDeleteSnapshotExposedByVolume(final String snapshotId, final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException fullCopyNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException snapshotNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException mirrorNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException expansionNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException cgNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException varrayChangeNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException vpoolChangeNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    public BadRequestException cannotUpdateTFTPBOOTDirectory();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException rpBlockApiImplRemoveProtectionException(final String message);
    
    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException dbConsistencyCheckAlreadyProgress();
    
    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    public BadRequestException canNotCanceldbConsistencyCheck();
}
