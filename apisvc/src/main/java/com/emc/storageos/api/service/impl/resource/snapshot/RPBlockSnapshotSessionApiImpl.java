/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.RPBlockServiceApiImpl;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
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
            String name, int newTargetsCount, String newTargetsName, String newTargetCopyMode, boolean skipInternalCheck,
            BlockFullCopyManager fcManager) {
        // TBD Future - RP protected VPLEX volumes backed by a supported platform.

        // If the requested source is a simple volume that is RP protected,
        // then we simply do platform specific validation for that volume's
        // platform.
        URI requestedSourceURI = requestedSourceObj.getId();
        URI srcSystemURI = requestedSourceObj.getStorageController();
        StorageSystem srcSystem = _dbClient.queryObject(StorageSystem.class, srcSystemURI);
        if ((URIUtil.isType(requestedSourceURI, Volume.class))
                && (!RPBlockServiceApiImpl.isProtectionBasedSnapshot((Volume) requestedSourceObj,
                        BlockSnapshot.TechnologyType.NATIVE.name()))) {
            BlockSnapshotSessionApi snapSessionImpl = _blockSnapshotSessionMgr.getPlatformSpecificImplForSystem(srcSystem);
            snapSessionImpl.validateSnapshotSessionCreateRequest(requestedSourceObj, sourceObjList, project, name, newTargetsCount,
                    newTargetsName, newTargetCopyMode, skipInternalCheck, fcManager);
        } else {
            throw APIException.badRequests.createSnapSessionNotSupportedForRPProtected();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<URI, BlockSnapshot> prepareSnapshotsForSession(BlockObject sourceObj, int sessionCount, int newTargetCount,
            String newTargetsName) {
        // Important: that the only difference between these snapshots and snapshots created with the
        // create snapshot APIs is that the parent and project NamedURIs for the snapshot use the
        // snapshot label rather than the source label. This is an inconsistency between non-RP snaps
        // and other snaps and should probably be fixed.
        Map<URI, BlockSnapshot> snapshotMap = super.prepareSnapshotsForSession(sourceObj, sessionCount, newTargetCount, newTargetsName);
        for (BlockSnapshot snapshot : snapshotMap.values()) {
            // This is a native snapshot so do not set the consistency group, otherwise
            // the SMIS code/array will get confused trying to look for a consistency
            // group that only exists in RecoverPoint.
            snapshot.setConsistencyGroup(null);
            _dbClient.persistObject(snapshot);
        }
        return snapshotMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(BlockObject sourceObj, List<URI> snapSessionURIs,
            Map<URI, List<URI>> snapSessionSnapshotMap, String copyMode, String taskId) {
        URI srcSystemURI = sourceObj.getStorageController();
        StorageSystem srcSystem = _dbClient.queryObject(StorageSystem.class, srcSystemURI);
        BlockSnapshotSessionApi snapSessionImpl = _blockSnapshotSessionMgr
                .getPlatformSpecificImplForSystem(srcSystem);
        snapSessionImpl.createSnapshotSession(sourceObj, snapSessionURIs, snapSessionSnapshotMap, copyMode, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateLinkNewTargetsRequest(BlockObject snapSessionSourceObj, Project project, int newTargetsCount,
            String newTargetsName, String newTargetCopyMode) {
        throw APIException.methodNotAllowed.notSupportedForRP();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRelinkSnapshotSessionTargets(BlockSnapshotSession tgtSnapSession, Project project,
            List<URI> snapshotURIs, UriInfo uriInfo) {
        throw APIException.methodNotAllowed.notSupportedForRP();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateUnlinkSnapshotSessionTargets(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project,
            Set<URI> snapshotURIs, UriInfo uriInfo) {
        throw APIException.methodNotAllowed.notSupportedForRP();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRestoreSnapshotSession(BlockObject snapSessionSourceObj, Project project) {
        throw APIException.methodNotAllowed.notSupportedForRP();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateDeleteSnapshotSession(BlockSnapshotSession snapSession, Project project) {
        throw APIException.methodNotAllowed.notSupportedForRP();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockSnapshotSession> getSnapshotSessionsForSource(BlockObject sourceObj) {
        throw APIException.methodNotAllowed.notSupportedForRP();
    }
}
