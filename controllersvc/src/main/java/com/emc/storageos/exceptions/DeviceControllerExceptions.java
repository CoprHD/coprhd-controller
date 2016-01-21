/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.exceptions;

import java.net.URI;
import java.util.Collection;

import com.emc.storageos.cinder.errorhandling.CinderExceptions;
import com.emc.storageos.hds.HDSExceptions;
import com.emc.storageos.isilon.restapi.IsilonExceptions;
import com.emc.storageos.netapp.NetAppExceptions;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerExceptions;
import com.emc.storageos.plugins.DiscoveryExceptions;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointExceptions;
import com.emc.storageos.scaleio.ScaleIOExceptions;
import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.vnx.xmlapi.VNXExceptions;
import com.emc.storageos.vnxe.VNXeExceptions;
import com.emc.storageos.volumecontroller.impl.smis.SmisExceptions;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiExceptions;

/**
 * This interface holds all the methods and interfaces used to create {@link DeviceControllerException}s
 * <p/>
 * Remember to add the English message associated to the method in DeviceControllerExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface DeviceControllerExceptions {
    /** Holds the methods used to create Isilon related exceptions */
    public static final IsilonExceptions isilon = ExceptionMessagesProxy.create(IsilonExceptions.class);

    /** Holds the methods used to create SMIS related exceptions */
    public static final SmisExceptions smis = ExceptionMessagesProxy.create(SmisExceptions.class);

    /** Holds the methods used to create NetApp related exceptions */
    public static final NetAppExceptions netapp = ExceptionMessagesProxy.create(NetAppExceptions.class);

    /** Holds the methods used to create VPLEX related exceptions */
    public static final VPlexApiExceptions vplex = ExceptionMessagesProxy.create(VPlexApiExceptions.class);

    /** Holds the methods used to create discovery plugin related exceptions */
    public static final DiscoveryExceptions discovery = ExceptionMessagesProxy.create(DiscoveryExceptions.class);

    /** Holds the methods used to create network devices related exceptions */
    public static final NetworkDeviceControllerExceptions network = ExceptionMessagesProxy.create(NetworkDeviceControllerExceptions.class);

    /** Holds the methods used to create recovery point related exceptions */
    public static final RecoverPointExceptions recoverpoint = ExceptionMessagesProxy.create(RecoverPointExceptions.class);

    /** Holds the methods used to create VNX related exceptions */
    public static final VNXExceptions vnx = ExceptionMessagesProxy.create(VNXExceptions.class);

    /** Holds the methods used to create HDS related exceptions */
    public static final HDSExceptions hds = ExceptionMessagesProxy.create(HDSExceptions.class);

    /** Holds the methods used to create Cinder related exceptions */
    public static final CinderExceptions cinder = ExceptionMessagesProxy.create(CinderExceptions.class);

    /** Holds the methods used to create ScaleIO related exceptions */
    public static final ScaleIOExceptions scaleio = ExceptionMessagesProxy.create(ScaleIOExceptions.class);

    /** Holds the methods used to create VNXe related exceptions */
    public static final VNXeExceptions vnxe = ExceptionMessagesProxy.create(VNXeExceptions.class);

    @DeclareServiceCode(ServiceCode.DISPATCHER_UNABLE_FIND_CONTROLLER)
    public DeviceControllerException unableToDispatchToController(final String targetClassName);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException attachVolumeMirrorFailed(String message);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException resumeVolumeMirrorFailed(URI id);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException stopVolumeMirrorFailed(URI id);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException createConsistencyGroupFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException connectStorageFailedDb(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException connectStorageFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException connectStorageFailedNull();

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException connectStorageFailedNoDevice(final String deviceType);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException disconnectStorageFailedDb(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException disconnectStorageFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException disconnectStorageFailedNull();

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException blockDeviceOperationNotSupported();

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException scanFailedToFindSystem(final String provider,
            final String system);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException scanProviderFailed(final String system,
            final String provider);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException addStorageSystemFailed(final String system,
            final String provider);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException getDeviceTypeFailed(final String deviceURI);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException exportGroupCreateFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException exportGroupDeleteFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException exportGroupUpdateFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException exportGroupAddInitiatorsFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException exportGroupRemoveInitiatorsFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException createVolumeSnapshotFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException activateVolumeSnapshotFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException deactivateMirrorFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException deleteConsistencyGroupFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException deleteVolumeSnapshotFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException exportGroupAddVolumesFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException restoreVolumeFromSnapshotFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException activateVolumeFullCopyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException detachVolumeFullCopyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException restoreVolumeFromFullCopyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException resynchronizeFullCopyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException fractureFullCopyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException synchronizationInstanceNull(final String targetLabel);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException srdfConsistencyGroupAlreadyExistsWithVolume(final String cgName);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException srdfConsistencyGroupNotFoundOnProviders();

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException srdfBothSourceAndTargetProvidersNotReachable();

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException failbackVolumeOperationFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException suspendVolumeOperationFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException splitVolumeOperationFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException resumeVolumeOperationFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException snapSettingsInstanceNull(final String snapLabel, final String snapURI);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException exportAddVolumes(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException exportRemoveVolumes(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException exportAddInitiators(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException exportRemoveInitiators(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException exportGroupChangePathParams(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException changeAutoTieringPolicy(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException exportChangePolicyAndLimits(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public VPlexApiException findExportMasksFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public VPlexApiException refreshExportMaskFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.VNXFILE_FILESYSTEM_ERROR)
    public DeviceControllerException unableToCreateFileSystem(final String msg);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToDeleteFileSystem(final Throwable cause);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToExportFileShare(final Throwable cause);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToUnexportFileShare(final Throwable cause);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToExpandFileSystem(final Throwable cause);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToShareFileSystem(final Throwable cause);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToDeleteFileShare(final Throwable cause);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToCreateFileSystemSnapshot(final Throwable cause);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToRestoreFileSystemFromSnapshot(final Throwable cause);
    
    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException createFileSystemOnPhysicalNASDisabled();

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToConnectToStorageDeviceForMonitoringDbException(
            final String storage, final Throwable cause);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToConnectToStorageDeviceForMonitoringDbNullRef(final String storage);

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException unableToConnectToStorageDeviceForMonitoringNoDevice(final String storage,
            final String devType);

    @DeclareServiceCode(ServiceCode.FILE_CONNECTION_ERROR)
    DeviceControllerException unableToDisconnectStorageDeviceMonitoringDbException(
            final String storage, final Throwable cause);

    @DeclareServiceCode(ServiceCode.FILE_CONNECTION_ERROR)
    public DeviceControllerException unableToDisconnectStorageDeviceMonitoringDbNullRef(final String storage);

    @DeclareServiceCode(ServiceCode.FILE_CONNECTION_ERROR)
    public DeviceControllerException unableToDisconnectStorageDeviceMonitoringNoDevice(final String storage,
            final String deviceType);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException operationNotSupported();

    @DeclareServiceCode(ServiceCode.FILE_CONTROLLER_ERROR)
    public DeviceControllerException createSmbShareFailed(final String name, final String description);

    @DeclareServiceCode(ServiceCode.CONTROLLER_INVALID_SYSTEM_TYPE)
    public DeviceControllerException invalidSystemType(String type);

    @DeclareServiceCode(ServiceCode.CONTROLLER_INVALID_URI)
    public DeviceControllerException invalidURI(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ENTITY_NOT_FOUND)
    public DeviceControllerException invalidObjectNull();

    @DeclareServiceCode(ServiceCode.CONTROLLER_ENTITY_NOT_FOUND)
    public DeviceControllerException objectNotFound(final URI objId);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ENTITY_INACTIVE)
    public DeviceControllerException entityInactive(final URI id);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ENTITY_NOT_FOUND)
    public DeviceControllerException entityNullOrEmpty(final String id);

    // Invalid endpoint, not FC WWN: {0}
    @DeclareServiceCode(ServiceCode.CONTROLLER_ENDPOINTS_ERROR)
    public DeviceControllerException invalidEndpointExpectedFC(final String endpoint);

    // FC endpoint being added to non-FC network: {0}
    @DeclareServiceCode(ServiceCode.CONTROLLER_ENDPOINTS_ERROR)
    public DeviceControllerException invalidEndpointExpectedNonFC(final String endpoint);

    // Supplied endpoints not found for removal: {0}
    @DeclareServiceCode(ServiceCode.CONTROLLER_ENDPOINTS_ERROR)
    public DeviceControllerException endpointsNotFoundForRemoval(final Collection<String> endpoints);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ENDPOINTS_ERROR)
    public DeviceControllerException endPointsCannotBeAddedOrRemoved(final String endpoint, final String action);

    @DeclareServiceCode(ServiceCode.TRANSPORT_ZONE_ERROR)
    public DeviceControllerException unknownTransportZone(final String intitiator);

    @DeclareServiceCode(ServiceCode.CONTROLLER_VOLUME_REUSE_ERROR)
    public DeviceControllerException volumesAlreadyPartOfStorageGroups(final String volumes);

    @DeclareServiceCode(ServiceCode.CONTROLLER_UNEXPECTED_VOLUME)
    public DeviceControllerException notAVolumeOrBlocksnapshotUri(URI uri);

    @DeclareServiceCode(ServiceCode.TRANSPORT_ZONE_ERROR)
    public DeviceControllerException cannotFindSwitchConnectionToStoragePort(String storagePort);

    @DeclareServiceCode(ServiceCode.TRANSPORT_ZONE_ERROR)
    public DeviceControllerException cannotFindSwitchConnectionToInitiator();

    @DeclareServiceCode(ServiceCode.CONTROLLER_CANNOTLOCATEPORTS)
    public DeviceControllerException cannotLocateMatchingSanStoragePortInitiator(String initiatorPortWwn, String volume);

    @DeclareServiceCode(ServiceCode.CONTROLLER_CANNOTLOCATEPORTS)
    public DeviceControllerException cannotFindStoragePortSanFabricInitiator(String initiatorPort);

    @DeclareServiceCode(ServiceCode.TRANSPORT_ZONE_ERROR)
    public DeviceControllerException initiatorNotPartOfNetwork(String initiatorPort);

    @DeclareServiceCode(ServiceCode.TRANSPORT_ZONE_ERROR)
    public DeviceControllerException cannotMatchSanStoragePortInitiatorForVolume(String storagePort, String initiatorPort, String volume);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ENTITY_NOT_FOUND)
    public DeviceControllerException virtualArrayNotFoundForVolume(String volume);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ENTITY_NOT_FOUND)
    public DeviceControllerException virtualArrayNotFound();

    @DeclareServiceCode(ServiceCode
            .CONTROLLER_VMAX_MULTIPLE_MATCHING_COMPUTE_RESOURCE_MASKS)
            public DeviceControllerException
            vmaxMultipleMatchingComputeResourceMasks(String maskNames);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR_ASSIGNING_STORAGE_PORTS)
    public DeviceControllerException
            exceptionAssigningStoragePorts(String message, Throwable ex);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR_ASSIGNING_STORAGE_PORTS)
    public DeviceControllerException
            unexpectedExceptionAssigningPorts(Throwable ex);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public DeviceControllerException cannotFindActiveProviderForStorageSystem();

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public DeviceControllerException cannotFindValidActiveProviderForStorageSystem();

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public DeviceControllerException noNetworksConnectingVPlexToArray(String vplex, String array);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public DeviceControllerException multipleVarraysInVPLEXExportGroup(String array, String varray1, String varray2);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException srdfAsyncStepCreationfailed(String groupName);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException srdfAsyncStepDeletionfailed(String groupName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException consistencyGroupNotFound(String name, String deviceLabel);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException consistencyGroupNotFoundForProvider(String name, String label, String provider);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException
            failedToAddMembersToConsistencyGroup(String name, String deviceLabel, String error);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException
            failedToRemoveMembersToConsistencyGroup(String name, String deviceLabel, String error);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR)
    public DeviceControllerException failedToUpdateConsistencyGroup(String message);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException failedToAddMembersToReplicationGroup(String name, String deviceLabel, String error);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException failedToRemoveMembersFromReplicationGroup(String name, String deviceLabel, String error);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException existingExportFoundButWithSPsInDifferentNetwork(String vArrayName, String maskInfo);

    @DeclareServiceCode(ServiceCode.VPLEX_UNSUPPORTED_ARRAY)
    public DeviceControllerException unsupportedVPlexArray(final String arrayType, final String arrayLabel);

    @DeclareServiceCode(ServiceCode.VPLEX_VARRAY_HAS_MIXED_CLUSTERS)
    public DeviceControllerException vplexVarrayMixedClusters(final String varray, final String vplex);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException existingExportFoundButNotEnoughPortsToSatisfyMinPaths(String maskName, String totalPorts,
            String exportPathParam);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException failedToAcquireLock(String lockName, String operation);

    @DeclareServiceCode(ServiceCode.BLOCK_CONTROLLER_ERROR)
    public DeviceControllerException failedToReleaseLock(String lockName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_ERROR_ASSIGNING_STORAGE_PORTS)
    public DeviceControllerException unexpectedCondition(String message);
}
