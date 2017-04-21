package com.emc.storageos.remotereplicationcontroller;


import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByRelation;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesUriByAltId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationDataClient;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationDataClientImpl;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;

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
    public static  void validateRemoteReplicationOperation(RemoteReplicationElement rrElement, RemoteReplicationController.RemoteReplicationOperations operation) {
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
        //
        if (!isOperationValid) {
            // bad request
            throw APIException.badRequests.remoteReplicationLinkOperationIsNotAllowed(rrElement.getType().toString(), rrElement.getElementUri().toString(),
                    operation.toString());
        }
    }

    public static void validateRemoteReplicationModeChange(RemoteReplicationElement rrElement, String newMode) {

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

    /**
     * Create remote replication pair for srdf volume pair.
     *
     * @param sourceUri srdf source volume
     * @param targetUri srdf target volume
     */
    public static void createRemoteReplicationPairForSrdfPair(URI argSourceUri, URI argTargetUri, DbClient dbClient) {

        try {
            URI sourceUri = argSourceUri;
            URI targetUri = argTargetUri;
            boolean swapped = isSwapped(argSourceUri, dbClient);
            if (swapped) {
                sourceUri = argTargetUri;
                targetUri = argSourceUri;
            }
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair driverRrPair = null;
            driverRrPair = buildRemoteReplicationPairForSrdfPair(sourceUri, targetUri, swapped, dbClient);
            RemoteReplicationDataClient remoteReplicationDataClient = new RemoteReplicationDataClientImpl(dbClient);
            remoteReplicationDataClient.createRemoteReplicationPair(driverRrPair, sourceUri, targetUri);
        } catch (Exception ex) {
            String msg = String.format("Failed to create remote replication pair for srdf pair: %s -> %s", argSourceUri, argTargetUri);
            _log.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }

    /**
     * Delete remote replication pair for srdf volume pair.
     *
     * @param sourceUri srdf source volume
     * @param targetUri srdf target volume
     */
    public static void deleteRemoteReplicationPairForSrdfPair(URI argSourceUri, URI argTargetUri, DbClient dbClient) {
        String sourceLabel = null;
        String targetLabel = null;
        try {
            URI sourceUri = argSourceUri;
            URI targetUri = argTargetUri;
            if (isSwapped(argSourceUri, dbClient)) {
                sourceUri = argTargetUri;
                targetUri = argSourceUri;
            }
            Volume source = dbClient.queryObject(Volume.class, sourceUri);
            Volume target = dbClient.queryObject(Volume.class, targetUri);
            if (source == null) {
                _log.warn("Srdf source volume does not exist in database. ID: {}", sourceUri);
            } else {
                sourceLabel = source.getLabel();
            }
            if (target == null) {
                _log.warn("Srdf target volume does not exist in database. ID: {}", targetUri);
            } else {
                targetLabel = target.getLabel();
            }
            _log.info(String.format("Processing srdf pair: %s/%s -> %s/%s", sourceLabel, sourceUri, targetLabel, targetUri));

            RemoteReplicationDataClient remoteReplicationDataClient = new RemoteReplicationDataClientImpl(dbClient);
            remoteReplicationDataClient.deleteRemoteReplicationPair(sourceUri, targetUri);
        } catch (Exception ex) {
            String msg = String.format("Failed to delete remote replication pair for srdf pair: %s/%s -> %s/%s",
                    sourceLabel, argSourceUri, targetLabel, argTargetUri);
            _log.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }

    /**
     * Builds sb sdk remote replication pair for srdf source and target volumes.
     *
     * @param sourceUri
     * @param targetUri
     * @param dbClient
     * @return sb sdk remote replication pair
     */
    private static com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair
       buildRemoteReplicationPairForSrdfPair(URI inSourceUri, URI inTargetUri, boolean swapped, DbClient dbClient) {
        URI sourceUri = inSourceUri;
        URI targetUri = inTargetUri;
        try {
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair
                    driverRrPair = new com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair();
            Volume source = dbClient.queryObject(Volume.class, sourceUri);
            Volume target = dbClient.queryObject(Volume.class, targetUri);
            
            _log.info(String.format("Processing srdf pair: %s/%s -> %s/%s", source.getLabel(), sourceUri, target.getLabel(), targetUri));
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, source.getStorageController());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, target.getStorageController());

            URI rdfGroupId = target.getSrdfGroup();
            String replicationMode = target.getSrdfCopyMode();
            String replicationDirection = SRDFUtils.SyncDirection.SOURCE_TO_TARGET.toString();
            if (swapped) {
                replicationDirection = SRDFUtils.SyncDirection.TARGET_TO_SOURCE.toString();
                rdfGroupId = source.getSrdfGroup();
                replicationMode = source.getSrdfCopyMode();
            }
            RemoteDirectorGroup rdGroup = dbClient.queryObject(RemoteDirectorGroup.class, rdfGroupId);

            // Get native id for rr set.
            List<StorageSystem> storageSystems = Arrays.asList(sourceSystem, targetSystem);
            String rrSetNativeId = getRemoteReplicationSetNativeIdForSrdfSet(storageSystems);

            // Get nativeId for rr group.
            String rrGroupNativeId = null;
            if (rdGroup != null) {
                rrGroupNativeId = getRemoteReplicationGroupNativeIdForSrdfGroup(sourceSystem, targetSystem, rdGroup);
            } else {
                _log.info("RDF group is not defined for srdf pair: {} -> {}", sourceUri, targetUri);
            }

            StorageVolume driverSourceVolume = new StorageVolume();
            driverSourceVolume.setStorageSystemId(sourceSystem.getSerialNumber());
            driverSourceVolume.setNativeId(source.getNativeId());
            //
            StorageVolume driverTargetVolume = new StorageVolume();
            driverTargetVolume.setStorageSystemId(targetSystem.getSerialNumber());
            driverTargetVolume.setNativeId(target.getNativeId());

            driverRrPair.setNativeId(getRemoteReplicationPairNativeIdForSrdfPair(source, target));
            driverRrPair.setReplicationSetNativeId(rrSetNativeId);
            driverRrPair.setReplicationGroupNativeId(rrGroupNativeId);
            driverRrPair.setSourceVolume(driverSourceVolume);
            driverRrPair.setTargetVolume(driverTargetVolume);
            driverRrPair.setReplicationMode(replicationMode);
            driverRrPair.setReplicationState(source.getLinkStatus());
            driverRrPair.setReplicationDirection(replicationDirection);

            return driverRrPair;
        } catch (Exception ex) {
            String msg = String.format("Failed to build remote replication pair for srdf pair: %s -> %s", sourceUri, targetUri);
            _log.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }
    
    public static String getRemoteReplicationGroupNativeIdForSrdfGroup(StorageSystem sourceSystem, StorageSystem targetSystem,
                                                                      RemoteDirectorGroup rdGroup) {
        return sourceSystem.getSerialNumber() + Constants.PLUS + rdGroup.getSourceGroupId() + Constants.PLUS
                + targetSystem.getSerialNumber() + Constants.PLUS + rdGroup.getRemoteGroupId();
    }


    /**
     * Return native id of remote replication set based on srdf set.
     * @param storageSystems storage systems in the set
     * @return native id
     */
    public static String getRemoteReplicationSetNativeIdForSrdfSet(List<StorageSystem> storageSystems) {
        if (storageSystems == null || storageSystems.isEmpty()) {
            return null;
        }
        
        List<String> systemSerialNumbers = new ArrayList<>();
        for (StorageSystem system : storageSystems) {
            systemSerialNumbers.add(system.getSerialNumber());
        }
        Collections.sort(systemSerialNumbers);
        return StringUtils.join(systemSerialNumbers, "+");
    }

    public static String getRemoteReplicationPairNativeIdForSrdfPair(Volume source, Volume target) {
        return source.getNativeId()+ Constants.PLUS + target.getNativeId();
    }
    
    /**
     * checks for the existence of the remote replication pair and updates or creates it as needed
     * 
     * @param argSourceUri
     * @param argTargetUri
     * @param dbClient
     */
    public static void updateOrCreateReplicationPairForSrdfPair(URI argSourceUri, URI argTargetUri, DbClient dbClient) {
        try {
            URI sourceUri = argSourceUri;
            URI targetUri = argTargetUri;
            // NOTE: SRDF volume roles may not correspond to volume roles in remote replication pair.
            // This is due to the fact that SRDF swap operation changes source and target volumes, but
            // remote replication pair roles are immutable and swap operation changes replication direction
            // property.
            boolean swapped = isSwapped(argSourceUri, dbClient);
            if (swapped) {
                sourceUri = argTargetUri;
                targetUri = argSourceUri;
            }
            RemoteReplicationDataClient remoteReplicationDataClient = new RemoteReplicationDataClientImpl(dbClient);
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair driverRrPair = buildRemoteReplicationPairForSrdfPair(sourceUri, targetUri, swapped, dbClient);
            if (null == remoteReplicationDataClient.checkRemoteReplicationPairExistsInDB(sourceUri, targetUri)) {
                remoteReplicationDataClient.createRemoteReplicationPair(driverRrPair, sourceUri, targetUri);
            } else {
                remoteReplicationDataClient.updateRemoteReplicationPair(driverRrPair, sourceUri, targetUri);
            }
        } catch (Exception e) {
            String msg = String.format("Failed to update or create remote replication pair for srdf pair: %s -> %s", argSourceUri, argTargetUri);
            _log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    /**
     * updates properties of a RemoteReplicationPairobject for an SRDF source and target
     * 
     * @param argSourceUri
     * @param argTargetUri
     * @param dbClient
     */
    public static void updateRemoteReplicationPairForSrdfPair(URI argSourceUri, URI argTargetUri, DbClient dbClient) {
        try {
            URI sourceUri = argSourceUri;
            URI targetUri = argTargetUri;
            // NOTE: SRDF volume roles may not correspond to volume roles in remote replication pair.
            // This is due to the fact that SRDF swap operation changes source and target volumes, but
            // remote replication pair roles are immutable and swap operation changes replication direction
            // property.
            boolean swapped = isSwapped(argSourceUri, dbClient);
            if (swapped) {
                sourceUri = argTargetUri;
                targetUri = argSourceUri;
            }
            RemoteReplicationDataClient remoteReplicationDataClient = new RemoteReplicationDataClientImpl(dbClient);
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair driverRrPair = buildRemoteReplicationPairForSrdfPair(sourceUri, targetUri, swapped, dbClient);
            remoteReplicationDataClient.updateRemoteReplicationPair(driverRrPair, sourceUri, targetUri);
        } catch (Exception ex) {
            String msg = String.format("Failed to update remote replication pair for srdf pair: %s -> %s", argSourceUri, argTargetUri);
            _log.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }
    
    /**
     * determines if the source and target pair are in a swapped state based on the source volume virtual pool
     * 
     * @param sourceVolumeId
     * @param dbClient
     * @return
     */
    private static boolean isSwapped(URI sourceVolumeId, DbClient dbClient) {
        List<Volume> sourceVolume = dbClient.queryObjectField(Volume.class, "virtualPool", Arrays.asList(sourceVolumeId));
        if (sourceVolume == null || sourceVolume.isEmpty()) {
            String msg = String.format("Source volume could not be found in the ViPR database: %s", sourceVolumeId);
            _log.error(msg);
            throw new RuntimeException(msg);
        }
        
        List<VirtualPool> vpools = dbClient.queryObjectField(VirtualPool.class, "remoteProtectionSettings", Arrays.asList(sourceVolume.iterator().next().getVirtualPool()));
        return (vpools == null || vpools.isEmpty());
    }

}
