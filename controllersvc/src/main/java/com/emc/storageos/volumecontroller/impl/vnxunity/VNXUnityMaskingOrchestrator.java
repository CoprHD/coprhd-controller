package com.emc.storageos.volumecontroller.impl.vnxunity;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.vnxe.VNXeMaskingOrchestrator;

public class VNXUnityMaskingOrchestrator extends VNXeMaskingOrchestrator {

    private static final AtomicReference<BlockStorageDevice> VNXUNITY_BLOCK_DEVICE = new AtomicReference<BlockStorageDevice>();
    public static final String VNXUNITY_DEVICE = "vnxunityDevice";
    public static final String DEFAULT_LABEL = "Default";

    @Override
    public BlockStorageDevice getDevice() {
        BlockStorageDevice device = VNXUNITY_BLOCK_DEVICE.get();
        synchronized (VNXUNITY_BLOCK_DEVICE) {
            if (device == null) {
                device = (BlockStorageDevice) ControllerServiceImpl.getBean(VNXUNITY_DEVICE);
                VNXUNITY_BLOCK_DEVICE.compareAndSet(null, device);
            }
        }
        return device;
    }

}
