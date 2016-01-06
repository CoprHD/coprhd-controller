/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.block.AbstractMaskingFirstOrchestrator;

public abstract class ExternalDeviceMaskingOrchestrator extends AbstractMaskingFirstOrchestrator {
    @Override
    public BlockStorageDevice getDevice() {
        return null;
    }
}
