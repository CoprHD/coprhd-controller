/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.blockorchestrationcontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.Controller;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;

public interface BlockOrchestrationController extends Controller {
    public final static String BLOCK_ORCHESTRATION_DEVICE = "block-orchestration";

    /**
     * Creates one or more volumes having mixed technology attributes
     * (Block, BlockMirroring, RP, VPlex). This method is responsible for creating
     * a Workflow and invoking the BlockOrchestrationInterface.addStepsForCreateVolume
     * for each technology layer.
     * 
     * @param volumes -- The complete list of VolumeDescriptors (of all technology types)
     *            received from the API layer. This defines what volumes need to be created,
     *            and in which pool each volume should be created.
     * @param taskId -- The overall taskId for the operation.
     */
    public abstract void createVolumes(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException;

    /**
     * Deletes one or more volumes having potentially mixed technology attributes.
     * 
     * @param volumes -- a list of top level VolumeDescriptors
     * @param taskId -- The overall taskId for the operation
     */
    public abstract void deleteVolumes(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException;

    /**
     * Expands a single volume having potentially mixed technology attributes.
     * 
     * @param volumes -- a list of top level VolumeDescriptors
     * @param taskId -- The overall taskId for the operation
     */
    public abstract void expandVolume(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException;

    /**
     * Restores a single volume from a snapshot.
     * 
     * @param storage - URI of storage controller
     * @param pool - URI of pool where the volume belongs
     * @param volume - URI of volume to be restored
     * @param snapshot - URI of snapshot used for restoration
     * @param syncDirection specifies sync direction between R1 and R2
     * @param taskId - The top level operation's taskId
     * @throws ControllerException
     */
    void restoreVolume(URI storage, URI pool, URI volume, URI snapshot, String syncDirection, String taskId)
            throws ControllerException;

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
    
    /**
     * Restore contents the source volumes from the full copies with the passed
     * URIs.
     * 
     * @param storage The URI of the storage system.
     * @param fullCopyURIs The URIs of the full copies to be restored.
     * @param opId The unique operation Id.
     * 
     * @throws InternalException When an exception occurs restoring the full
     *             copies.
     */
    public void restoreFromFullCopy(URI storage, List<URI> fullCopyURIs, String opId)
            throws InternalException;
}
