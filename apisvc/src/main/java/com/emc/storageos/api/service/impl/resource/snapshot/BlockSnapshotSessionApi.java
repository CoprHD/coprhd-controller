/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;

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
     * @param newTargetsCount The number of new target to create and link to the session.
     * @param newTargetCopyMode The copy mode for newly linked targets.
     * @param fcManager A reference to a full copy manager.
     */
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, int newTargetsCount, String newTargetCopyMode, BlockFullCopyManager fcManager);

    /**
     * Prepare a ViPR BlockSnapshotSession instance for each source. Also, if new linked
     * targets are to be created and linked to the snapshot sessions, then prepare ViPR
     * BlockSnapshot instances to represent these linked targets.
     * 
     * @param sourceObjList The list of source objects for which we are to create a snapshot session.
     * @param snapSessionLabel The snapshot session label for these snapshot sessions.
     * @param newTargetCount The number of new targets to create and link to each snapshot session.
     * @param snapSessionURIs This OUT parameter gets populated with the URIs of the created snapshot sessions.
     * @param snapSessionSnapshotMap This OUT parameter gets populated with the BlockSnaphot instances created for each session, if any.
     * @param taskId The unique task identifier.
     * 
     * @return
     */
    public List<BlockSnapshotSession> prepareSnapshotSessions(List<BlockObject> sourceObjList, String snapSessionLabel, int newTargetCount,
            List<URI> snapSessionURIs, Map<URI, List<URI>> snapSessionSnapshotMap, String taskId);

    /**
     * Creates a new block snapshot session.
     * 
     * @return TaskList
     */
    public void createSnapshotSession(BlockObject sourceObj, List<URI> snapSessionURIs,
            Map<URI, List<URI>> snapSessionSnapshotMap, String copyMode, Boolean createInactive, String taskId);
}
