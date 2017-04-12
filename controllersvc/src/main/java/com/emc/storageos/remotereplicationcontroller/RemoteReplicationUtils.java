package com.emc.storageos.remotereplicationcontroller;


import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByAltId;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByRelation;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesUriByAltId;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;
import com.emc.storageos.volumecontroller.impl.plugins.ExternalDeviceDiscoveryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.util.CustomQueryUtility;

public class RemoteReplicationUtils {
    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationUtils.class);
    public static List<URI> getElements(DbClient dbClient, List<URI> remoteReplicationPairs) {

        List<RemoteReplicationPair> systemReplicationPairs = dbClient.queryObject(RemoteReplicationPair.class, remoteReplicationPairs);
        List<URI> volumes = new ArrayList<>();
        for(RemoteReplicationPair pair : systemReplicationPairs) {
            URI sourceVolume = pair.getSourceElement().getURI();
            URI targetVolume = pair.getTargetElement().getURI();
            volumes.add(sourceVolume);
            volumes.add(targetVolume);
        }
        return volumes;
    }

    public static VirtualPool getTargetVPool(VirtualPool remoteReplicationVPool, DbClient dbClient) {
        StringMap remoteReplicationSettings = remoteReplicationVPool.getRemoteReplicationProtectionSettings();
        VirtualPool targetVirtualPool = remoteReplicationVPool;

        // There can be multiple entries for target varray and vpool combinations in the vpool
        for (Map.Entry<String, String> entry : remoteReplicationSettings.entrySet()) {
            String targetVirtualPoolId = entry.getValue();
            if (targetVirtualPoolId != null) {
                targetVirtualPool = dbClient.queryObject(VirtualPool.class, URIUtil.uri(targetVirtualPoolId));
            }
            break;
        }
        return targetVirtualPool;
    }

    public static VirtualArray getTargetVArray(VirtualPool remoteReplicationVPool, DbClient dbClient) {
        StringMap remoteReplicationSettings = remoteReplicationVPool.getRemoteReplicationProtectionSettings();
        VirtualArray targetVirtualArray = null;

        // There can be multiple entries for target varray and vpool combinations in the vpool
        for (Map.Entry<String, String> entry : remoteReplicationSettings.entrySet()) {
            String targetVirtualArrayId = entry.getKey();
            targetVirtualArray = dbClient.queryObject(VirtualArray.class, URIUtil.uri(targetVirtualArrayId));
            break;
        }
        return targetVirtualArray;
    }

    /**
     * Return RemoteReplicaionSet objects for a given storage system.
     *
     * @param storageSystem storage system instance
     * @param dbClient  database client
     * @return
     */
    public static List<RemoteReplicationSet> getRemoteReplicationSetsForStorageSystem(StorageSystem storageSystem, DbClient dbClient) {
        String storageSystemType = storageSystem.getSystemType();
        List<RemoteReplicationSet> systemSets = new ArrayList<>();

        // get existing replication sets for the storage system type
        List<URI> remoteReplicationSets =
                queryActiveResourcesUriByAltId(dbClient, RemoteReplicationSet.class,
                        "storageSystemType", storageSystemType);

        for (URI setUri : remoteReplicationSets) {
            RemoteReplicationSet systemSet = dbClient.queryObject(RemoteReplicationSet.class, setUri);
            if(systemSet.getSystemToRolesMap().keySet().contains(storageSystem.getId().toString())) {
                systemSets.add(systemSet);
            }
        }
        return systemSets;
    }


    public static List<RemoteReplicationPair> getRemoteReplicationPairsForSourceElement(URI sourceElementURI, DbClient dbClient) {
        _log.info("Called: getRemoteReplicationPairsForSourceElement() for storage element {}", sourceElementURI);

        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair> rrPairs =
                queryActiveResourcesByRelation(dbClient, sourceElementURI, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair.class,
                        "sourceElement");

        _log.info("Found pairs: {}", rrPairs);

        return rrPairs;
    }

    public static List<RemoteReplicationPair> getRemoteReplicationPairsForCG(BlockConsistencyGroup cg, DbClient dbClient) {
        _log.info("Called: getRemoteReplicationPairsForCG() for consistency group {}", cg.getId());
        List<RemoteReplicationPair> rrPairs = new ArrayList<>();
        List<Volume> cgVolumes = CustomQueryUtility.queryActiveResourcesByRelation(dbClient, cg.getId(),
                Volume.class, "consistencyGroup");

        // get all remote replication pairs where these volumes are source volumes
        for (Volume volume : cgVolumes) {
           rrPairs.addAll(getRemoteReplicationPairsForSourceElement(volume.getId(), dbClient));
        }
        return rrPairs;
    }


    /**
     * Validate that remote replication link operation is valid based on the remote replication configuration discovered on the device.
     *
     * @param rrElement remote replication element (pair, cg, group, set)
     * @param operation operation
     * @return true/false
     */
    public static void validateRemoteReplicationOperation(DbClient dbClient, RemoteReplicationElement rrElement, RemoteReplicationController.RemoteReplicationOperations operation) {
        boolean isOperationValid = true;
        // todo: validate that this operation is valid (operational validity):
        //   For rr pairs:
        //     parent set supports operations on pairs;
        //     if pair is in a group, check that group consistency is not enforced (operations are allowed on subset of pairs);
        //   For rr cgs:
        //     parent set supports operations on pairs;
        //     if pairs are in groups, check that group consistency is not enforced (operations are allowed on subset of pairs);
        //   For groups:
        //     parent set supports operations on groups;
        //   For sets:
        //     set supports operations on sets;
        switch (rrElement.getType()) {
            case REPLICATION_PAIR:
                RemoteReplicationPair rrPair = dbClient.queryObject(RemoteReplicationPair.class, rrElement.getElementUri());
                isOperationValid = supportOperationOnRrPair(rrPair, dbClient, true);
                break;
            case CONSISTENCY_GROUP:
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, rrElement.getElementUri());
                List<RemoteReplicationPair> rrPairs = getRemoteReplicationPairsForCG(cg, dbClient);
                for (RemoteReplicationPair pair : rrPairs) {
                    if (!(isOperationValid &= supportOperationOnRrPair(pair, dbClient, true))) {
                        break;
                    }
                }
                break;
            case REPLICATION_GROUP:
                RemoteReplicationGroup rrGroup = dbClient.queryObject(RemoteReplicationGroup.class, rrElement.getElementUri());
                RemoteReplicationSet rrSet = getRemoteReplicationSetForRrGroup(dbClient, rrGroup);
                if (rrSet == null || !rrSet.supportRemoteReplicationGroupOperation()) {
                    isOperationValid = false;
                }
                break;
            case REPLICATION_SET:
                rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrElement.getElementUri());
                if (!rrSet.supportRemoteReplicationSetOperation()) {
                    isOperationValid = false;
                }
                break;
        }

        if (!isOperationValid) { // bad request
            throw APIException.badRequests.remoteReplicationLinkOperationIsNotAllowed(rrElement.getType().toString(), rrElement.getElementUri().toString(),
                    operation.toString());
        }
    }

    /**
     * Find a remote replication set that meets below constraints:
     *  1. has the same storage system type with the given remote replication group;
     *  2. contains both the source and target systems of the remote replication group.
     */
    private static RemoteReplicationSet getRemoteReplicationSetForRrGroup(DbClient dbClient, RemoteReplicationGroup rrGroup) {
        List<RemoteReplicationSet> rrSets =
                queryActiveResourcesByAltId(dbClient, RemoteReplicationSet.class, "storageSystemType", rrGroup.getStorageSystemType());
        for (RemoteReplicationSet rrSet : rrSets) {
            if (rrSet.getSystemToRolesMap().containsKey(rrGroup.getSourceSystem().toString())
                    && rrSet.getSystemToRolesMap().containsKey(rrGroup.getTargetSystem().toString())) {
                return rrSet;
            }
        }
        // should not run here because there's must be a rr set for a rr group
        return null;
    }

    /**
     * @param rrPair
     *            remote replication pair to be validated
     * @param dbClient
     * @param checkRrGroupEnforcement
     *            whether it's needed to check group consistency enforcement of
     *            rr pair's parent rr group (if has)
     * @return true if rr set of given rr pair support rr pair granularity
     *         operation and its rr group (if has) doesn't enforce group group
     *         consistency when checkRrGroupEnforcement is true
     */
    private static boolean supportOperationOnRrPair(RemoteReplicationPair rrPair, DbClient dbClient, boolean checkRrGroupEnforcement) {
        RemoteReplicationSet rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrPair.getReplicationSet());
        if (!rrSet.supportRemoteReplicationPairOperation()) {
            return false;
        }
        if (!checkRrGroupEnforcement) {
            return true;
        }
        URI rrGroupUri;
        if ((rrGroupUri = rrPair.getReplicationGroup()) == null) {
            return true;
        }
        RemoteReplicationGroup rrGroup = dbClient.queryObject(RemoteReplicationGroup.class, rrGroupUri);
        if (rrGroup.getIsGroupConsistencyEnforced() == Boolean.TRUE) {
            return false;
        }
        return false;
    }

    public static void validateRemoteReplicationModeChange(DbClient dbClient, RemoteReplicationElement rrElement, String newMode) {

        // todo: validate that this operation is valid:
        //   For rr pair and cgs:
        //       validate that parent set supports operations on pairs;
        //       validate that pair is not in rr group;
        //       validate that set supports new replication mode;
        //       check that set/group parents are reachable
        //   For rr group:
        //       check if group is reachable;
        //       validate that parent set supports operation on groups;
        //       validate that parent set supports new replication mode;
        //   For rr set:
        //       check id set is reachable;
        //       validate that set supports operations on sets;
        //       validate that set supports new replication mode;
        //
        boolean isChangeValid = true;
        switch (rrElement.getType()) {
            case REPLICATION_PAIR:
                RemoteReplicationPair rrPair = dbClient.queryObject(RemoteReplicationPair.class, rrElement.getElementUri());
                isChangeValid = supportModeChangeOnRrPair(rrPair, dbClient, newMode);
                break;
            case CONSISTENCY_GROUP:
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, rrElement.getElementUri());
                List<RemoteReplicationPair> rrPairs = getRemoteReplicationPairsForCG(cg, dbClient);
                for (RemoteReplicationPair pair : rrPairs) {
                    if (!(isChangeValid &= supportModeChangeOnRrPair(pair, dbClient, newMode))) {
                        break;
                    }
                }
                break;
            case REPLICATION_GROUP:
                RemoteReplicationGroup rrGroup = dbClient.queryObject(RemoteReplicationGroup.class, rrElement.getElementUri());
                if (rrGroup.getReachable() != Boolean.TRUE) {
                    isChangeValid = false;
                    break;
                }
                RemoteReplicationSet rrSet = getRemoteReplicationSetForRrGroup(dbClient, rrGroup);
                isChangeValid = rrSet.supportRemoteReplicationGroupOperation() && rrSet.supportMode(newMode);
                break;
            case REPLICATION_SET:
                rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrElement.getElementUri());
                isChangeValid = rrSet.getReachable() == Boolean.TRUE && rrSet.supportRemoteReplicationSetOperation()
                        && rrSet.supportMode(newMode);
                break;
        }
        if (!isChangeValid) {
            throw APIException.badRequests.remoteReplicationLinkOperationIsNotAllowed(rrElement.getType().toString(),
                    rrElement.getElementUri().toString(), newMode);
        }
    }

    private static boolean supportModeChangeOnRrPair(RemoteReplicationPair rrPair, DbClient dbClient, String newMode) {
        RemoteReplicationSet rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrPair.getReplicationSet());
        return rrSet.getReachable() == Boolean.TRUE && rrSet.supportRemoteReplicationPairOperation()
                && rrPair.getReplicationGroup() == null && rrSet.supportMode(newMode);
    }

    public static Iterator<RemoteReplicationSet> findAllRemoteRepliationSetsIteratively(DbClient dbClient) {
        List<URI> ids = dbClient.queryByType(RemoteReplicationSet.class, true);
        _log.info("Found sets: {}", ids);
        return dbClient.queryIterativeObjects(RemoteReplicationSet.class, ids);
    }

    public static Iterator<RemoteReplicationGroup> findAllRemoteRepliationGroupsIteratively(DbClient dbClient) {
        List<URI> ids = dbClient.queryByType(RemoteReplicationGroup.class, true);
        _log.info("Found groups: {}", ids);
        return dbClient.queryIterativeObjects(RemoteReplicationGroup.class, ids);
    }
}
