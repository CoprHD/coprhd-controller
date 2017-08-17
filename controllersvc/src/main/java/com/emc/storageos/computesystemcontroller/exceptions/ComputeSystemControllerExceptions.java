/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface ComputeSystemControllerExceptions {

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException discoverFailed(final String id, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException targetNotFound(final String type);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException duplicateSystem(final String type, final String name);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException discoveryAdapterNotFound(final String name, final String id);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException incompatibleHostVersion(final String type, final String version,
            final String minVersion);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException versionNotSupported(final String version, final String minVersion);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ComputeSystemControllerException verifyVersionFailedNull(final String uri);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException incompatibleLinuxHostVersion(final String type, final String version,
            final String minSuSe, final String minRH);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException powerStateChangeFailed(final String state, final String id,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException bindHostToTemplateFailed(final String id,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToSetSanBootTargets(final String id, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToSetBootVolume(final String id, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToRollbackBootVolume(final String id, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException volumeNotFound(final String id);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException invalidBootVolumeExport(final String host, final String volume );

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException cannotDetermineComputeSystemForHost(final String host);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException cannotSetSanBootTargets(final String host, final String reason);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToValidateBootVolumeExport(final String host, final String volume, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unbindHostFromTemplateFailed(final String id,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToProvisionHost(final String hostName, final String computeSystem,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException invalidComputeVirtualPool(final String hostName, final String vcpId,
            final String computeSystem, String detailedMessage, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException noComputeElementAssociatedWithHost(final String hostName,
            final String hostId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException noBootVolumeAssociatedWithHost(final String hostName, final String hostId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToDeactivateBootVolumeAssociatedWithHost(final String hostName,
            final String hostId, final String bootVolumeId, final String causeMessage);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException noCorrespondingNetworkForHBAInVarray(final String hba, final String vArray,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToSetOsInstallNetwork(final String osInstallVlan,
            final String computeElement, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToRemoveOsInstallNetwork(final String osInstallVlan,
            final String computeElement, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToPrepareHostForOSInstall(final String host, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToUpdateHostAfterOSInstall(final String host, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToSetLanBoot(final String computeElement, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToSetNoBoot(final String computeElement, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToSetSanBootTarget(final String computeElement, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToDeactivateHost(final String hostName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException deviceOperationTimeOut(final String computeSystemURL, final long timeOut);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToPutHostInMaintenanceMode(final String host, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToRemoveHostVcenterCluster(final String host, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToRemoveHostFromCluster(final String host, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToReAddHostToCluster(final String host, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException clusterNotFound(final String cluster);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException hostNotFound(final String host);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToCheckClusterVms(final String cluster, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToRemoveVcenterCluster(final String cluster, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException serviceProfileUuidChanged(final String serviceProfileDn, final String oldUUID, final String newUUID);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException serviceProfileMatchedMultipleHosts(final String serviceProfileDn, final String uuid, final String hostNames);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException serviceProfileUuidDuplicate(final String lsServerDn, final String serviceProfileDn, final String uuid);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException newServiceProfileDuplicateUuid(final String serviceProfile, final String uuid, final String host);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException hostMatcherError(final String msg);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException invalidServiceProfileReference(final String serviceProfileId);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException invalidServiceProfile(final String serviceProfileId);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException clusterHasVms(final String cluster);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException noInitiatorPortConnectivity(final String initiator, final String export);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException noHostInitiators(final String host);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException illegalInitiator(final String host, final String initiator, final String initiatorHost);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerTimeoutException timeoutWaitingForMOTerminalState(final String moDn, final String currentState,
            final int timeOutIntervalInMS);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToMount(final String systemType, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToUnmount(final String systemType, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException commandTimedOut(final String systemType);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToAddHostPortsToVArrayNetworks(final String array, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException hostHasVmsOnBootVolume(final String bootVolumeId, final String hostname);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToCheckVMsOnHostBootVolume(final String bootVolumeId, final String hostname, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToUntagVolume(final String bootVolumeId, final String hostname, Exception exception);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unbindHostFromComputeElementFailed(String object, Exception exception);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToBindHostComputeElement(String computeelement, String host, Exception exception);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException hostHasPoweredOnVmsOnBootVolume(final String bootVolumeId, final String hostname);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException verifyHostUCSServiceProfileStateFailed(final String hostname, String expectedState, String currentState);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToVerifyHostUCSServiceProfileState(final String hostname, Exception exception);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException unableToCheckVMsOnHostExclusiveVolumes(final String hostname, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException hostHasVmsOnExclusiveVolumes(final String hostname);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException hostHasPoweredOnVmsOnExclusiveVolumes(final String hostname);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException prerequisiteForBindServiceProfileToBladeFailed(final String hostId, String computeelementId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_COMPUTESYSTEM_ERROR)
    public ComputeSystemControllerException rollbackPrerequisiteForBindServiceProfileToBladeFailed(final String hostId, final Throwable cause);
}
