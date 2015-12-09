package com.emc.storageos.volumecontroller.impl.utils;

import static com.emc.storageos.db.client.util.NullColumnValueGetter.isNullURI;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * Utility class for acquiring/checking consistency groups from ViPR block objects.
 *
 * @author Ian Bibby
 */
public class ConsistencyUtils {

    /**
     * Gets the {@BlockConsistencyGroup} associated with the given clone.
     *
     * @param cloneURI
     * @param dbClient
     * @return
     */
    public static BlockConsistencyGroup getCloneConsistencyGroup(URI cloneURI, DbClient dbClient) {
        BlockConsistencyGroup cgResult = null;
        Volume clone = dbClient.queryObject(Volume.class, cloneURI);

        if (clone != null) {
            URI systemURI = clone.getStorageController();
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, systemURI);
            if (storage.deviceIsType(DiscoveredDataObject.Type.ibmxiv)) {
                return null;
            }
            URI source = clone.getAssociatedSourceVolume();
            BlockObject sourceObj = BlockObject.fetch(dbClient, source);
            if (sourceObj instanceof BlockSnapshot) {
                return null;
            }
            Volume sourceVolume = (Volume) sourceObj;

            if (!isNullURI(sourceVolume.getConsistencyGroup())) {
                final URI cgId = sourceVolume.getConsistencyGroup();
                if (cgId != null) {
                    cgResult = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
                    if (!ControllerUtils.checkCGCreatedOnBackEndArray(sourceVolume)) {
                        return null;
                    }
                }
            }
        }

        return cgResult;
    }

    public static BlockConsistencyGroup getSnapshotSessionConsistencyGroup(URI snapshotSession, DbClient dbClient) {
        BlockConsistencyGroup cgResult = null;
        BlockSnapshotSession snapshotSessionObj = dbClient.queryObject(BlockSnapshotSession.class, snapshotSession);

        if (snapshotSessionObj != null) {
            URI parent = snapshotSessionObj.getParent().getURI();
            BlockObject parentObj = BlockObject.fetch(dbClient, parent);
            if (parentObj instanceof BlockSnapshot) {
                return null;
            }
            Volume parentVolumeObj = (Volume) parentObj;
            if (!isNullURI(parentVolumeObj.getConsistencyGroup())) {
                URI cgId = parentVolumeObj.getConsistencyGroup();
                if (cgId != null) {
                    cgResult = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
                }
            }
        }

        return cgResult;
    }

    /**
     * Returns true, if the given clone is in a consistency group, false otherwise.
     *
     * @param cloneURI
     * @param dbClient
     * @return
     */
    public static boolean isCloneInConsistencyGroup(URI cloneURI, DbClient dbClient) {
        return getCloneConsistencyGroup(cloneURI, dbClient) != null;
    }

    /**
     * Gets the {@BlockConsistencyGroup} associated with a snapshot in the given list of snapshots.
     *
     * @param snapshots
     * @param dbClient
     * @return
     */
    public static BlockConsistencyGroup getSnapshotsConsistencyGroup(List<BlockSnapshot> snapshots, DbClient dbClient) {
        if (snapshots.isEmpty()) {
            return null;
        }

        BlockConsistencyGroup cgResult = null;
        BlockSnapshot snapshot = snapshots.get(0);

        if (snapshot != null && !isNullURI(snapshot.getConsistencyGroup())) {
            cgResult = dbClient.queryObject(BlockConsistencyGroup.class, snapshot.getConsistencyGroup());
        }
        return cgResult;
    }

    /**
     * Returns true, if a snapshot in the given list of snapshots is in a consistency group, false otherwise.
     *
     * @param snapshots
     * @param dbClient
     * @return
     */
    public static boolean isSnapshotInConsistencyGroup(List<BlockSnapshot> snapshots, DbClient dbClient) {
        return getSnapshotsConsistencyGroup(snapshots, dbClient) != null;
    }

    /**
     * Gets the {@BlockConsistencyGroup} associated with a mirror in the given list of mirrors.
     *
     * @param mirrors
     * @param dbClient
     * @return
     */
    public static BlockConsistencyGroup getMirrorsConsistencyGroup(List<URI> mirrors, DbClient dbClient) {
        BlockMirror mirror = dbClient.queryObject(BlockMirror.class, mirrors.get(0));
        Volume source = dbClient.queryObject(Volume.class, mirror.getSource().getURI());
        BlockConsistencyGroup cgResult = null;

        if (source != null && source.isInCG() && ControllerUtils.checkCGCreatedOnBackEndArray(source)) {
            cgResult = dbClient.queryObject(BlockConsistencyGroup.class, source.getConsistencyGroup());
        }
        return cgResult;
    }

    /**
     * Returns true, if a mirror in the given list of mirrors is in a consistency group, false otherwise.
     *
     * @param mirrors
     * @param dbClient
     * @return
     */
    public static boolean isMirrorInConsistencyGroup(List<URI> mirrors, DbClient dbClient) {
        return getMirrorsConsistencyGroup(mirrors, dbClient) != null;
    }
}
