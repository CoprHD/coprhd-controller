/*
 * Copyright (c) 2014 EMC Corporation
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
    public ServiceError createVolumesFailed(String volumes, String errorMsg);

}
