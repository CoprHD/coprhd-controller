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

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.SecurityContext;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * 
 */
public class VMAX3BlockSnapshotSessionApiImpl extends DefaultBlockSnapshotSessionApiImpl {

    private static final int MAX_LINKED_TARGETS_PER_SOURCE = 1024;

    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private VMAX3BlockSnapshotSessionApiImpl() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param dbClient A reference to a data base client.
     * @param coordinator A reference to the coordinator client.
     * @param permissionsHelper A reference to a permission helper.
     * @param securityContext A reference to the security context.
     * @param blockSnapshotSessionMgr A reference to the snapshot session manager.
     */
    public VMAX3BlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator, PermissionsHelper permissionsHelper,
            SecurityContext securityContext, BlockSnapshotSessionManager blockSnapshotSessionMgr) {
        super(dbClient, coordinator, permissionsHelper, securityContext, blockSnapshotSessionMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, boolean createInactive, int newTargetsCount, String newTargetCopyMode, BlockFullCopyManager fcManager) {
        // Do the super class validation.
        super.validateSnapshotSessionCreateRequest(requestedSourceObj, sourceObjList, project, name, createInactive, newTargetsCount,
                newTargetCopyMode, fcManager);

        // Verify new target copy mode is a valid value.
        verifyNewTargetCopyMode(newTargetCopyMode);
    }

    /**
     * Verifies the copy mode specified is valid.
     * 
     * @param copyMode The copy mode specified in the request.
     */
    private void verifyNewTargetCopyMode(String copyMode) {
        if ((!BlockSnapshotSession.CopyMode.copy.name().equals(copyMode))
                && (!BlockSnapshotSession.CopyMode.nocopy.name().equals(copyMode))) {
            throw APIException.badRequests.invalidCopyModeForLinkedTarget(copyMode);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void verifyNewTargetCount(BlockObject sourceObj, int newTargetsCount) {
        if (newTargetsCount > 0) {
            // The total number of linked targets for all sessions for a given
            // source can't exceed 1024. So, get all sessions for the source
            // and add all linked targets for these sessions. That value plus
            // the number of new targets cannot exceed the max.
            int totalLinkedTargets = newTargetsCount;
            List<BlockSnapshotSession> snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    BlockSnapshotSession.class, ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(sourceObj.getId()));
            for (BlockSnapshotSession snapSession : snapSessions) {
                StringSet linkedTargetIds = snapSession.getLinkedTargets();
                if (linkedTargetIds != null) {
                    totalLinkedTargets += linkedTargetIds.size();
                }
            }

            if (totalLinkedTargets > MAX_LINKED_TARGETS_PER_SOURCE) {
                throw APIException.badRequests.invalidNewLinkedTargetsCount(newTargetsCount, sourceObj.getLabel(),
                        MAX_LINKED_TARGETS_PER_SOURCE - totalLinkedTargets);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(BlockObject sourceObj, List<URI> snapSessionURIs,
            Map<URI, Map<URI, BlockSnapshot>> snapSessionSnapshotMap, String copyMode, boolean createInactive, String taskId) {
        // Invoke the BlockDeviceController to create the array snapshot session and create and link
        // target volumes as necessary.
    }
}
