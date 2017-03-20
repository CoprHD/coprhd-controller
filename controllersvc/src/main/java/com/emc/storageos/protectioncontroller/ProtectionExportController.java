/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.protectioncontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.workflow.Workflow;

/**
 * Protection Export Base Controller Interface
 */
public interface ProtectionExportController {

    /**
     * Adds the export group create workflow steps.
     *
     * @param workflow the main workflow
     * @param wfGroupId the workflow group Id, if any
     * @param waitFor the id of a step on which this workflow has to wait, if any
     * @param export the export group being updated
     * @param objectsToAdd the map of block objects to be added
     * @param storageUri the block storage system
     * @param initiatorURIs the new list of initiators to be added
     * @return the id of the last step that was added to main workflow
     */
    public String addStepsForExportGroupCreate(Workflow workflow, String wfGroupId, String waitFor, URI export,
            Map<URI, Integer> objectsToAdd, URI storageUri, List<URI> initiatorURIs);

    /**
     * Adds the export group remove volumes workflow steps.
     *
     * @param workflow the main workflow
     * @param wfGroupId the workflow group Id, if any
     * @param waitFor the id of a step on which this workflow has to wait, if any
     * @param export the export group being updated
     * @param objectsToRemove the map of block objects to be removed
     * @param storageUri the block storage system
     * @return the id of the last step that was added to main workflow
     */
    public String addStepsForExportGroupRemoveVolumes(Workflow workflow, String wfGroupId, String waitFor, URI export,
            Map<URI, Integer> objectsToRemove, URI storageUri);

    /**
     * Adds the export group create workflow steps.
     *
     * @param workflow the main workflow
     * @param wfGroupId the workflow group Id, if any
     * @param waitFor the id of a step on which this workflow has to wait, if any
     * @param export the export group being updated
     * @param objectsToAdd the map of block objects to be added
     * @param storageUri the block storage system
     * @return the id of the last step that was added to main workflow
     */
    public String addStepsForExportGroupAddVolumes(Workflow workflow, String wfGroupId, String waitFor, URI export,
            Map<URI, Integer> objectsToAdd, URI storageUri);
}
