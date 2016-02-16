/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * 
 */
public class BlockSnapshotSessionUtils {

    /**
     * Returns the volume or block snapshot instance for the passed URI.
     * 
     * @param sourceURI The URI for the Volume or BlockSnapshot instance.
     * @param uriInfo A reference to the URI information.
     * @param checkAssociatedVolumes check if the passed source is an associated volume for another volume.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the block object.
     */
    public static BlockObject querySnapshotSessionSource(URI sourceURI, UriInfo uriInfo, boolean checkAssociatedVolumes, DbClient dbClient) {
        ArgValidator.checkUri(sourceURI);
        if ((!URIUtil.isType(sourceURI, Volume.class)) && (!URIUtil.isType(sourceURI, BlockSnapshot.class))) {
            throw APIException.badRequests.invalidSnapshotSessionSource(sourceURI.toString());
        }
        BlockObject sourceObj = BlockObject.fetch(dbClient, sourceURI);
        ArgValidator.checkEntity(sourceObj, sourceURI, BlockServiceUtils.isIdEmbeddedInURL(sourceURI, uriInfo), true);

        // This essentially checks if the passed snapshot session source is
        // a backend volume for a VPLEX volume, in which case the VPLEX volume
        // is returned.
        if (URIUtil.isType(sourceURI, Volume.class) && (checkAssociatedVolumes)) {
            List<Volume> volumes = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, Volume.class,
                    AlternateIdConstraint.Factory.getVolumeByAssociatedVolumesConstraint(sourceURI.toString()));
            if (!volumes.isEmpty()) {
                sourceObj = volumes.get(0);
            }
        }

