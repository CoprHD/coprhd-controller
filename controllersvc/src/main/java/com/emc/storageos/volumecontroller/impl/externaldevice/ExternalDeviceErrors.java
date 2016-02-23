/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.externaldevice;


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

}
