package com.emc.storageos.migrationorchestrationcontroller;

import java.util.List;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.volumecontroller.ControllerException;

public class MigrationOrchestrationControllerImpl implements MigrationOrchestrationController {

    @Override
    public void changeVirtualPool(List<VolumeDescriptor> volumes, String taskId) throws ControllerException {
        System.out.println(volumes.get(0));
    }

    @Override
    public void changeVirtualArray(List<VolumeDescriptor> volumeDescriptors, String taskId) throws ControllerException {
        System.out.println(volumeDescriptors.get(0));
    }
}
