/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.SecurityContext;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.RPBlockServiceApiImpl;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
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
            String name, int newTargetsCount, String newTargetCopyMode, BlockFullCopyManager fcManager) {
        // TBD Future - Other platforms that support creation of arrays snapshots
        // without linked targets. Also RP protected VPLEX volumes backed by VMAX3
        // and these other platforms.

        // If the requested source is a simple VMAX3 volume that is RP protected,
        // then we simply do VMAX3 platform validation.
        // TBD - Need a check to allow only local, native snapshots of RP source volumes.
        URI requestedSourceURI = requestedSourceObj.getId();
        URI srcSystemURI = requestedSourceObj.getStorageController();
        StorageSystem srcSystem = _dbClient.queryObject(StorageSystem.class, srcSystemURI);
        if ((URIUtil.isType(requestedSourceURI, Volume.class))
                && (!RPBlockServiceApiImpl.isProtectionBasedSnapshot((Volume) requestedSourceObj,
                        BlockSnapshot.TechnologyType.NATIVE.name()))
                && (srcSystem.checkIfVmax3())) {
            BlockSnapshotSessionApi vmax3Impl = _blockSnapshotSessionMgr
                    .getPlatformSpecificImpl(BlockSnapshotSessionManager.SnapshotSessionImpl.vmax3);
            vmax3Impl.validateSnapshotSessionCreateRequest(requestedSourceObj, sourceObjList, project, name, newTargetsCount,
                    newTargetCopyMode, fcManager);
        } else {
            throw APIException.badRequests.createSnapSessionNotSupportedForRPProtected();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<URI> prepareSnapshotsForSession(int newTargetCount, BlockObject sourceObj, String sessionLabel,
            String sessionInstanceLabel) {
        // Important: that the only difference between these snapshots and snapshots created with the
        // create snapshot APIs is that the parent and project NamedURIs for the snapshot use the
        // snapshot label rather than the source label. This is an inconsistency between non-RP snaps
        // and other snaps and should probably be fixed.
        List<URI> snapshotURIs = super.prepareSnapshotsForSession(newTargetCount, sourceObj, sessionLabel, sessionInstanceLabel);
        Iterator<BlockSnapshot> snapshotsIter = _dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs, true);
        while (snapshotsIter.hasNext()) {
            BlockSnapshot snapshot = snapshotsIter.next();
            // This is a native snapshot so do not set the consistency group, otherwise
            // the SMIS code/array will get confused trying to look for a consistency
            // group that only exists in RecoverPoint.
            snapshot.setConsistencyGroup(null);
            _dbClient.persistObject(snapshot);
        }
        return snapshotURIs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(BlockObject sourceObj, List<URI> snapSessionURIs,
            Map<URI, List<URI>> snapSessionSnapshotMap, String copyMode, Boolean createInactive, String taskId) {
        BlockSnapshotSessionApi vmax3Impl = _blockSnapshotSessionMgr
                .getPlatformSpecificImpl(BlockSnapshotSessionManager.SnapshotSessionImpl.vmax3);
        vmax3Impl.createSnapshotSession(sourceObj, snapSessionURIs, snapSessionSnapshotMap, copyMode, createInactive, taskId);
    }
}
