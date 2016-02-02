/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.util.List;

import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public interface FileOrchestrationInterface {
    /**
     * Adds the steps necessary for creating one or more filesystems of a given
     * 
     * @param workflow -- a Workflow
     * @param waitFor -- The String key that should be used in the Workflow.createStep
     *            waitFor parameter in order to wait on the previous controller's actions to complete.
     * @param filesystems -- The entire list of filesystem uri's for this request (all technologies).
     * @param taskId -- The top level operation's taskId.
     * @return -- A waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller.
     * @throws InternalException
     */
    public String addStepsForCreateFileSystems(
            Workflow workflow, String waitFor, List<FileDescriptor> filesystems, String taskId)
            throws InternalException;

    /**
     * Adds the steps necessary for deleting one or more filesystems of a given
     * technology (syncIQ, replicator, SnapshotMirror etc.) to the given Workflow.
     *
     * @param workflow -- a Workflow
     * @param waitFor -- The String key that should be used in the Workflow.createStep
     *            waitFor parameter in order to wait on the previous controller's actions to complete.
     * @param filesystems -- The entire list of filesystem uri's for this request (all technologies).
     * @param taskId -- The top level operation's taskId.
     * @return -- A waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller.
     * @throws InternalException
     */
    public String addStepsForDeleteFileSystems(
            Workflow workflow, String waitFor, List<FileDescriptor> filesystems, String taskId)
            throws InternalException;

    /**
     * Add the necessary steps for expanding filesystems
     *
     * @param workflow - Workflow being constructed
     * @param waitFor - The String key that should be used for waiting on previous steps in Workflow.createStep
     * @param fileDescriptors - The entire list of FileDescriptors for this request (all technologies)
     * @param taskId - The top level operation's taskId
     * @return A waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller
     */
    public String addStepsForExpandFileSystems(
            Workflow workflow, String waitFor, java.util.List<FileDescriptor> fileDescriptors, String taskId)
            throws InternalException;

}
