/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.blockorchestrationcontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.workflow.Workflow;

/**
 * This interface must be implemented by any controllers that want to participate
 * in the BlockOrchstrationDeviceController's creation or deletion of volumes.
 */
public interface BlockOrchestrationInterface {
    /**
     * Adds the steps necessary for creating one or more volumes of a given
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
    public String addStepsForCreateVolumes(
            Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId)
            throws InternalException;

    /**
     * Adds the steps necessary for deleting one or more volumes of a given
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
    public String addStepsForDeleteVolumes(
            Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId)
            throws InternalException;

    /**
     * Add the steps necessary for post processing after volume deletes have been processed. This
     * might be marking the database records inactive, or taking other actions.
     * 
     * @param workflow - a Workflow that is being constructed
     * @param waitFor -- The String key that should be used for waiting on previous steps in Workflow.createStep
     * @param volumes -- The entire list of VolumeDescriptors for this request (all technologies).
     * @param taskId -- The top level operation's taskId
     * @param completer -- The completer for the entire workflow.
     * @return
     */
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId, VolumeWorkflowCompleter completer);

    /**
     * Add the necessary steps for expanding volumes with mixed technology attributes.
     * 
     * @param workflow - Workflow being constructed
     * @param waitFor - The String key that should be used for waiting on previous steps in Workflow.createStep
     * @param volumeDescriptors - The entire list of VolumeDescriptors for this request (all technologies)
     * @param taskId - The top level operation's taskId
     * @return A waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller
     */
    public String addStepsForExpandVolume(
            Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException;

    /**
     * Add the necessary steps for restoring a volume from a snapshot.
     * 
     * @param workflow - Workflow being constructed
     * @param waitFor - The String key that should be used for waiting on previous steps in Workflow.createStep
     * @param storage - URI of storage controller
     * @param pool - URI of pool where the volume belongs
     * @param volume - URI of volume to be restored
     * @param snapshot - URI of snapshot used for restoration
     * @param updateOpStatus - Operation ID
     * @param taskId - The top level operation's taskId
     * @param completer - The completer for the entire workflow.
     * @return A waitFor key that can be used by subsequent controllers to wait on
     * @throws InternalException
     */
    public String addStepsForRestoreVolume(Workflow workflow, String waitFor, URI storage,
            URI pool, URI volume, URI snapshot, Boolean updateOpStatus, String syncDirection, String taskId,
            BlockSnapshotRestoreCompleter completer)
            throws InternalException;

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
}
