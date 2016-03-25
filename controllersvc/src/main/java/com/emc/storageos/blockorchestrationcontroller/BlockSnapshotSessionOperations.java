/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.blockorchestrationcontroller;

import java.net.URI;

import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.workflow.Workflow;

/**
 * 
 * TODO We will move all the Snap Session related operations from BlockOrchestrationInterface to this class
 * We need to create seperate interface for snapshot, fulcopy as well like this class.
 *
 */
public interface BlockSnapshotSessionOperations {
    /**
     * 
     * @param workflow
     * @param waitFor
     * @param storage
     * @param volume
     * @param snapSession
     * @param updateOpStatus
     * @param taskId
     * @param completer
     * @return
     * @throws ControllerException
     */
    public String addStepsForRestoreFromSnapshotSession(Workflow workflow, String waitFor, URI storage, URI volume,
            URI snapSession, Boolean updateOpStatus, String taskId)
            throws ControllerException;
}
