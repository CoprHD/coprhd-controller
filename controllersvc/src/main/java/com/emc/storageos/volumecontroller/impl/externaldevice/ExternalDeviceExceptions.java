/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;

import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.scaleio.ScaleIOException;
import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ExternalDeviceException}s
 * <p/>
 * Remember to add the English message associated to the method in ExternalDeviceExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface ExternalDeviceExceptions {

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_NO_DRIVER_DEFINED_FOR_DEVICE_ERROR)
    public ExternalDeviceException noDriverDefinedForDevice(String systemName);

    @DeclareServiceCode(ServiceCode.EXTERNALDEVICE_NO_NATIVEID_DEFINED_FOR_STORAGE_PORT_ERROR)
    public ExternalDeviceException noNativeIdDefinedForPort(String storageSystem, String storagePort);
}
