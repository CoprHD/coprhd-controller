/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.externaldevice;


import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface ExternalDeviceErrors {

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_CREATE_VOLUMES_ERROR)
    public ServiceError createVolumesFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_DELETE_VOLUMES_ERROR)
    public ServiceError deleteVolumesFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_CREATE_SNAPSHOTS_ERROR)
    public ServiceError createSnapshotsFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_CREATE_CONSISTENCY_GROUP_ERROR)
    public ServiceError createConsistencyGroupFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_DELETE_CONSISTENCY_GROUP_ERROR)
    public ServiceError deleteConsistencyGroupFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_DELETE_GROUP_SNAPSHOT_ERROR)
    public ServiceError deleteGroupSnapshotFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_DELETE_SNAPSHOT_ERROR)
    public ServiceError deleteSnapshotFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_RESTORE_FROM_SNAPSHOT_ERROR)
    public ServiceError restoreFromSnapshotFailed(String method, String errorMsg);


    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_CREATE_EXPORT_MASK_ERROR)
    public ServiceError createExportMaskFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_ADD_VOLUME_TO_EXPORT_MASK_ERROR)
    public ServiceError addVolumesToExportMaskFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_DELETE_VOLUME_FROM_EXPORT_MASK_ERROR)
    public ServiceError deleteVolumesFromExportMaskFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_DELETE_EXPORT_MASK_ERROR)
    public ServiceError deleteExportMaskFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_CREATE_VOLUME_CLONE_ERROR)
    public ServiceError createVolumeCloneFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_CREATE_GROUP_CLONE_ERROR)
    public ServiceError createGroupCloneFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_DETACH_CLONE_ERROR)
    public ServiceError detachVolumeCloneFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_RESTORE_CLONES_ERROR)
    public ServiceError restoreVolumesFromClonesFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_EXPAND_VOLUME_ERROR)
    public ServiceError expandVolumeFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_ADD_EXPORT_MASK_INITIATORS_ERROR)
    public ServiceError addInitiatorsToExportMaskFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_REMOVE_EXPORT_MASK_INITIATORS_ERROR)
    public ServiceError removeInitiatorsFromExportMaskFailed(String method, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_DRIVERTASK_ERROR)
    public ServiceError driverTaskFailed(String taskId, String systemId, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_CREATE_GROUP_REMOTE_REPLICATION_PAIR_ERROR)
    public ServiceError createGroupRemoteReplicationPairsFailed(String groupId, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_CREATE_SET_REMOTE_REPLICATION_PAIR_ERROR)
    public ServiceError createSetRemoteReplicationPairsFailed(String groupId, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_DELETE_REMOTE_REPLICATION_PAIR_ERROR)
    public ServiceError deleteRemoteReplicationPairsFailed(String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_CREATE_REMOTE_REPLICATION_GROUP_ERROR)
    public ServiceError createRemoteReplicationGroupFailed(String groupName, String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_MOVE_REMOTE_REPLICATION_PAIR_ERROR)
    public ServiceError moveRemoteReplicationPairFailed(final URI pairId, final URI targetGroupId, final String errorMsg);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_REMOTE_REPLICATION_LINK_OPERATION_ERROR)
    public ServiceError remoteReplicationLinkOperationFailed(String operationType, String elementType, String elementURI, String errorMsg);
}
