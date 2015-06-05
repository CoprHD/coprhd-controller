/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface ScaleIOErrors {
    @DeclareServiceCode(ServiceCode.SCALEIO_UNSUPPORTED_OPERATION)
    public ServiceError operationIsUnsupported(String op);

    @DeclareServiceCode(ServiceCode.SCALEIO_OPERATION_EXCEPTION)
    public ServiceError encounteredAnExceptionFromScaleIOOperation(String operation, String message);

    @DeclareServiceCode(ServiceCode.SCALEIO_CREATE_VOLUME_ERROR)
    public ServiceError createVolumeError(String volumeLabel, String scaleIOMsg);

    @DeclareServiceCode(ServiceCode.SCALEIO_DELETE_VOLUME_ERROR)
    public ServiceError deleteVolumeError(String volumeLabel, String scaleIOMsg);

    @DeclareServiceCode(ServiceCode.SCALEIO_MODIFY_VOLUME_CAPACITY_ERROR)
    public ServiceError modifyCapacityFailed(String nativeId, String errorString);

    @DeclareServiceCode(ServiceCode.SCALEIO_MAP_ERROR)
    public ServiceError mapVolumeToClientFailed(String volumeId, String sdcId, String errorString);

    @DeclareServiceCode(ServiceCode.SCALEIO_UNMAP_ERROR)
    public ServiceError unmapVolumeToClientFailed(String volumeId, String sdcId, String errorString);

    @DeclareServiceCode(ServiceCode.SCALEIO_CREATE_SNAPSHOT_ERROR)
    public ServiceError createSnapshotError(String snapshotLabel, String errorString);

    @DeclareServiceCode(ServiceCode.SCALEIO_DELETE_SNAPSHOT_ERROR)
    public ServiceError deleteSnapshotError(String snapshotLabel, String errorString);

    @DeclareServiceCode(ServiceCode.SCALEIO_CREATE_FULL_COPY_ERROR)
    public ServiceError createFullCopyError(String sourceLabel, String fullCopyLabel, String errorString);
}
