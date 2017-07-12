/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.workflow.Workflow;

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
     * Adds steps necessary to check for an existing target filesystem in database or else create one.
     * @param workflow-- a Workflow
     * @param waitFor -- The String key that should be used in the Workflow.createStep
     *            waitFor parameter in order to wait on the previous controller's actions to complete.
     * @param filesystems -- The entire list of filesystem uri's for this request (all technologies).
     * @param sourceFS
     * @param policyURI -- uri of the policy template to be applied
     * @param taskId -- the top level operations taskid
     * @return -- A waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller.
     */
    public String addStepsForCheckAndCreateFileSystems(Workflow workflow,
            String waitFor, List<FileDescriptor> filesystems, URI sourceFS, URI policyURI, String taskId);

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
    
    /**
     * Add the necessary steps for reduceing size of filesystems
     *
     * @param workflow - Workflow being constructed
     * @param waitFor - The String key that should be used for waiting on previous steps in Workflow.createStep
     * @param fileDescriptors - The entire list of FileDescriptors for this request (all technologies)
     * @param taskId - The top level operation's taskId
     * @return A waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller
     */
    public String addStepsForReduceFileSystems(
            Workflow workflow, String waitFor, java.util.List<FileDescriptor> fileDescriptors, String taskId)
            throws InternalException;

}
