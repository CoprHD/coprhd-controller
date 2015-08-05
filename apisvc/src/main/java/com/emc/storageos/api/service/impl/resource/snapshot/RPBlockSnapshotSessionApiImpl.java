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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.SecurityContext;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 *
 */
public class RPBlockSnapshotSessionApiImpl extends DefaultBlockSnapshotSessionApiImpl {
    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private RPBlockSnapshotSessionApiImpl() {
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
    public RPBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator, PermissionsHelper permissionsHelper,
            SecurityContext securityContext, BlockSnapshotSessionManager blockSnapshotSessionMgr) {
        super(dbClient, coordinator, permissionsHelper, securityContext, blockSnapshotSessionMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, boolean createInactive, int newTargetsCount, String newTargetCopyMode, BlockFullCopyManager fcManager) {
        // TBD Future - Other platforms that support creation of arrays snapshots
        // without linked targets. Also RP protected VPLEX volumes backed by VMAX3
        // and these other platforms.

        // If the requested source is a simple VMAX3 volume that is RP protected,
        // then we simply do VMAX3 platform validation.
        URI srcSystemURI = requestedSourceObj.getStorageController();
        StorageSystem srcSystem = _dbClient.queryObject(StorageSystem.class, srcSystemURI);
        if (srcSystem.checkIfVmax3()) {
            BlockSnapshotSessionApi vmax3Impl = _blockSnapshotSessionMgr
                    .getPlatformSpecificImpl(BlockSnapshotSessionManager.SnapshotSessionImpl.vmax3);
            vmax3Impl.validateSnapshotSessionCreateRequest(requestedSourceObj, sourceObjList, project, name, createInactive,
                    newTargetsCount, newTargetCopyMode, fcManager);
        } else {
            throw APIException.badRequests.createSnapSessionNotSupportedForRPProtected();
        }
    }

    @Override
    protected Map<URI, BlockSnapshot> prepareSnapshotsForSession(int newTargetCount, BlockObject sourceObj, String sessionLabel,
            String sessionInstanceLabel) {
        Map<URI, BlockSnapshot> snapshotsMap = new HashMap<URI, BlockSnapshot>();
        for (int i = 1; i <= newTargetCount; i++) {
        }

        /*
         * boolean isRPTarget = false;
         * if (NullColumnValueGetter.isNotNullValue(volume.getPersonality()) &&
         * volume.getPersonality().equals(PersonalityTypes.TARGET.name())) {
         * isRPTarget = true;
         * }
         * 
         * BlockSnapshot snapshot = prepareSnapshotFromVolume(volumeToSnap, snapshotName, (isRPTarget ? volume : null));
         * snapshot.setTechnologyType(snapshotType);
         * 
         * // Check to see if the requested volume is a former target that is now the
         * // source as a result of a swap. This is done by checking the source volume's
         * // virtual pool for RP protection. If RP protection does not exist, we know this
         * // is a former target.
         * // TODO: In the future the swap functionality should update the vpools accordingly to
         * // add/remove protection. This check should be removed at that point and another
         * // method to check for a swapped state should be used.
         * boolean isFormerTarget = false;
         * VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
         * if (NullColumnValueGetter.isNotNullValue(volume.getPersonality()) &&
         * volume.getPersonality().equals(PersonalityTypes.SOURCE.name()) &&
         * !VirtualPool.vPoolSpecifiesProtection(vpool)) {
         * isFormerTarget = true;
         * }
         * 
         * if (((isRPTarget || isFormerTarget) && vplex) || !vplex) {
         * // For RP+Vplex target and former target volumes, we do not want to create a
         * // backing array CG snap. To avoid doing this, we do not set the consistency group.
         * // OR
         * // This is a native snapshot so do not set the consistency group, otherwise
         * // the SMIS code/array will get confused trying to look for a consistency
         * // group that only exists in RecoverPoint.
         * snapshot.setConsistencyGroup(null);
         * }
         * 
         * snapshots.add(snapshot);
         * 
         * _log.info(String.format("Prepared snapshot : [%s]", snapshot.getLabel()));
         * }
         */

        return snapshotsMap;
    }
}
