/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.Iterator;

import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
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
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the block object.
     */
    public static BlockObject validateSnapshotSessionSource(URI sourceURI, UriInfo uriInfo, DbClient dbClient) {
        ArgValidator.checkUri(sourceURI);
        if ((!URIUtil.isType(sourceURI, Volume.class)) && (!URIUtil.isType(sourceURI, BlockSnapshot.class))) {
            throw APIException.badRequests.invalidSnapshotSessionSource(sourceURI.toString());
        }

        BlockObject sourceObj = BlockObject.fetch(dbClient, sourceURI);
        ArgValidator.checkEntity(sourceObj, sourceURI, BlockServiceUtils.isIdEmbeddedInURL(sourceURI, uriInfo), true);
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
     * 
     * @return A reference to the BlockSnapshotSession instance.
     */
    public static BlockSnapshotSession querySnapshotSession(URI snapSessionURI, UriInfo uriInfo, DbClient dbClient) {
        ArgValidator.checkUri(snapSessionURI);
        BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);
        ArgValidator.checkEntity(snapSession, snapSessionURI, BlockServiceUtils.isIdEmbeddedInURL(snapSessionURI, uriInfo), true);
        return snapSession;
    }
}
