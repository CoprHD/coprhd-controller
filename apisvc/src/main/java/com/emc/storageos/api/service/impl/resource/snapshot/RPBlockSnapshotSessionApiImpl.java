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

import javax.ws.rs.core.SecurityContext;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
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
}
