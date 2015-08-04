/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.util.List;

import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.model.TaskList;

public interface BlockSnapshotSessionApi {

    /**
     * Get a list of all block objects to be operated on given the passed
     * snapshot session source object for a snapshot session request.
     * 
     * @param sourceObj A reference to a Volume or BlockSnapshot instance.
     * 
     * @return A list of all snapshot session source objects.
     */
    public List<BlockObject> getAllSourceObjectsForSnapshotSessionRequest(BlockObject sourceObj);

    /**
     * Executes platform specific validation of a create block snapshot session request.
     * 
     * @param requestedSourceObj A reference to the source object.
     * @param sourceObjList A list of all source objects to be processed for the request.
     * @param project A reference to the source project.
     * @param name The requested name for the new block snapshot session.
     * @param createInactive Whether or not the session should be activated.
     * @param newTargetsCount The number of new target to create and link to the session.
     * @param newTargetCopyMode The copy mode for newly linked targets.
     * @param fcManager A reference to a full copy manager.
     */
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, boolean createInactive, int newTargetsCount, String newTargetCopyMode, BlockFullCopyManager fcManager);

    /**
     * Creates a new block snapshot session.
     * 
     * @return TaskList
     */
    public TaskList createSnapshotSession();
}
