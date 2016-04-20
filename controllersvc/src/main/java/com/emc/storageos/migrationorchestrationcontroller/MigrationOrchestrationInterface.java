package com.emc.storageos.migrationorchestrationcontroller;

import java.util.List;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.workflow.Workflow;

public interface MigrationOrchestrationInterface {
    /**
     * Adds the steps necessary for changing the virtual pool of one or more volumes of a given
     * technology (Block, RP, VPlex, etc.) to the given Workflow.
     * 
     * @param workflow -- a Workflow
     * @param waitFor -- The String key that should be used in the Workflow.createStep
     *            waitFor parameter in order to wait on the previous controller's actions to complete.
     * @param volumes -- The entire list of VolumeDescriptors for this request (all technologies).
     * @param taskId -- The top level operation's taskId.
     * @return -- A waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller.
     * @throws InternalException
     */
    public String addStepsForChangeVirtualPool(
            Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId)
            throws InternalException;

    /**
     * Adds the steps necessary for changing the virtual array of one or more
     * volumes of a given technology (Block, RP, VPlex, etc.) to the given
     * Workflow.
     * 
     * @param workflow -- a Workflow
     * @param waitFor -- The String key that should be used in the
     *            Workflow.createStep waitFor parameter in order to wait on the
     *            previous controller's actions to complete.
     * @param volumes -- The entire list of VolumeDescriptors for this request
     *            (all technologies).
     * @param taskId -- The top level operation's taskId.
     * @return -- A waitFor key that can be used by subsequent controllers to
     *         wait on the Steps created by this controller.
     * @throws InternalException
     */
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws InternalException;

    public String addStepsForMigrateVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws InternalException;
}
