/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface XtremIOErrors {
    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError createVolumeFailure(final String errMsg);

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError deleteVolumeFailure(final String errMsg);

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError expandVolumeFailure(final Throwable cause);

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError createSnapshotFailure(final Throwable cause);

    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError operationFailed(final String string, final String message);
    
    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError expandSnapshotFailure(final Throwable cause);
    
    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError restoreSnapshotFailureSourceSizeMismatch(final String message);
    
    @DeclareServiceCode(ServiceCode.XTREMIO_API_ERROR)
    public ServiceError resyncSnapshotFailureSourceSizeMismatch(final String message);
}
