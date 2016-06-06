package com.emc.storageos.migrationorchestrationcontroller;

import java.util.List;

import com.emc.storageos.Controller;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.volumecontroller.ControllerException;

public interface MigrationOrchestrationController extends Controller {
    public final static String MIGRATION_ORCHESTRATION_DEVICE = "migration-orchestration";
    /**
     * Changes the virtual pool of one or more volumes having potentially mixed technology attributes.
     * 
     * @param volumes -- a list of top level VolumeDescriptors
     * @param taskId -- The overall taskId for the operation
     */
    public abstract void changeVirtualPool(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException;

    /**
     * Changes the virtual array of one or more volumes.
     * 
     * @param volumeDescriptors -- Descriptors for the volumes participating in the varray change.
     * @param taskId -- The overall taskId for the operation.
     */
    public abstract void changeVirtualArray(List<VolumeDescriptor> volumeDescriptors,
            String taskId) throws ControllerException;
}
