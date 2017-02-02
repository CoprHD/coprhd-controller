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
    public ComputeSystemControllerException clusterHasVms(final String cluster);

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
}
