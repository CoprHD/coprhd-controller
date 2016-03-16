/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import com.emc.storageos.driver.scaleio.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.driver.scaleio.errorhandling.annotations.MessageBundle;
import com.emc.storageos.driver.scaleio.models.block.export.VolumeParam;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
    BadRequestException modificationOfGroupAttributeNotAllowed(final String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException authnProviderCouldNotBeValidated(final String errorMsg);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException autoTieringNotEnabledOnStorageSystem(final URI systemId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException blockSourceAlreadyHasContinuousCopiesOfSameName(List<String> dupList);

    @DeclareServiceCode(ServiceCode.API_CANNOT_REGISTER)
    BadRequestException cannotAddStorageSystemTypeToStorageProvider(String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotCreateRPVolumesInCG(final String volumeName, final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotCreateSnapshotOfVplexCG();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException snapshotsNotSupportedForRPCGs();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotCreateVolumeAsConsistencyGroupHasSnapshots(String label, URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotCreateVolumeAsConsistencyGroupHasMirrors(String label, URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeactivateStorageSystem();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeactivateObjectIngestionTask();

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    BadRequestException cannotDeleteProviderWithManagedStorageSystems(final URI systemId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDiscoverUnmanagedResourcesForUnsupportedSystem();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotExpandMirrorsUsingMetaVolumes();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotHaveFcAndIscsiInitiators();

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    BadRequestException cannotImportExportedVolumeToVplex(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    BadRequestException cannotImportConsistencyGroupVolumeToVplex(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotMixLdapAndLdapsUrls();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotPauseContinuousCopyWithSyncState(URI mid, String syncState,
                                                               URI vid);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotPauseContinuousCopyWhileResynchronizing(URI mid,
                                                                      String syncState, URI vid);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotResumeContinuousCopyWithSyncState(URI mid, String syncState,
                                                                URI vid);

    @DeclareServiceCode(ServiceCode.API_CANNOT_REGISTER)
    BadRequestException cannotRegisterSystemWithType(final String systemType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotRemoveAllValues(final String fieldName, final String objectName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotUnassignNetworkInUse(URI networkId, URI associatedId,
                                                   String associatedType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException changeInVNXVirtualPoolNotSupported(final URI vpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException changePropertyIsNotAllowed(final String property);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException changesNotSupportedFor(final String propertyTryingToBeChanged,
                                               final String unsupportedFor);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException changeToVirtualPoolNotSupported(final URI vpoolId,
                                                        final String notSuppReasonBuff);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException changeToComputeVirtualPoolNotSupported(final String vpool,
                                                               final String notSuppReasonBuff);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException changeToVirtualPoolNotSupportedForNonCGVolume(
            final URI volumeId, String vpoolLabel);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException configEmailError();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException consistencyGroupNotCreated();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException consistencyGroupStillBeingCreated(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException currentAndRequestedVArrayAreTheSame();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException domainAlreadyExists(String domain);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException duplicateChildForParent(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException duplicateEntityWithField(String entityName, String fieldName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException duplicateLabel(String entityName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException duplicateNamespace(String entityName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException endpointsCannotBeAdded(final String endpoints);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException endpointsCannotBeRemoved(final String endpoints);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException endpointsCannotBeUpdatedActiveExport(final String endpoint);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterUnManagedFsListEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterForUnManagedVolumeQuery(String exportType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterProjectEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVirtualArrayEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVirtualPoolEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException endTimeBeforeStartTime(final String startTime, final String endTime);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INACTIVE)
    BadRequestException entityInRequestIsInactive(final URI value);

    @DeclareServiceCode(ServiceCode.API_EXCEEDING_LIMIT)
    BadRequestException exceedingLimit(final String of, final Number limit);

    @DeclareServiceCode(ServiceCode.API_EXCEEDING_ASSIGNMENT_LIMIT)
    BadRequestException exceedingRoleAssignmentLimit(final Number limit, final Number value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException initiatorExportGroupInitiatorsBelongToSameHost();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceURLBadSyntax();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException expansionNotSupportedForMetaVolumesWithMirrors();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException expansionNotSupportedForHitachThickVolumes();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException expansionNotSupportedForHitachiVolumesNotExported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException failedToCreateAuthenticationHandlerDomainsCannotBeNull(final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException failedToCreateAuthenticationHandlerManagerUserDNPasswordAreRequired(
            final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException failedToCreateAuthenticationHandlerSearchBaseCannotBeNull(
            final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException failedToCreateAuthenticationHandlerSearchFilterCannotBeNull(
            final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException failedToCreateAuthenticationHandlerServerURLsAreRequired(final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException fileSizeExceedsLimit(final long maxSize);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fileSystemHasExistingExport();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotHasExistingExport();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotHasNoExport(final URI value);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException getNodeDataForESRSFailure(final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ZONE)
    BadRequestException illegalZoneName(final String zoneName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException illegalZoneMember(final String zoneMemeber);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ZONE)
    BadRequestException nameZoneLongerThanAllowed(String zoneName, final int zoneNameAllowedLength);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException illegalWWN(final String wwn);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException illegalWWNAlias(final String alias);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_FSNAME)
    BadRequestException invalidFileshareName(final String fsName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_QDNAME)
    BadRequestException invalidQuotaDirName(final String fsName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException initiatorHostsInSameOS();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException initiatorNotConnectedToStorage(String initiator, String storage);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException errorVerifyingInitiatorConnectivity(String initiator,
                                                            String storage, String error);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException initiatorNotCreatedManuallyAndCannotBeDeleted();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException initiatorInClusterWithAutoExportDisabled();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException initiatorPortNotValid();

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_QUOTA)
    BadRequestException insufficientQuotaForVirtualPool(final String label, final String type);

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_QUOTA)
    BadRequestException insufficientQuotaForProject(final String label, final String type);

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_QUOTA)
    BadRequestException insufficientQuotaForTenant(final String label, final String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidACL(String ace);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidACLTypeEmptyNotAllowed();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidACLTypeMultipleNotAllowed();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidAutoTieringPolicy();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidDriveType(final String systemType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidBlockObjectToExport(String label, String simpleName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException invalidDate(final String date);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidEndpointExpectedFC(String endpoint);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidEndpointExpectedNonFC(String endpoint);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidEntryACLEntryMissingTenant();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidEntryForProjectACL();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidEntryForCatalogServiceACL();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    BadRequestException invalidEntryForRoleAssignment(final String role);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    BadRequestException invalidEntryForRoleAssignmentDetails(final String role,
                                                             final String details);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    BadRequestException invalidRoleAssignments(final String details);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    BadRequestException invalidEntryForRoleAssignmentSubjectIdAndGroupAreNull();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_ROLE)
    BadRequestException invalidEntryForRoleAssignmentSubjectIdsCannotBeALocalUsers(
            final String localUsers);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException invalidEntryForUsageACL(final String tenant);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidField(final String field, final String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidField(final String field, final String value,
                                     final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidHighAvailability(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidInput(final Integer line, final Integer column);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidIpProtocol();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidIscsiInitiatorPort();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameter(final String parameterName,
                                         final String parameterValue);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameter(final String parameterName,
                                         final String parameterValue, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    BadRequestException invalidParameterAboveMaximum(String string, long size, long minimum,
                                                     String unit);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterAssignedPoolNotInMatchedPools(String poolStr);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    BadRequestException invalidParameterBelowMinimum(String string, long size, long minimum,
                                                     String unit);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterBlockCopyDoesNotBelongToVolume(URI pid, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterBlockMaximumCopiesForVolumeExceeded(
            Integer maxNativeContinuousCopies, String sourceVolumeName, URI sourceVolumeURI, Integer currentMirrorCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterBlockMaximumCopiesForVolumeExceededForSourceAndHA(
            Integer sourceVpoolMaxCC, Integer haVpoolMaxCC, String sourceVolumeName, String sourceVpoolName, String haVpoolName,
            Integer currentSourceMirrorCount, Integer currentHAMirrorCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterBlockMaximumCopiesForVolumeExceededForSource(
            Integer sourceVpoolMaxCC, String sourceVolumeName, String sourceVpoolName, Integer currentSourceMirrorCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterBlockMaximumCopiesForVolumeExceededForHA(
            Integer haVpoolMaxCC, String sourceVolumeName, String haVpoolName, Integer currentHAMirrorCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterBlockSnapshotCannotBeExportedWhenInactive(
            String label, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterCannotDeactivateRegisteredNetwork(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterCannotDeactivateRegisteredNetworkSystem(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterCannotDeleteDiscoveredNetwork(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterCannotRegisterUnmanagedNetwork(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterClusterAssignedToDifferentProject(String label,
                                                                          String label2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterClusterInDifferentTenantToProject(String label,
                                                                          String label2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterClusterNotInDataCenter(String label, String label2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterClusterNotInHostProject(String label);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupAlreadyContainsVolume(URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupCannotAddProtectedVolume(
            URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupNotForVplexStorageSystem(
            URI consistencyGroup);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupProvidedButVirtualPoolHasNoMultiVolumeConsistency(
            URI consistencyGroup, URI vpool);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupStorageySystemMismatch(URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterIBMXIVConsistencyGroupVolumeNotInPool(URI volumeURI, URI poolURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupStorageSystemMismatchVirtualArray(URI cgURI, URI varrayURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupVirtualArrayMismatch(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterUserTenantNamespaceMismatch();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupVirtualPoolMustSpecifyMultiVolumeConsistency(
            URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupVolumeHasIncorrectHighAvailability(
            URI cg, String expected);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupVolumeMismatch(URI volumeURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterEndpointsNotFound(Collection<String> endpoints);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterExportGroupAlreadyHasLun(Integer lun);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidHostConnection();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidVCenterConnection(String message);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidNotAVCenter(String hostname, String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterExportGroupHostAssignedToDifferentProject(
            String hostName, String name);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterExportGroupHostAssignedToDifferentTenant(
            String hostName, String label);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterExportGroupInitiatorNotInHost(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterFileSystemHasNoVirtualPool(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterFileSystemNoSuchExport();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterHighAvailabilityType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterHighAvailabilityVirtualArrayRequiredForType(
            String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterHighAvailabilityVirtualPoolNotValidForVirtualArray(
            String haNhVirtualPoolId, String haVirtualArrayId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterHostNotInCluster(String hostName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterInitiatorBelongsToDeregisteredNetwork(
            Object initiator, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterInitiatorIsDeregistered(Object initiator);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterIpInterfaceIsDeregistered(Object ipInterface);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterInvalidIP(String fieldName, String ip);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterInvalidIPV4(String fieldName, String ip);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterInvalidIPV6(String fieldName, String ip);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterLengthTooLong(String fieldName, String value,
                                                      long maximum);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterNetworkMustBeUnassignedFromVirtualArray(
            String label, String label2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterNetworkUsedByFCZoneReference(String network,
                                                                     String zoneName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterNoActiveInitiatorPortsFound(
            List<String> invalidNetworkPorts);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterNoMatchingPoolsExistToAssignPools(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterNoOperationForTaskId(String taskId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterNoOperationForTaskId(final URI taskId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterNoStorageFoundForVolume(URI id, URI id2, URI id3);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterNoStoragePool(URI storagePoolURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterNoSuchVirtualPoolOfType(URI id, String string);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterOnlyClustersForExportType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterOnlyHostsForExportType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterOnlyHostsOrInitiatorsForExportType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterOnlyInitiatorsForExportType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    BadRequestException invalidParameterPercentageExpected(String parameter, int percent);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterProjectQuotaInvalidatesTenantQuota(Long quota);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    BadRequestException invalidParameterRangeLessThanMinimum(String parameter, long value,
                                                             long minimum);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException invalidParameterSearchMissingParameter(String resourceType,
                                                               String missing);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterSearchProjectNotSupported(String name);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterSearchStringTooShort(String field, String value,
                                                             String name, int minimum);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterSMISProviderAlreadyRegistered(String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterStorageProviderAlreadyRegistered(String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterSMISProviderListIsEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterSMISProviderNotConnected(String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterProviderStorageSystemAlreadyExists(String parameter,
                                                                           String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterStoragePoolHasNoTiers(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterStorageSystemNamespace(String namespace);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterProtectionSystemNamespace(String namespace);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterSystemTypeforAutoTiering();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterTenantsQuotaExceedsProject(long quotaGb,
                                                                   long totalProjects);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterTenantsQuotaExceedsSubtenants(long quotaGb,
                                                                      long totalSubtenants);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterTenantsQuotaInvalidatesParent(Long quota);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterURIInvalid(String fieldName, URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterURIWrongType(String fieldName, URI value, String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidUrl(String fieldName, String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterCannotUpdateComputeImageUrl();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteComputeWhileInUse();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterComputeImageIsNotAvailable(URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterHostHasNoComputeElement();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException osInstallNetworkNotSet();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException osInstallNetworkNotValid(final String network);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException hostPasswordNotSet();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException computeElementHasNoUuid();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException osInstallAlreadyInProgress();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidHostName(String hostName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidHostNamesAreNotUnique();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidNodeNamesAreNotUnique(String nodeName, String prop1, String prop2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidNodeShortNamesAreNotUnique(String shortName, String prop1, String prop2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidNodeNameIsIdOfAnotherNode(String name, String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterHostAlreadyHasOs(String os);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterValueWithExpected(String fieldName, Object value,
                                                          Collection<Object> expected);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterValueWithExpected(String fieldName, Object value,
                                                          Object... expected);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterValueWithExpected(String fieldName, Object value,
                                                          String... expected);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterValueExceedsArrayLimit(String fieldName, Integer value,
                                                               Integer limit);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVirtualArrayAndVirtualPoolDoNotApplyForType(
            String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVirtualPoolAndVirtualArrayNotApplicableForHighAvailabilityType(
            String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVirtualPoolHighAvailabilityMismatch(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVirtualPoolNotValidForArray(String haNhVpoolId,
                                                                    String haNhId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVolumeExportMismatch(URI volUri, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVolumeExportProjectsMismatch(URI blockProject,
                                                                     URI uri);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVolumeExportVirtualArrayMismatch(URI id,
                                                                         URI virtualArray, URI virtualArray2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVolumeHasNoContinuousCopies(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVolumeMirrorMismatch(URI mid, URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException blockObjectHasNoConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVolumeNotOnSystem(URI id, URI srcStorageSystemURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterVolumeSnapshotAlreadyExists(String name, URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidPermissionType(String permission);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidUserType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidPermissionForACL(String permission);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException missingValueInACE(String opName, String inputParamName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidPermission(String permission);
    
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidNFSPermission(String permission);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException bothUserAndGroupInACLFound(String user, String group);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException shareACLAlreadyExists(String opType, String acl);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException nfsACLAlreadyExists(String opType, String acl);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException multipleACLsWithUserOrGroupFound(String opType, String userOrGroup);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException multipleDomainsFound(String opType, String domain1, String domain2);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException shareACLNotFoundFound(String opType, String acl);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException nfsACLNotFound(String opType, String acl);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidProjectConflict(URI projectId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidProtocolsForVirtualPool(String type, Set<String> requested,
                                                       String... valid);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidSecurityType(String security);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException keyCertificateVerificationFailed();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException keyCertificateVerificationFailed(final Throwable e);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException failedToLoadCertificateFromString(final String cert,
                                                          final Throwable e);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException failedToStoreCertificateInKeyStore(final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException failedToLoadCertificateFromString(final String... certs);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException apiEndpointNotMatchCertificate(final String apiEndpoint);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException failedToLoadKeyFromString();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException failedToLoadKeyFromString(final Throwable e);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException trustStoreUpdatePartialSuccess(
            final List<String> failedParse, final List<String> expired, final List<String> notInTrustStore);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException invalidSeverityInURI(final String severity, final String severities);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidSystemType(final String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_TIME_FORMAT)
    BadRequestException invalidTimeBucket(final String time);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_TIME_FORMAT)
    BadRequestException invalidTimeBucket(final String time, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException invalidURI(final String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException invalidURI(final String value, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException invalidURI(final URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidVirtualPoolSpecifiedVMAXThin();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidVolumeForProtectionSet();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidVolumeParamsAllOrNoneShouldSpecifyLun(
            List<VolumeParam> volumes);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidVolumeSize(long size, long maximum);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidWwnForFcInitiatorNode();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidNodeForiScsiPort();

    @DeclareServiceCode(ServiceCode.API_INVALID_PROTECTION_VPOOLS)
    BadRequestException invalidProtectionVirtualPools();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidWwnForFcInitiatorPort();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException leastVolumeSize(String sizeInGB);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException licenseIsNotValid(final String cause);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException licenseTextIsEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException mandatorySystemTypeRaidLevels();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException mirrorDoesNotBelongToVolume(final URI mid, final URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException missingParameterSystemTypeforAutoTiering();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException networkCanOnlyBeAssociatedWithASingleVirtualArray(URI id);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    BadRequestException networkSystemExistsAtIPAddress(final String ipAddress);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    BadRequestException networkSystemSMISProviderExistsAtIPAddress(final String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException newSizeShouldBeLargerThanOldSize(String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException ipInterfaceNotCreatedManuallyAndCannotBeDeleted();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException noIntiatorsConnectedToVolumes();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noMatchingRecoverPointProtectionPools(final URI varrayId,
                                                              final URI vPoolId, final Set<String> varrayIds);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noMatchingRecoverPointStoragePoolsForVpoolAndVarrays(
            final URI vpoolId, final Set<String> varrayId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noMatchingStoragePoolsForVpoolAndVarray(final URI vpoolId,
                                                                final URI varrayId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noMatchingStoragePoolsForVpoolAndVarrayForClones(final URI vpoolId,
                                                                         final URI varrayId, final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noStoragePoolsForVpoolInVarray(final URI varrayId, final URI vpoolId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noMatchingStoragePoolsForContinuousCopiesVpoolForVplex(final String varrayLabel, final String vpoolLabel,
                                                                               final String storageSystem);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException noRoleSpecifiedInAssignmentEntry();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noStorageFoundForVolume();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noStorageFoundForVolumeMigration(final URI vPoolId,
                                                         final URI vArrayId, final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notAnInstanceOf(final String clazzName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_NOT_FOUND)
    BadRequestException noTenantDefinedForUser(final String username);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noVolumesToSnap();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException noVolumesSpecifiedInRequest();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException noVolumesToBeAddedRemovedFromCG();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noVPlexSystemsAssociatedWithStorageSystem(final URI systemId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException numberOfInstalledExceedsMax();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException onlyNameAndMaxResourceCanBeUpdatedForSystemWithType(
            final String systemType);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_INGESTED_VOLUME_OPERATION)
    BadRequestException operationNotPermittedOnIngestedVolume(final String operation,
                                                              final String volumeName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterForSearchCouldNotBeCombinedWithAnyOtherParameter(
            String name, String first, String second);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException parameterForSearchCouldOnlyBeCombinedWithOtherParameter(
            String resourceType, String first, String second);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException parameterForSearchHasInvalidSearchValueWithSuggestions(
            final String resourceTypename, final String parameter, final String value,
            Object[] validValues);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException parameterIsNotOneOfAllowedValues(final String parameter,
                                                         final String allowedValues);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException parameterIsNotValid(final String parameter);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException parameterIsNotValid(final String parameter, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException parameterIsNotValidURI(final String value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException parameterIsNotValidURI(final URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException parameterIsNotValidURI(final URI value, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException parameterIsNullOrEmpty(final String parameter);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterIsOnlyApplicableTo(final String parameterName,
                                                    final String applicableTo);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterValueIsNotValid(final String parameterName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterValueCannotBeUpdated(final String parameterName,
                                                      final String reason);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException failedToChangeThePassword();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException failedToValidateThePassword();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    BadRequestException parameterMustBeGreaterThan(final String parameter,
                                                   final Number greaterThan);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterNotSupportedFor(final String parameter,
                                                 final String unsupportedFor);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_RANGE)
    BadRequestException parameterNotWithinRange(final String parameter, Number value,
                                                final Number min, final Number max, String units);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterOnlySupportedForVmaxAndVnxBlock(final String propertyName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterOnlySupportedForVmax(final String propertyName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException vArrayUnSupportedForGivenVPool(final URI vPool, final URI vArray);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterTooShortOrEmpty(final String parameterName,
                                                 final Number minNumberOfCharacters);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parentTenantIsNotRoot();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException poolNotBelongingToSystem(final URI port, final URI system);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException computeElementNotBelongingToSystem(final URI ce, final URI system);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException computeElementNotFound(final URI ce);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException portNotBelongingToSystem(final URI port, final URI system);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException propertyIsNotValid(final String property);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException propertyIsNullOrEmpty();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException propertyValueDoesNotMatchAllowedValues(final String property,
                                                               final String allowedValues);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException propertyValueLengthExceedMaxLength(final String property,
                                                           final int maxLen);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException propertyValueLengthLessThanMinLength(final String property,
                                                             final int minLen);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException propertyValueTypeIsInvalid(final String property, final String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException upgradeCheckFrequencyNotPositive();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectedVolumesNotSupported();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException protectionForRpClusters();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException protectionFullCopyAlreadyActive(URI fullCopyId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionNoCopyCorrespondingToVirtualArray(URI varray);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidCopyMode(String copyMode);
    
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidReplicationRPOType(String rpoType);
    
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidVirtualPoolFromVirtualArray(URI vpool, URI varray);
    
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidReplicationType(String copyMode);
    
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException noReplicationRemoteCopies(String replicationType);
    
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException noReplicationTypesSpecified();
    
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException moreThanOneRemoteCopiesSpecified();
    
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException remoteCopyDoesNotExists(URI varray, URI vpool);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException duplicateRemoteSettingsDetected(URI varray);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionNotSpecifiedInVirtualPool();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException protectionOnlyFullCopyVolumesCanBeActivated();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException protectionOnlyFullCopyVolumesCanBeDetached();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException protectionSystemMappingError(final String varray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException protectionUnableToGetSynchronizationProgress();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionVirtualPoolArrayMissing();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionVirtualPoolDoesNotSupportExpandingMirrors(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionVirtualPoolDoesNotSupportHighAvailabilityAndRecoverPoint(
            URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionVirtualPoolJournalSizeInvalid(String type,
                                                                String journalSize);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException protectionVolumeNotFullCopy(URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException protectionVolumeNotFullCopyOfVolume(URI fullCopyId,
                                                            URI sourceVolumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException protectionVolumeInvalidTargetOfVolume(URI copyId,
                                                              URI sourceVolumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException providedVirtualPoolNotCorrectType();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException recoverPointProtectionSystemError();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException requestedVolumeIsNotVplexVolume(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException requiredParameterMissingOrEmpty(final String field);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    BadRequestException resourceAlreadyExistsWithProperty(final String clazz, final URI id,
                                                          final String property, final String value);

    @DeclareServiceCode(ServiceCode.API_ALREADY_REGISTERED)
    BadRequestException resourceAlreadyRegistered(final String resourceType,
                                                  final URI resourceId);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DELETE)
    BadRequestException resourceCannotBeDeleted(final String resource);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException resourcedoesNotBelongToClusterTenantOrg(String resource);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException resourcedoesNotBelongToHostTenantOrg(String resource);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException resourceEmptyConfiguration(final String resource);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    BadRequestException resourceExistsWithSameName(final String resourceType);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    BadRequestException resourceHasActiveReferences(final String clazz, final URI resourceId);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    BadRequestException resourceInClusterWithAutoExportDisabled(final String clazz, final URI resourceId);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    BadRequestException resourceHasActiveReferencesWithType(final String clazz,
                                                            final URI resourceId, final String depType);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_CANNOT_BE_DELETE_DUE_TO_UNREACHABLE_VDC)
    BadRequestException resourceCannotBeDeleteDueToUnreachableVdc();

    @DeclareServiceCode(ServiceCode.API_NOT_REGISTERED)
    BadRequestException resourceNotRegistered(final String resourceType, final URI resourceId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException sameVirtualArrayAndHighAvailabilityArray();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException sameVolumesInAddRemoveList();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException searchFilterMustContainEqualTo();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException searchFilterMustContainPercentU();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException snapshotExportPermissionReadOnly();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException snapshotSMBSharePermissionReadOnly();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotNotActivated(String snapshot);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotEstablishGroupRelationForInactiveSnapshot(final String snapshot);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotNullSettingsInstance(String snapshot);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotParentHasActiveMirrors(String parentLabel, int mirrorCount);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotParentForVPlexHasActiveMirrors(String parentLabel, String vplexVolumelabel, String vplexVolumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException storageSystemNotConnectedToCorrectVPlex(URI tgtStorageSystemURI,
                                                                URI vplexSystemURI);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException targetAndSourceStorageCannotBeSame();

    @DeclareServiceCode(ServiceCode.API_BAD_HEADERS)
    BadRequestException theMediaTypeHasNoMarshallerDefined(final String mediaType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException theParametersAreNotValid(final String parameter);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException theParametersAreNotValid(final String parameter,
                                                 final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException theURIIsNotOfType(final URI id, final String name);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException theURIsOfParametersAreNotValid(final String parameter,
                                                       final Set<String> value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException thinVolumePreallocationPercentageOnlyApplicableToThin();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException thinVolumePreallocationPercentageOnlyApplicableToVMAX();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unableToCreateMarshallerForMediaType(final String mediaType);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DELETE)
    BadRequestException unableToDeactivate(URI id);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DELETE)
    BadRequestException unableToDeactivateDueToDependencies(URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unableToDeleteNetworkContainsEndpoints();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException executionWindowAlreadyExists(String name, String tenantId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException unableToEncodeString(final String parameter, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_NOT_FOUND)
    BadRequestException unableToFindEntity(final URI value);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_NOT_FOUND)
    BadRequestException unableToFindTenant(final URI value);

    @DeclareServiceCode(ServiceCode.DBSVC_ENTITY_NOT_FOUND)
    BadRequestException unableToFindSMISProvidersForIds(List<URI> ids);

    @DeclareServiceCode(ServiceCode.DBSVC_ENTITY_NOT_FOUND)
    BadRequestException unableToFindStorageProvidersForIds(List<URI> ids);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException unableToFindSuitablePoolForProtectionVArray(final URI varrayId);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    BadRequestException unableToUpdateDiscoveredNetworkForStoragePort();

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    BadRequestException unableToUpdateStorageSystem(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException unexpectedClass(final String expectedClazz, final String actualClazz);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException unexpectedValueForProperty(final String propertyName,
                                                   final String expectedPropertyValue, final String actualPropertyValue);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException uniqueLunsOrNoLunValue();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException unknownParameter(final String operationName,
                                         final String parameterName);

    @DeclareServiceCode(ServiceCode.API_UNKNOWN_RP_CONFIGURATION)
    BadRequestException unknownRPConfiguration();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException unsupportedSystemType(final String systemType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException updateVirtualPoolOnlyAllowedToChange();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException updatingFileSystemExportNotAllowed(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException updatingSnapshotExportNotAllowed(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException userMappingDuplicatedInAnotherTenant(final String userMapping);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException userMappingDuplicatedInAnotherTenantExtended(
            final String userMapping, final String tenantId);

    @DeclareServiceCode(ServiceCode.API_BAD_VERSION)
    BadRequestException versionNotExist(String string);

    @DeclareServiceCode(ServiceCode.API_BAD_VERSION)
    BadRequestException versionIsInstalled(final String version);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException versionIsNotAvailableForUpgrade(final String version);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException versionIsNotRemovable(final String version);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID_URI)
    BadRequestException versionIsNotUpgradable(final String target, final String current);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException virtualPoolDoesNotSupportExpandable();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException virtualPoolDoesNotSupportHighAvailabilityAndRecoverPoint();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException orderServiceNotFound(final String serviceId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException orderServiceDescriptorNotFound(final String serviceId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException virtualPoolNotForFileBlockStorage(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException virtualPoolDoesNotSupportSRDFAsyncVolumes(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException virtualPoolSupportsVmaxVnxblockWithRaid();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldRequired(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldBelowMin(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldAboveMax(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldBelowMinStorgeSize(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldAboveMaxStorageSize(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldBelowMinLength(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldBeyondMaxLength(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldNonNumeric(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldNonText(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldNonBoolean(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException serviceFieldNonInteger(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException executionWindowLengthBelowMin(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException executionWindowLengthAboveMax(final String field);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException baseServiceNotFound(final String baseServiceId);

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    BadRequestException vplexPlacementError(final URI uri);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    BadRequestException vPoolChangeNotValid(URI srcS, URI tgt);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    BadRequestException vPoolUpdateNotAllowed(final String associate);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException wrongHighAvailabilityVArrayInVPool(final String varray);

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    BadRequestException noClientProvided();

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    BadRequestException noEndPointsForNetwork();

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    BadRequestException noIpNetworksFound();

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    BadRequestException noIpNetworksFoundForStorageSystem();

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    BadRequestException multipleIpNetworksFound();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noStorageForPrimaryVolumesForVplexVolumeCopies();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noStorageForHaVolumesForVplexVolumeCopies();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noStoragePortForNetwork(final String network);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException hostsNetworksNotRegistered(final String network);

    @DeclareServiceCode(ServiceCode.API_INVALID_VOLUME_TYPE)
    BadRequestException notSupportedSnapshotVolumeType(final String volumeType);

    @DeclareServiceCode(ServiceCode.API_BAD_ATTACHMENT)
    BadRequestException attachmentLogsSizeError(final long currentSize, final long logsZie,
                                                final long maxSize, final String queryParams);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException connectEMCNotConfigured();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException eventsNotAllowedOnNonControlNode();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException isilonSnapshotRestoreNotSupported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException hostProjectChangeNotAllowed(String name);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException clusterProjectChangeNotAllowed(String name);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException maxNativeSnapshotsIsZero(String virtualPool);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteVolumeBlockSnapShotExists(String dependencies);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotStopSRDFBlockSnapShotExists(String volumeLabel);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException principalSearchFailed(final String userName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException searchForPrincipalsFailedForThisTenant(
            final String commaSeperatedPrincipals);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException inventoryDeleteNotSupportedonExportedVolumes(final String nativeGuid);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException notSupportedForInternalVolumes();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException inventoryDeleteNotSupportedOnSnapshots(final String nativeGuid);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException consistencyGroupAddVolumeThatIsInDifferentProject(final String name,
                                                                          final String expected, final String actual);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotReleaseFileSystemExportExists(String dependencies);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotReleaseFileSystemSharesExists(String dependencies);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotReleaseFileSystemSnapshotExists(Integer snapCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotReleaseFileSystemWithTasksPending();

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_PERMISSIONS)
    BadRequestException cannotReleaseFileSystemRootTenantLacksVPoolACL(String virtualPool);

    @DeclareServiceCode(ServiceCode.API_INSUFFICIENT_PERMISSIONS)
    BadRequestException unauthorizedAccessToNonPublicResource();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException missingPersonalityAttribute(String volumeId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noMatchingSRDFPools(final URI varrayId, final URI vPoolId,
                                            final Set<String> varrayIds);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException unableToFindSuitablePoolForTargetVArray(final URI varrayId);

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException srdfNoSolutionsFoundError();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteSRDFVolumes(final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotCreateSRDFVolumes(final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException srdfVolumeMissingPersonalityAttribute(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterRPNotSupportedWithSRDF();

    // inactiveRemoteVArrayDetected
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException inactiveRemoteVArrayDetected(final URI vArray);
    
    // inactiveRemoteVPoolDetected
    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException inactiveRemoteVPoolDetected(final URI vPool);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException parameterVPLEXNotSupportedWithSRDF();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException minPathsGreaterThanMaxPaths();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException pathsPerInitiatorGreaterThanMaxPaths();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException maxPathsRequired();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException parameterMaxResourcesMissing();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException sameVirtualArrayInAddRemoveList();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException virtualArrayHasPortFromOtherVPLEXCluster(final String portId,
                                                                 final String varrayId);

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    BadRequestException noStoragePortFoundForVArray(final String varrayId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidParameterForVarrayNetwork(final String varrayId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException consistencyGroupBelongsToTarget(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException consistencyGroupbelongstoSRDF(URI id);

    @DeclareServiceCode(ServiceCode.API_RESOURCE_BEING_REFERENCED)
    BadRequestException deleteOnlyAllowedOnEmptyCGs(final String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException notAllowedOnSRDFConsistencyGroups();

    @DeclareServiceCode(ServiceCode.SYS_IMAGE_DOWNLOAD_FAILED)
    BadRequestException downloadFailed(final String version, final String url);

    @DeclareServiceCode(ServiceCode.SYS_IMAGE_DOWNLOAD_FAILED)
    BadRequestException invalidImageUrl(final String version, final String url);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotChangePortVarraysExportExists(final String portId,
                                                            final String varrayId, final String exportId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotChangePoolVarraysVolumeExists(final String poolId,
                                                            final String varrayId, final String volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException failoverCopiesParamCanOnlyBeOne();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException swapCopiesParamCanOnlyBeOne();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException failOverCancelCopiesParamCanOnlyBeOne();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidCopyType(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidSRDFCopyMode(String copyMode);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotChangeSRDFCopyMode(String volumeNativeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidCopyModeOp(String newCopyMode, String vpoolCopyMode);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidCopyIDCombination(String type);

    @DeclareServiceCode(ServiceCode.API_INVALID_ACTION_FOR_VPLEX_MIRRORS)
    BadRequestException actionNotApplicableForVplexVolumeMirrors(final String actionName);

    @DeclareServiceCode(ServiceCode.API_PLACEMENT_ERROR)
    BadRequestException unableToFindSuitableStorageSystemsforSRDF(final String grpname1);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException unsupportedConfigurationWithMultipleAsyncModes();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException groupNameCannotExceedEightCharactersoronlyAlphaNumericAllowed();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotRemovePoolWithResources(final Set<String> poolIds);

    @DeclareServiceCode(ServiceCode.OBJ_PROJECT_INVALID)
    BadRequestException invalidObjProject(URI projectId);

    @DeclareServiceCode(ServiceCode.OBJ_PROJECT_NOT_FOUND_FOR_NAMESPACE)
    BadRequestException objProjectNotFoundForNamespace(String namespace);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_NOT_FOUND_FOR_NAMESPACE)
    BadRequestException objVpoolNotFoundForNamespace(String namespace);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_INVALID)
    BadRequestException invalidObjVpool(URI vpoolId);

    @DeclareServiceCode(ServiceCode.DATASERVICE_INVALID_VARRAY)
    BadRequestException invalidDataServiceVarray(URI vArrayId);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_NOT_COMPATIBLE)
    BadRequestException objVpoolNotCompatible(URI vpoolId);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_EMPTY)
    BadRequestException objVpoolEmpty(URI vpoolId);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_LISTS_NOT_MUTUALLY_EXCLUSIVE)
    BadRequestException objVpoolListsNotMutuallyExclusive(List<String> allowed, List<String> disallowed);

    @DeclareServiceCode(ServiceCode.OBJ_BUCKET_EXISTS)
    BadRequestException objBucketExists(URI bucketId);

    @DeclareServiceCode(ServiceCode.OBJ_BUCKETNAME_INVALID)
    BadRequestException invalidObjBucketName(String bucketName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException objBucketIsNotEmpty(String bucketName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException objBucketIsHidden(String bucketName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException virtualToNonVirtualProtectionNotSupported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException nonVirtualToVirtualProtectionNotSupported();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeExpandNotSupportForRPVPlex();

    @DeclareServiceCode(ServiceCode.OBJ_BUCKETNAME_INVALID)
    BadRequestException objBucketNameEmpty();

    @DeclareServiceCode(ServiceCode.OBJ_NOT_BUCKT_OWNER)
    BadRequestException invalidBucketOwner(String ownerName, String bucketName);

    @DeclareServiceCode(ServiceCode.OBJ_VPOOL_TYPE_INVALID)
    BadRequestException objInvalidVpoolType(String vpoolType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotReduceVpoolMaxPaths(final String exportGroupId, final String exportMask);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException noTokenProvided();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotChangeVpoolPathsPerInitiator(final String exportGroupId, final String exportMask);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotRemoveVArrayWithPools(final Set<String> varrayIds);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotRemoveVArrayWithVPoolResources(final Set<String> varrayIds);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException snapshotNotAllowedOnSRDFAsyncVolume(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_INVALID_VARRAY_NETWORK_CONFIGURATION)
    BadRequestException invalidVarrayNetworkConfiguration(final String varrayLabel, final String vplexLabel);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterConsistencyGroupVolumeHasIncorrectVArray(URI cg, URI expected);

    @DeclareServiceCode(ServiceCode.API_NO_DOWNLOAD_IN_PROGRESS)
    BadRequestException noDownloadInProgress();

    @DeclareServiceCode(ServiceCode.API_RESOURCE_EXISTS)
    BadRequestException computeSystemExistsAtIPAddress(final String ipAddress);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterCannotDeactivateRegisteredComputeSystem(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterOsInstallNetworkDoesNotExist(final String network);

    @DeclareServiceCode(ServiceCode.API_INVALID_VPOOL_FOR_INGESTION)
    BadRequestException virtualPoolIsForVplex(final String vpoolLabel, final String highAvailabilityParam);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException multiVolumeConsistencyMustBeEnabledWithRP();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException consistencyGroupMissingForRpProtection();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException consistencyGroupMustBeEmptyOrContainRpVolumes(final URI cgUri);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException consistencyGroupIsNotCompatibleWithRequest(final URI cgUri, final String compatibleTypes,
                                                                   final String requestedTypes);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidVpoolUsedForRpConsistencyGroup(final URI cgUri, final URI correctVpoolUri);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException vPoolSourceVarraysNotCompatibleForCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException vPoolTargetVarraysNotCompatibleForCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotAddVolumesToSwappedCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotMixMetroPointAndNonMetroPointVolumes(final String cgName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException srdfVolumeVPoolChangeNotSupported(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException srdfVolumeVPoolChangeToNonSRDFVPoolNotSupported(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException srdfInternalError(Throwable cause);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException expandSupportedOnlyOnSource(URI id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException vmaxAllowedOnlyAsSrdfTargets();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteSRDFTargetWithActiveSource(URI target, URI source);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotIsNotForConsistencyGroup(final String snapshotName, final String cgName);

    @DeclareServiceCode(ServiceCode.API_INVALID_MAX_CONTINUOUS_COPIES)
    BadRequestException invalidMaxContinuousCopiesForVplex(final Integer maxCopies);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cantDeleteVPlexHaVPool(final String vpoolName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_REQUIRED)
    BadRequestException invalidMirrorVpoolForVplexDistributedVpool();

    @DeclareServiceCode(ServiceCode.API_INVALID_HIGH_AVAILABILITY_FOR_MIRROR_VPOOL)
    BadRequestException invalidHighAvailabilityForMirrorVpool(final String mirrorVpoolName, final String mirrorVpoolHA,
                                                              final String vpoolHA, final String correctMirrorVpoolHA);

    @DeclareServiceCode(ServiceCode.API_INVALID_VARARY_CONTINUOUS_COPIES_VPOOL)
    BadRequestException noVarrayForMirrorVpoolWithExpectedVplex(final String vPoolName, final String vplexSystemName,
                                                                final String vplexCluster);

    @DeclareServiceCode(ServiceCode.API_INVALID_VARARY_CONTINUOUS_COPIES_VPOOL)
    BadRequestException noMirrorVpoolForVplexVolume(final String volumeName);

    @DeclareServiceCode(ServiceCode.API_VPOOL_IN_USE_AS_CONTINUOUS_COPIES_VPOOL)
    BadRequestException virtualPoolIsSetAsContinuousCopiesVpool(final String vPoolName, final String vPoolNames);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantChangeConnectionStatusOfLocalVDC();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException badVdcId(final String vdcId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterTenantNamespaceIsEmpty();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException tenantNamespaceMappingConflict(final String tenantId, final String namespace);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeNotInVirtualPool(final String volumeName, final String vpoolName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotUpdateProviderIP(String providerKey);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotFindSolutionForRP(String string);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidMetroPointConfiguration();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterProtectionTypeIsEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException objUserExists(final String user);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException objNamespaceExists(final String namespace);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException objNamespaceNameEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException objNamespaceNotEmpty();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException objNamespaceNotBound(final String namespace);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException objNamespaceConcurrentlyModified();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException objNamespaceTenantAlreadyBound();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unableToFindNamespaceForTenant(final String userName, final String tenantId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException objNamespaceZonesMisconfiguration();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeNotExpandable(final String volumeName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unsupportedUnManagedVolumeDiscovery(final String systemSerialNumber);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotCreateProtectionForConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionVirtualPoolRemoteCopyModeInvalid(final String remoteCopyMode);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionVirtualPoolRPOTypeInvalid(final String rpoType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionVirtualPoolRPOValueInvalid(final String rpoValue);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionVirtualPoolRPOTypeNotSpecified(final Long rpoValue);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException protectionVirtualPoolRPOValueNotSpecified(final String rpoType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notValidRPSourceVolume(String volname);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException newCertificateMustBeSpecified();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyNotSupportedFromSnapshot(final String systemType, final URI snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyNotSupportedOnArray(final URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyNotSupportedForConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notEnoughComputeElementsInPool();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidUpdatingSPT(final String templateName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException incompatibleSPT(final String templateName, final String varrayName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noComputeSystemsFoundForVarray();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException mustHaveAtLeastOneChange(final String changeClass);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException objBaseUrlConflicts(final String enteredBaseUrl, final String existingBaseUrl);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noRepGroup();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noCosForRepGroup(final String repGroup, final String tenantId, final String zone);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException accessDenied();

    @DeclareServiceCode(ServiceCode.API_NO_PLACEMENT_FOUND)
    BadRequestException noRPConnectedVPlexStorageSystemsForTarget(final String vpool,
                                                                  final String varray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeactivateStorageSystemActiveRpVolumes();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotExportNotSupported(final String systemType, final URI snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException objNoNamespaceForTenant(URI tenantId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noSecretKeyForUser(URI user);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumesShouldBelongToSameVpool();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException requestedVolumeCountExceedsLimitsForCG(final int count,
                                                               final int maxCount, final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteAuthProviderWithTenants(final int numTenants, final List<URI> tenantIDs);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteAuthProviderWithVdcRoles(final int num, final List<String> users);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteAuthProviderWithTenantRoles(final String tenantName, final int num, final List<String> users);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotExportInitiatorWithNoCompute(final String exportGroup, final String port);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unableToSetUserScopeConfig(final String scope);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException updatingCompletedApproval();
    
    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException updateApprovalBySameUser(final String name);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidVarrayForVplex(final String vplex, final String varray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantChangeClusterForLocalVolumeInCG();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidInterval(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordIntervalNotInRange(int min, int max);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidLength(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidLowercaseNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidUppercaseNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidNumericNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidSpecialNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidRepeating(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidChangeNumber(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidDictionary();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidHistory(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordExpired(int number);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidOldPassword();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException passwordInvalidExpireDays(int min, int max);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotCreatePortForSystem(final String systemType);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException metroPointConfigurationNotSupported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException metroPointTargetVarrayConfigurationNotSupported();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException sptIsNotValidForVarrays(final String spt);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotRemoveVarraysFromCVP(final String computeVirtualPool);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidFloatParameterBelowMinimum(String string, float value, int minimum,
                                                          String unit);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidFloatParameterAboveMaximum(String string, float value, int maximum,
                                                          String unit);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotRemoveVCP(String vcpId);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DEREGISTER)
    BadRequestException unableToDeregisterProvisionedComputeSystem(final String resource, final String hosts);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DELETE)
    BadRequestException unableToDeactivateProvisionedComputeSystem(final String resource, final String hosts);

    @DeclareServiceCode(ServiceCode.API_CANNOT_DEREGISTER)
    BadRequestException unableToDeregisterProvisionedComputeElement(final String resource, final String host);

    @DeclareServiceCode(ServiceCode.API_INTERNAL_SERVER_ERROR)
    BadRequestException unableToLockBladeReservation();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException vcenterOperationFailed();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notAllowedWhenCGHasSnapshots();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notAllowedWhenCGHasMirrors();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notAllowedInvalidBackendSystem();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notAllowedOnRPConsistencyGroups();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidParameterVolumeAlreadyInAConsistencyGroup(
            final URI cgUri, final URI currentCgUri);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidParameterSourceVolumeNotInGivenConsistencyGroup(
            final URI sourceVolumeUri, final URI cgUri);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumesWithMultipleReplicasCannotBeAddedToConsistencyGroup(
            final String volumeName, final String replicaType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumesWithReplicaCannotBeAdded(
            final String volumeName, final String replicaType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeWithDifferentNumberOfReplicasCannotBeAdded(
            final String volumeName, final String replicaType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantChangeVarrayForVplexVolumeInAppConsistenctCG();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantChangeVpoolForVplexVolumeInAppConsistenctCG();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_MISSING)
    BadRequestException missingParameterSystemTypeforHostIOLimits();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterSystemTypeforHostIOLimits();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterValueforHostIOLimitIOPs();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidParameterValueforHostIOLimitBandwidth();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException exportNotFound(String operatioName, String exportDetails);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException fileSystemNotExported(String operatioName, String id);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException exportExists(String operatioName, String exportDetails);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException missingInputTypeFound(String type, String opName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException missingUserOrGroupInACE(String opName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidAnon(String anon);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException unableToProcessRequest(String msg);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidFileExportXML(String msg);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException sameSecurityFlavorInMultipleExportsFound(String msg, String opName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException haVpoolForVpoolUpdateHasInvalidHAVpool(final String haVpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException haVpoolForVpoolUpdateIsInactive(final String haVpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException haVpoolForVpoolUpdateDoesNotExist(final String haVpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException haVpoolForNewHAVpoolForVpoolUpdateDoesNotExist(final String haVpoolId, final String newHaVpoolId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantChangeVpoolNotAllCGVolumes();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantChangeVpoolVolumeIsNotInCG();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException targetVPoolDoesNotSpecifyUniqueSystem();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException targetHAVPoolDoesNotSpecifyUniqueSystem();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidConfigType(final String configType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidScopeFomart(final String scope);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException systemDefaultConfigCouldNotBeModifiedOrDeactivated(final URI configId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException sourceNotExported(final URI sourceId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidParameterRemovePreexistingInitiator(final String maskName, final String initiatorPort);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException failedToFindVDC(String name);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeactivateStoragePool();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeactivateStoragePort();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notAllowedAddVolumeToCGWithIngestedVolumes();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException mixedVolumesinCGForVarrayChange();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantChangeVarrayNotAllCGVolumes();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantChangeVarrayVolumeIsNotInCG();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException nonSRDFVolumeCannotbeAddedToSRDFCG();

    @DeclareServiceCode(ServiceCode.API_INVALID_OBJECT)
    BadRequestException invalidVplexMirror(String mirrorName, String mirrorId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDetachStorageForHost(String reason);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotUpdateHost(String reason);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException clusterContainsNoCompatibleHostsForVcenter();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeForVarrayChangeHasSnaps(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeForVpoolChangeHasSnaps(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeForVpoolChangeHasMirrors(final String volumeId, final String volumeLabel);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeForVarrayChangeHasMirrors(final String volumeId, final String volumeLabel);

    @DeclareServiceCode(ServiceCode.API_TASK_EXECUTION_IN_PROGRESS)
    BadRequestException cannotExecuteOperationWhilePendingTask(final String pendingVolumes);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException rpClusterVarrayNoClusterId(String label);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException rpClusterVarrayInvalidClusterId(String label);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException rpClusterVarrayInvalidVarray(String label, String clusterId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cgContainsTooManyVolumesForVPoolChange(final String cgId, final int cgVolumes, final int maxCgVolumes);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cgContainsTooManyVolumesForVArrayChange(final String cgId, final int cgVolumes, final int maxCgVolumes);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException vpoolNotAssignedToVarrayForVarrayChange(final String vpoolId, final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException storageSystemsNotConnectedForAddVolumes(final String listOfArrays);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidVplexCgName(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException subDirNotFound(final String msg);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException userMappingAttributeIsEmpty();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException userMappingNotAllowed(final String user);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteOrEditUserGroup(final int numResources, final Set<URI> resourceIDs);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotRenameUserGroup(final String name);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException authnProviderGroupObjectClassesAndMemberAttributesIsEmpty(
            final String id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException authnProviderGroupObjectClassesAndMemberAttributesRequired(
            final String param);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidStructureForIngestedVolume(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_DUPLICATE_EXPORT_GROUP_NAME_SAME_PROJECT_AND_VARRAY)
    BadRequestException duplicateExportGroupProjectAndVarray(final String egName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException incompatibleGeoVersions(final String version, final String feature);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException operationNotSupportedForSystemType(final String operation, final String storageSystemType);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException userGroupExistsAlready(final String name);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteAuthnProviderWithUserGroup(final int numResources, final Set<URI> resourceIDs);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidFullCopySource(final String copySourceId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantCreateFullCopyForVPlexSnapshot();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidFullCopyCountForVolumesInConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidMirrorCountForVolumesInConsistencyGroup();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyRestoreNotSupportedForSnapshot();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyResyncNotSupportedForSnapshot();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyOperationNotAllowedOnEmptyCG(final String cgId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyOperationNotAllowedNotAFullCopy(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyOperationNotAllowedSourceNotInCG(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantDeleteFullCopyNotDetached(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantStopSRDFFullCopyNotDetached(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantCreateNewVolumesInCGActiveFullCopies(final String cgId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantUpdateCGActiveFullCopies(final String cgId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException detachedFullCopyCannotBeActivated(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException detachedFullCopyCannotBeRestored(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException inactiveFullCopyCannotBeRestored(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyCannotBeRestored(final String fullCopyId, final String state);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException detachedFullCopyCannotBeResynchronized(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException inactiveFullCopyCannotBeResynchronized(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyCannotBeResynchronized(final String fullCopyId, final String state);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotEstablishGroupRelationForDetachedFullCopy(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotEstablishGroupRelationForInactiveFullCopy(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotCheckProgressFullCopyDetached(final String fullCopyId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeForVarrayChangeHasFullCopies(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeForVpoolChangeHasFullCopies(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeForRPVpoolChangeHasFullCopies(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeForSRDFVpoolChangeHasFullCopies(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noFullCopiesForVMAX3VolumeWithActiveSnapshot(final String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noSnapshotsForVMAX3VolumeWithActiveFullCopy();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notSupportedSnapshotWithMixedArrays(URI cgUri);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyExpansionNotAllowed(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException overlappingAttributesNotAllowed(final String userGroupName,
                                                        final Set<String> overlappingUserGroups);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantCreateFullCopyOfVNXFullCopy();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantCreateFullCopyOfVPlexFullCopyUsingVNX();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException maxFullCopySessionLimitExceeded(final URI volume, final int maxStillAllowed);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyNotSupportedByBackendSystem(final URI volume);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noAuthnProviderFound(String userId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidPrincipals(String details);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException storagePoolsRequireVplexForProtection(final String personality, final String vpoolName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException migrationCantBePaused(String migrationName, String status);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException migrationCantBeResumed(String migrationName, String status);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException migrationCantBeCancelled(String migrationName, String status);
    
    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cancelMigrationFailed(String migrationName, String reason);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException migrationHasntStarted(String migrationId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException siteIdNotFound();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException operationOnlyAllowedOnSyncedSite(final String siteId, final String siteState);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException operationOnlyAllowedOnPausedSite(final String siteId, final String siteState);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException operationNotAllowedOnActiveSite();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidQuotaRequestForObjectStorage(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException virtualPoolNotForObjectStorage(String type);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException insufficientRetentionForVirtualPool(final String label, final String type);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException consistencyGroupContainsNoVolumes(final URI id);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException targetVirtualArrayDoesNotMatch(final URI consistencyGroup, final URI virtualArray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noValidSrdfTargetVolume(final URI volumeId, final URI virtualArray);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException volumeMustBeSRDFProtected(final URI volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException srdfCgContainsNoSourceVolumes(final URI consistencyGroupId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException consistencyGroupMustBeSRDFProtected(final URI consistencyGroupId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException consistencyGroupMustBeRPProtected(final URI consistencyGroupId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotRemoveTenant(final String resource, final String name, final Set<String> tenants);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unsupportedNumberOfPrivileges(final URI tenantId, final List<String> privileges);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unsupportedPrivilege(final URI tenantId, final String privilege);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDetachResourceFromTenant(final String resource);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException resourceCannotBelongToProject(final String resource);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException tenantDoesNotShareTheVcenter(final String tenantName, final String vCenterName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException rpBlockApiImplPrepareVolumeException(final String volume);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidEntryForProjectVNAS();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException failedToAssignVNasToProject(final String assignVnasError);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException failedToDeleteVNasAssignedProject();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noVNasServersAssociatedToProject(final String project);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException vNasServersNotAssociatedToProject();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException oneOrMorevNasServersNotAssociatedToProject();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteOrUpdateImageServerWhileInUse();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noExistingVolumesInCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noSourceVolumesInCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException noProtectionSystemAssociatedWithTheCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unableToFindSuitableJournalRecommendation();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unableToFindJournalRecommendation(final String rpSiteName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException unableToFindTheSpecifiedCopy(final String copy);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotNotAllowedWhenCGAcrossMultipleSystems();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyNotAllowedWhenCGAcrossMultipleSystems();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantAddMixVolumesToIngestedCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notAllVolumesAddedToIngestedCG(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyNotAllowedForIngestedCG(final String uri);

    @DeclareServiceCode(ServiceCode.API_DELETION_IN_PROGRESS)
    BadRequestException deletionInProgress(final String dataObjectType, final String dataObjectName);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException imageNotPresentOnComputeImageServer(final String computeImage, final String computeImageServer);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException noImageServerAssociatedToComputeSystem(final String computeSystem);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException clientIpNotExist();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotShareVcenterWithMultipleTenants(final String vcenterName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotRemoveDatacenterTenant(final String dataCenterName, final String vcenterName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotEditVcenterOrUpdateACL(final String vcenterName, final long refreshInterval);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cgReferencesInvalidProtectionSystem(final URI cgUri, final URI protectionSystemUri);
	
	@DeclareServiceCode(ServiceCode.API_AUTH_KEYSTONE_PROVIDER_CREATE_NOT_ALLOWED)
    BadRequestException keystoneProviderAlreadyPresent();
    
	@DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException managerDNMustcontainUserNameAndTenantName();
    
	@DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException managerDNMustcontainEqualTo();
    
	@DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException managerDNInvalid();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException projectWithTagNonexistent(final String openstackTenantId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException notEnoughPortsForMaxpath(final URI storageURI, final Integer portCount, final Integer maxPaths);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException pathParameterPortsDoNotIncludeArray(final URI arrayURI);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotOverrideVpoolPathsBecauseExistingExports(final String message);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotAddSRDFProtectionToPartialCG(String msg);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cannotDeleteImageServer();

    @DeclareServiceCode(ServiceCode.API_PRECONDITION_FAILED)
    BadRequestException cannotAddImageWithoutImageServer();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantUpdateCGWithMixedBlockObjects(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantUpdateCGWithReplicaFromMultipleSystems(final String cgName);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantExposeNonVPLEXSnapshot(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantExposeInactiveSnapshot(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantExposeUnsynchronizedSnapshot(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantExposeSnapshotAlreadyExposed(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantDeleteSnapshotExposedByVolume(final String snapshotId, final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException fullCopyNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException mirrorNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException expansionNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cgNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException varrayChangeNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException vpoolChangeNotAllowedVolumeIsExposedSnapshot(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException cannotUpdateTFTPBOOTDirectory();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException rpBlockApiImplRemoveProtectionException(final String message);
    
    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException dbConsistencyCheckAlreadyProgress();
    
    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException canNotCanceldbConsistencyCheck();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException invalidSnapshotSessionSource(final String sourceId);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidCopyModeForLinkedTarget(String copyMode);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidNewLinkedTargetsCount(int requestedCount, String sourceId, int validCount);

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException createSnapSessionNotSupportForSnapshotSource();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException createSnapSessionNotSupportedForRPProtected();

    @DeclareServiceCode(ServiceCode.API_PARAMETER_INVALID)
    BadRequestException invalidZeroLinkedTargetsRequested();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotSessionDoesNotHaveAnyTargets(final String snapSessionId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException targetIsNotLinkedToSnapshotSession(final String targetId, final String snapSessionIdd);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException snapshotSessionSourceHasActiveMirrors(final String sourceId, final int mirrorCount);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException relinkTargetNotLinkedToActiveSnapshotSession(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException relinkSnapshotSessionsNotOfSameSource();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException relinkTgtSnapshotSessionHasDifferentSource(final String snapSessionSourceId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException canDeactivateSnapshotSessionWithLinkedTargets();

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException cantUnlinkExportedSnapshotSessionTarget(final String snapshotId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException maximumNumberVpoolSnapshotsReached(final String sourceId);

    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    BadRequestException maximumNumberSnapshotsForSourceReached(final String sourceId);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    BadRequestException cannotImportVolumeWithSnapshotSessions(final String volumeId);

    @DeclareServiceCode(ServiceCode.API_UNSUPPORTED_CHANGE)
    BadRequestException volumeForRPVpoolChangeHasSnapshotSessions(final String volumeId);
}
