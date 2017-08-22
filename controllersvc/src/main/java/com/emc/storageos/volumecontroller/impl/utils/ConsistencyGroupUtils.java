package com.emc.storageos.volumecontroller.impl.utils;

import static com.emc.storageos.db.client.util.NullColumnValueGetter.isNullURI;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * Utility class for acquiring/checking consistency groups from ViPR block objects.
 *
 * @author Ian Bibby
 */
public class ConsistencyGroupUtils {

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
        BlockSnapshotSession snapshotSessionObj = dbClient.queryObject(BlockSnapshotSession.class, snapshotSession);

        if (snapshotSessionObj != null) {
            URI consistencyGroupId = snapshotSessionObj.getConsistencyGroup();

            if (!isNullURI(consistencyGroupId) && NullColumnValueGetter.isNotNullValue(snapshotSessionObj.getReplicationGroupInstance())) {
                return dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);
            }
        }
        return null;
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

        if (snapshot != null && !isNullURI(snapshot.getConsistencyGroup()) &&
                getSourceConsistencyGroupName(snapshot, dbClient) != null) {
            cgResult = dbClient.queryObject(BlockConsistencyGroup.class, snapshot.getConsistencyGroup());
        }
        return cgResult;
    }

    /**
     * Gets the source consistency group name.
     * If the given block object is Volume, get the group name from Volume object.
     * If snapshot, mirror or clone, get the group name from its parent which is Volume.
     * 
     * @param bo the block object
     * @return the consistency group name
     */
    public static String getSourceConsistencyGroupName(BlockObject bo, DbClient dbClient) {
        Volume volume = null;
        if (bo instanceof BlockSnapshot) {
            volume = dbClient.queryObject(Volume.class, ((BlockSnapshot) bo).getParent().getURI());
        } else if (bo instanceof BlockMirror) {
            volume = dbClient.queryObject(Volume.class, ((BlockMirror) bo).getSource().getURI());
        } else if (bo instanceof Volume) {
            volume = (Volume) bo;
            if (ControllerUtils.isVolumeFullCopy(volume, dbClient)) {
                volume = dbClient.queryObject(Volume.class, volume.getAssociatedSourceVolume());
            }
        }

        if (volume == null || NullColumnValueGetter.isNullValue(volume.getReplicationGroupInstance())) {
            return null;
        }

        return volume.getReplicationGroupInstance();
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

    /**
     * Assume a consistency group is empty if it has no underlying storage
     * system
     */
    public static boolean isConsistencyGroupEmpty(BlockConsistencyGroup group) {
        return NullColumnValueGetter.isNullURI(group.getStorageController());
    }

    /**
     * Validate URI and find consistency group by URI
     */
    public static BlockConsistencyGroup findConsistencyGroupById(URI uri, DbClient dbClient) {
        BlockConsistencyGroup cGroup = dbClient.queryObject(BlockConsistencyGroup.class, uri);
        if (cGroup == null) {
            throw APIException.badRequests.invalidConsistencyGroup();
        }
        return cGroup;
    }

    /**
     * Find all consistency groups by given alternateLabel, and return their storageControllers
     */
    public static Set<String> findAllRRConsistencyGroupSystemsByAlternateLabel(String label, DbClient dbClient) {
        DataObjectType doType = TypeMap.getDoType(BlockConsistencyGroup.class);
        AlternateIdConstraint constraint = new AlternateIdConstraintImpl(doType.getColumnField("alternateLabel"),
                label);
        URIQueryResultList uris = new URIQueryResultList();
        dbClient.queryByConstraint(constraint, uris);
        Set<String> systems = new HashSet<>();
        for (URI uri : uris) {
            BlockConsistencyGroup group = dbClient.queryObject(BlockConsistencyGroup.class, uri);
            URI storageController = group.getStorageController();
            if (storageController == null) {
                continue;
            }
            systems.add(storageController.toString());
        }
        return systems;
    }

    public static boolean isConsistencyGroupSupportRemoteReplication(BlockConsistencyGroup cGroup) {
        return cGroup.checkForRequestedType(Types.RR);
    }
}
