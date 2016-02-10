/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.exceptions;

import com.emc.storageos.cinder.errorhandling.CinderErrors;
import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiErrors;
import com.emc.storageos.hds.HDSErrors;
import com.emc.storageos.isilon.restapi.IsilonErrors;
import com.emc.storageos.netapp.NetAppErrors;
import com.emc.storageos.netappc.NetAppCErrors;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerErrors;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointErrors;
import com.emc.storageos.scaleio.ScaleIOErrors;
import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.vnx.xmlapi.VNXErrors;
import com.emc.storageos.vnxe.VNXeErrors;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DeviceDataCollectionErrors;
import com.emc.storageos.volumecontroller.impl.smis.SmisErrors;
import com.emc.storageos.vplex.api.VPlexErrors;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOErrors;

/**
 * This interface holds all the methods and interfaces used to create {@link ServiceError}s related to Device Controllers
 * <p/>
 * Remember to add the English message associated to the method in DeviceControllerMessages.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface DeviceControllerErrors {
    /** Holds the methods used to create Isilon related error conditions */
    public static final IsilonErrors isilon = ExceptionMessagesProxy.create(IsilonErrors.class);

    /** Holds the methods used to create DataDomain related error conditions */
    public static final DataDomainApiErrors datadomain = ExceptionMessagesProxy.create(DataDomainApiErrors.class);

    /** Holds the methods used to create SMIS related error conditions */
    public static final XtremIOErrors xtremio = ExceptionMessagesProxy.create(XtremIOErrors.class);

    /** Holds the methods used to create SMIS related error conditions */
    public static final SmisErrors smis = ExceptionMessagesProxy.create(SmisErrors.class);

    /** Holds the methods used to create NetApp related error conditions */
    public static final NetAppErrors netapp = ExceptionMessagesProxy.create(NetAppErrors.class);

    /** Holds the methods used to create NetApp Cluster Mode related error conditions */
    public static final NetAppCErrors netappc = ExceptionMessagesProxy.create(NetAppCErrors.class);

    /** Holds the methods used to create VPLEX related error conditions */
    public static final VPlexErrors vplex = ExceptionMessagesProxy.create(VPlexErrors.class);

    /** Holds the methods used to create network devices related error conditions */
    public static final NetworkDeviceControllerErrors network = ExceptionMessagesProxy.create(NetworkDeviceControllerErrors.class);

    /** Holds the methods used to create RecoverPoint related error conditions */
    public static final RecoverPointErrors recoverpoint = ExceptionMessagesProxy.create(RecoverPointErrors.class);

    /** Holds the methods used to create VNX related error conditions */
    public static final VNXErrors vnx = ExceptionMessagesProxy.create(VNXErrors.class);

    /** Holds the methods used to create HDS related error conditions */
    public static final HDSErrors hds = ExceptionMessagesProxy.create(HDSErrors.class);

    /** Holds the methods used to create Cinder related error conditions */
    public static final CinderErrors cinder = ExceptionMessagesProxy.create(CinderErrors.class);

    public static final ScaleIOErrors scaleio = ExceptionMessagesProxy.create(ScaleIOErrors.class);

    /** Holds the methods used to create VNXe related error conditions */
    public static final VNXeErrors vnxe = ExceptionMessagesProxy.create(VNXeErrors.class);

    public static final DeviceDataCollectionErrors dataCollectionErrors = ExceptionMessagesProxy.create(DeviceDataCollectionErrors.class);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError jobFailedOp(final String operationName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError jobFailedOpMsg(final String operationName, final String message);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError jobFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError jobFailedMsg(final String errorMessage, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_UNABLE_DELETE_INITIATOR_GROUPS)
    public ServiceError unableToDeleteIGs(final String maskingViewName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_INVALID_URI)
    public ServiceError invalidURI(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError createVolumesFailed(final String volUris, final String operationName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ABORTED)
    public ServiceError createVolumesAborted(final String volUris, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ABORTED)
    public ServiceError deleteVolumesAborted(final String volUris, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError expandVolumeFailed(final String volUri, final String operationName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError restoreVolumeFromSnapshotFailed(final String volUri, final String snapshotUri, final String operationName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError deleteVolumesFailed(final String volUris, final String operationName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError deleteVolumesFailedInactive(final String volUri);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError deleteVolumeStepFailedExc(final String volUris, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError groupCopyToTargetNotApplicable();

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError unableToScheduleJob(String jobType);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError unableToExecuteJob(String jobType);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError exportGroupOpInitInOtherMaskError(String initiatorPort, String mask);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError exportHasExistingVolumeWithRequestedHLU(String blockObjectId, String hlu);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError unableToScheduleJob(String jobType, Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError failedToAcquireScanningLock();

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError unforeseen();

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError unsupportedOperationOnDevType(String op, String devType);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError changeVirtualPoolFailed(final String volUris, final String operationName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_INITIATORS_WITH_DIFFERENT_OSTYPE)
    public ServiceError initiatorsWithDifferentOSType();

    @DeclareServiceCode(ServiceCode.CONTROLLER_MIXING_CLUSTERED_AND_NON_CLUSTERED_INITIATORS)
    public ServiceError mixingClusteredAndNonClusteredInitiators();

    @DeclareServiceCode(ServiceCode
            .CONTROLLER_NON_CLUSTER_EXPORT_WITH_INITIATORS_IN_DIFFERENT_IGS)
            public ServiceError nonClusterExportWithInitiatorsInDifferentExistingIGs();

    @DeclareServiceCode(ServiceCode.CONTROLLER_EXISTING_IG_HAS_DIFFERENT_PORTS)
    public ServiceError existingInitiatorGroupHasDifferentPorts(String name);

    @DeclareServiceCode(ServiceCode.CONTROLLER_EXISTING_IG_DOES_NOT_HAVE_SAME_PORTS)
    public ServiceError existingInitiatorGroupDoesNotHaveSamePorts(String name);

    @DeclareServiceCode(ServiceCode.CONTROLLER_VMAX_STORAGE_GROUP_NOT_FOUND)
    public ServiceError vmaxStorageGroupNameNotFound(String name);

    @DeclareServiceCode(ServiceCode.CONTROLLER_VMAX_FAST_EXPORT_STORAGE_GROUP_ALREADY_IN_MASKINGVIEW)
    public ServiceError vmaxFASTStorageGroupAlreadyPartOfExistingMaskingView(String name);

    @DeclareServiceCode(ServiceCode.CONTROLLER_VMAX_EXPORT_GROUP_CREATE_ERROR)
    public ServiceError vmaxExportGroupCreateError(String message);

    @DeclareServiceCode(ServiceCode.CONTROLLER_VMAX_CONCURRENT_REMOVE_FROM_SG_CAUSES_EMPTY_SG)
    public ServiceError concurrentRemoveFromSGCausesEmptySG(Throwable cause);

    @DeclareServiceCode(ServiceCode.XTREMIO_IG_NOT_FOUND)
    public ServiceError xtremioInitiatorGroupsNotDetected(String name);

    @DeclareServiceCode(ServiceCode.CONTROLLER_VMAX_MASK_SUPPORTS_SINGLE_HOST_ERROR)
    public ServiceError vmaxMaskSupportsSingleHostError(String igName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError changeVirtualArrayFailed(final String volUris,
            final String operationName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError createFileSharesFailed(final String fsUris, final String operationName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError deleteFileSharesFailed(final String fsUris, final String operationName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_JOB_ERROR)
    public ServiceError expandFileShareFailed(final String fsUris, final String operationName, final Throwable cause);

}