        return sourceObj;
    }

    /**
     * Returns the project for the snapshot session source.
     * 
     * @param sourceObj A reference to the Volume or BlockSnapshot instance.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the project for the snapshot session source.
     */
    public static Project querySnapshotSessionSourceProject(BlockObject sourceObj, DbClient dbClient) {
        URI sourceURI = sourceObj.getId();
        URI projectURI = null;
        if (URIUtil.isType(sourceURI, Volume.class)) {
            projectURI = ((Volume) sourceObj).getProject().getURI();
        } else if (URIUtil.isType(sourceURI, BlockSnapshot.class)) {
            projectURI = ((BlockSnapshot) sourceObj).getProject().getURI();
        }

        if (projectURI == null) {
            throw APIException.badRequests.invalidSnapshotSessionSource(sourceURI.toString());
        }

        Project project = dbClient.queryObject(Project.class, projectURI);
        return project;
    }

    /**
     * Returns the vpool for the snapshot session source.
     * 
     * TBD Future - Tried to write this to be generic such that the source
     * may be a Volume or BlockSnapshot even though we don't currently support
     * creating a snapshot session of a BlockSnapshot. However, if we do
     * support this then we can't assume that parent of the passed source is
     * a Volume. We may need to do something recursively to get back the
     * originating source volume for these cascaded snapshots.
     * 
     * @param sourceObj A reference to the Volume or BlockSnapshot instance.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the vpool for the snapshot session source.
     */
    public static VirtualPool querySnapshotSessionSourceVPool(BlockObject sourceObj, DbClient dbClient) {
        URI sourceURI = sourceObj.getId();
        URI vpoolURI = null;
        if (URIUtil.isType(sourceURI, Volume.class)) {
            vpoolURI = ((Volume) sourceObj).getVirtualPool();
        } else if (URIUtil.isType(sourceURI, BlockSnapshot.class)) {
            URI parentURI = ((BlockSnapshot) sourceObj).getParent().getURI();
            // It may be possible that the source for the snapshot
            // session is a backend source volume for a VPLEX volume
            // when we support creating snapshot sessions for VPLEX
            // volumes backed by storage that supports snapshot sessions.
            // In this case, we want the VPLEX volume vpool.
            URIQueryResultList results = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeByAssociatedVolumesConstraint(parentURI.toString()), results);
            Iterator<URI> resultsIter = results.iterator();
            if (resultsIter.hasNext()) {
                parentURI = resultsIter.next();
            }

            Volume parentVolume = dbClient.queryObject(Volume.class, parentURI);
            vpoolURI = parentVolume.getVirtualPool();
        }

        if (vpoolURI == null) {
            throw APIException.badRequests.invalidSnapshotSessionSource(sourceURI.toString());
        }

        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolURI);
        return vpool;
    }

    /**
     * Validates and returns the BlockSnapshotSession instance with the passed URI.
     * 
     * @param sourceURI The URI for a BlockSnapshotSession instance.
     * @param uriInfo A reference to the URI information.
     * @param dbClient A reference to a database client.
     * @param checkInactive true to check if the snapshot session is inactive.
     * 
     * @return A reference to the BlockSnapshotSession instance.
     */
    public static BlockSnapshotSession querySnapshotSession(URI snapSessionURI, UriInfo uriInfo, DbClient dbClient, boolean checkInactive) {
        ArgValidator.checkUri(snapSessionURI);
        BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);
        ArgValidator.checkEntity(snapSession, snapSessionURI, BlockServiceUtils.isIdEmbeddedInURL(snapSessionURI, uriInfo), checkInactive);
        return snapSession;
    }

    /**
     * Validate that the passed targets represented by the BlockSnapshot instances
     * with the passed URIs are linked to the passed BlockSnapshotSession.
     * 
     * @param snapSession A reference to a BlockSnapshotSession.
     * @param snapshotURIs The URIs of the BlockSnapshot instances to verify.
     * @param uriInfo A reference to the URI information.
     * @param dbClient A reference to a database client.
     * 
     * @return A list of BlockSnapshot instances representing the session targets.
     */
    public static List<BlockSnapshot> validateSnapshotSessionTargets(BlockSnapshotSession snapSession, Set<URI> snapshotURIs,
            UriInfo uriInfo,
            DbClient dbClient) {
        StringSet sessionTargets = snapSession.getLinkedTargets();
        if ((sessionTargets == null) || (sessionTargets.isEmpty())) {
            // The snapshot session does not have any targets.
            throw APIException.badRequests.snapshotSessionDoesNotHaveAnyTargets(snapSession.getId().toString());
        }

        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        Iterator<URI> snapshotURIsIter = snapshotURIs.iterator();
        while (snapshotURIsIter.hasNext()) {
            // Snapshot session targets are represented by BlockSnapshot instances in ViPR.
            URI snapshotURI = snapshotURIsIter.next();
            BlockSnapshot snapshot = validateSnapshot(snapshotURI, uriInfo, dbClient);
            String snapshotId = snapshotURI.toString();
            if (!sessionTargets.contains(snapshotId)) {
                // The target is not linked to the snapshot session.
                throw APIException.badRequests.targetIsNotLinkedToSnapshotSession(snapshotId, snapSession.getId().toString());
            }
            snapshots.add(snapshot);
        }

        return snapshots;
    }

    /**
     * Validate the BlockSnapshot instance with the passed URI.
     * 
     * @param snapshotURI The URI of the BlockSnapshot instance to verify.
     * @param uriInfo A reference to the URI information.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the BlockSnapshot instance.
     */
    public static BlockSnapshot validateSnapshot(URI snapshotURI, UriInfo uriInfo, DbClient dbClient) {
        ArgValidator.checkUri(snapshotURI);
        BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotURI);
        ArgValidator.checkEntity(snapshot, snapshotURI, BlockServiceUtils.isIdEmbeddedInURL(snapshotURI, uriInfo), true);
        return snapshot;
    }

    /**
     * Return the BlockSnapshotSession associated with the given BlockSnapshot.
     *
     * @param snapshot  BlockSnapshot.
     * @param dbClient  Database client.
     * @return          BlockSnapshotSession, or null if snapshot is not a linked target.
     */
    public static BlockSnapshotSession getLinkedTargetSnapshotSession(BlockSnapshot snapshot, DbClient dbClient) {
        List<BlockSnapshotSession> sessions = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                BlockSnapshotSession.class,
                ContainmentConstraint.Factory.getLinkedTargetSnapshotSessionConstraint(snapshot.getId()));

        if (!sessions.isEmpty()) {
            return sessions.get(0);
        }
        return null;
    }
}