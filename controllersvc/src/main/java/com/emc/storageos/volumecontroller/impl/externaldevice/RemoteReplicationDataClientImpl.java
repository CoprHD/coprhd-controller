package com.emc.storageos.volumecontroller.impl.externaldevice;


import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByRelation;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.ExternalDeviceDiscoveryUtils;

public class RemoteReplicationDataClientImpl implements RemoteReplicationDataClient {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationDataClientImpl.class);
    private DbClient _dbClient;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public RemoteReplicationDataClientImpl(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    public RemoteReplicationDataClientImpl() {
    }

    @Override
    public void processRemoteReplicationSetsForStorageSystem(StorageSystem storageSystem, List<RemoteReplicationSet> replicationSets) {

        // natively managed systems, as VMAX, do not have nativeId set, we will use serial number for identity
        String systemNativeId = (storageSystem.getNativeId() == null)? storageSystem.getSerialNumber() : storageSystem.getNativeId();

        _log.info("processRemoteReplicationSetsForStorageSystem: processing sets for storage system {}  with nativeId {}, type {}",
                storageSystem.getId(), systemNativeId, storageSystem.getSystemType());

        // For each replication set create/update persistent set.
        List<DataObject> objectsToCreate = new ArrayList<>();
        List<DataObject> objectsToUpdate = new ArrayList<>();
        List<DataObject> notReachableExistingObjects = new ArrayList<>();
        String storageSystemType = storageSystem.getSystemType();
        try {
            for (RemoteReplicationSet driverSet : replicationSets) {
                com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet systemRRSet = null;
                try {
                    systemRRSet = ExternalDeviceDiscoveryUtils.processDriverRRSet(driverSet, objectsToCreate, objectsToUpdate, storageSystemType, _dbClient);
                } catch (Exception e) {
                    _log.error("Failed to process replication set {} of type {}. Error: {}.",
                            driverSet.getNativeId(), storageSystemType, e.getMessage(), e);
                    continue;
                }

                if (!systemRRSet.getReachable()) {
                    String message = String.format("Remote replication set %s of type %s was set to not reachable.",
                            driverSet.getNativeId(), storageSystemType);
                    _log.error(message);
                }
            }

            // check which existing system sets we did not discover --- set them as not reachable
            List<URI> updatedObjectUrisList = URIUtil.toUris(objectsToUpdate);
            Set<URI> updatedObjectUris = new HashSet<URI>(updatedObjectUrisList);

            // get existing replication sets for the storage system instance
            List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet> remoteReplicationSets =
                    RemoteReplicationUtils.getRemoteReplicationSetsForStorageSystem(storageSystem, _dbClient);
            // set existing replication sets which are not in the replicationSets argument to not reachable
            for (com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet systemSet : remoteReplicationSets) {
                if (!updatedObjectUris.contains(systemSet.getId())) {
                    // unreachable
                    systemSet.setReachable(false);
                    _log.warn("Existing replication set {}, id: {} was not discovered. Set to not reachable.", systemSet.getNativeGuid(),
                            systemSet.getId());
                    notReachableExistingObjects.add(systemSet);
                }
            }
        } catch (Exception e) {
            String message = String.format("Failed to process remote replication sets for storage system %s of type %s . Error: %s",
                    storageSystem.getId(), storageSystemType, e.getMessage());
            _log.error(message, e);
            throw e;
        } finally {
            // update database with results
            _dbClient.createObject(objectsToCreate);
            _dbClient.updateObject(objectsToUpdate);
            _dbClient.updateObject(notReachableExistingObjects);
        }
    }

    @Override
    public void processRemoteReplicationGroupsForStorageSystem(StorageSystem storageSystem, List<RemoteReplicationGroup> replicationGroups) {

        try {
            List<DataObject> objectsToCreate = new ArrayList<>();
            List<DataObject> objectsToUpdate = new ArrayList<>();
            List<DataObject> notReachableObjects = new ArrayList<>();

            // natively managed systems, as VMAX, do not have nativeId set, we will use serial number for identity
            String systemNativeId = (storageSystem.getNativeId() == null)? storageSystem.getSerialNumber() : storageSystem.getNativeId();

            _log.info("processRemoteReplicationGroupsForStorageSystem: processing groups for storage system {}, type {} - start",
                    storageSystem.getId(), storageSystem.getSystemType());

            List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup> systemRRGroups =
                    ExternalDeviceDiscoveryUtils.processDriverRRGroups(replicationGroups, systemNativeId, objectsToCreate, objectsToUpdate,
                            storageSystem.getSystemType(), _dbClient);

            List<URI> updatedObjectUrisList = URIUtil.toUris(objectsToUpdate);
            Set<URI> updatedObjectUris = new HashSet<URI>(updatedObjectUrisList);
            // find remote replication groups for this storage system which should be marked as not reachable
            // get all remote replication groups where this system is source system
            List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup> rrGroupsForSystem =
                    queryActiveResourcesByRelation(_dbClient,  storageSystem.getId(), com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup.class,
                            "sourceSystem");

            _log.info("Found existing groups for storage system in source role {}, groups {}", systemNativeId, rrGroupsForSystem);

            for (com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup existingGroup : rrGroupsForSystem) {
                if (!updatedObjectUris.contains(existingGroup.getId())) {
                    // unreachable
                    existingGroup.setReachable(false);
                    _log.warn("Existing replication group {}, id: {} was set to not reachable.", existingGroup.getNativeGuid(),
                            existingGroup.getId());
                    notReachableObjects.add(existingGroup);
                }
            }
            // update database with discovery results
            _dbClient.createObject(objectsToCreate);
            _dbClient.updateObject(objectsToUpdate);
            _dbClient.updateObject(notReachableObjects);
            String message = String.format("Remote replication groups for storage system %s with native id %s were processed successfully.",
                    storageSystem.getId(), systemNativeId);
            _log.info(message);
        } catch (Exception e) {
            String message = String.format("Failed to process remote replication groups for storage system %s with nativeId %s. Error: %s",
                    storageSystem.getId(), storageSystem.getNativeGuid(), e.getMessage());
            _log.error(message, e);
            throw e;
        }
    }


    /**
     *
     * Builds system remote replication pair for a given driver pair.
     * @param driverReplicationPair, driver remote replication pair, type: Input.
     * @param sourceVolumeURI source volume in the pair
     * @param targetVolumeURI target volume in the pair
     */
    @Override
    public void createRemoteReplicationPair(RemoteReplicationPair driverReplicationPair, URI sourceVolumeURI, URI targetVolumeURI) {
        com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair rrPair =
                new com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair();

        try {
            Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
            Volume targetVolume = _dbClient.queryObject(Volume.class, targetVolumeURI);

            if (sourceVolume == null) {
                String message = String.format("Cannot find volume %s for replication pair %s",
                            sourceVolumeURI, driverReplicationPair.getNativeId());
                    _log.error(message);
                return;
            }

            if (targetVolume == null) {
                String message = String.format("Cannot find volume %s for replication pair %s",
                        targetVolumeURI, driverReplicationPair.getNativeId());
                _log.error(message);
                return;
            }

            StorageSystem sourceStorageSystem = _dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());
            if (sourceStorageSystem == null) {
                String message = String.format("Cannot find storage system %s for volume %s",
                        sourceVolume.getStorageController(), sourceVolume.getId());
                _log.error(message);
                return;
            }

            // natively managed systems, as VMAX, do not have nativeId set, we will use serial number for identity
            String systemNativeId = (sourceStorageSystem.getNativeId() == null)? sourceStorageSystem.getSerialNumber() : sourceStorageSystem.getNativeId();

            _log.info("Processing replication pair {} with set nativeId {} and group nativeId {}.",
                    driverReplicationPair.getNativeId(), driverReplicationPair.getReplicationSetNativeId(), driverReplicationPair.getReplicationGroupNativeId());

            // Prepare system replication pair
            rrPair.setId(URIUtil.createId(com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair.class));
            rrPair.setElementType(com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair.ElementType.VOLUME);
            com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet systemSet =
                    getReplicationSetForDriverPair(driverReplicationPair, sourceStorageSystem.getSystemType());
            if (systemSet != null) {
                rrPair.setReplicationSet(systemSet.getId());
            }
            com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup systemGroup =
                    getReplicationGroupForDriverPair(driverReplicationPair, sourceStorageSystem);
            if (systemGroup != null) {
                rrPair.setReplicationGroup(systemGroup.getId());
            }
            rrPair.setNativeId(driverReplicationPair.getNativeId());
            rrPair.setReplicationState(driverReplicationPair.getReplicationState());
            rrPair.setReplicationMode(driverReplicationPair.getReplicationMode());
            rrPair.setReplicationDirection(driverReplicationPair.getReplicationDirection());
            rrPair.setSourceElement(new NamedURI(sourceVolumeURI, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair.ElementType.VOLUME.toString()));
            rrPair.setTargetElement(new NamedURI(targetVolumeURI, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair.ElementType.VOLUME.toString()));
            rrPair.setTenant(sourceVolume.getTenant());
            rrPair.setProject(sourceVolume.getProject());

            _log.info("Remote Replication Pair {} ", rrPair);
            _dbClient.createObject(rrPair);
        } catch (Exception ex) {
            String message = String.format("Failed to create replication pair %s ",
                    driverReplicationPair.getNativeId());
            _log.error(message, ex);
        }

    }


    @Override
    public void updateRemoteReplicationPair(RemoteReplicationPair driverReplicationPair, URI sourceVolumeURI, URI targetVolumeURI) throws DatabaseException {

    }

    @Override
    public void deleteRemoteReplicationPair(RemoteReplicationPair driverReplicationPair, URI sourceVolumeURI, URI targetVolumeURI) throws DatabaseException {
        com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair rrPairToDelete = null;
        try {
            Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
            Volume targetVolume = _dbClient.queryObject(Volume.class, targetVolumeURI);
            if (sourceVolume == null) {
                String message = String.format("Cannot find volume %s for replication pair %s",
                        sourceVolumeURI, driverReplicationPair.getNativeId());
                _log.error(message);
                return;
            }
            if (targetVolume == null) {
                String message = String.format("Cannot find volume %s for replication pair %s",
                        targetVolumeURI, driverReplicationPair.getNativeId());
                _log.error(message);
                return;
            }
            // Find system remote replication pair for the driver pair and source and target volumes
            List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair> rrPairs =
                    queryActiveResourcesByRelation(_dbClient, sourceVolumeURI, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair.class,
                            "sourceElement");
            for (com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair rrPair : rrPairs) {
                if (rrPair.getTargetElement().getURI().equals(targetVolumeURI) && rrPair.getNativeId().equals(driverReplicationPair.getNativeId())) {
                     rrPairToDelete = rrPair;
                     break;
                }
            }
            if (rrPairToDelete == null) {
                String message = String.format("Cannot find remote replication pair with native id %s for source volume %s and target volume %s",
                driverReplicationPair.getNativeId(), sourceVolumeURI, targetVolumeURI);
                _log.error(message);
                return;
            }
            _log.info("Found system replication pair to delete: {}, source volume {}, target volume {}.",
                    rrPairToDelete.getNativeId(), sourceVolumeURI, targetVolumeURI);
            _dbClient.markForDeletion(rrPairToDelete);
        } catch (Exception ex) {
            String message = String.format("Failed to delete replication pair %s with source/target volumes %s/%s",
                    driverReplicationPair.getNativeId(), sourceVolumeURI, targetVolumeURI);
            _log.error(message, ex);
        }
    }


    /**
     * Return  replication group for the remote replication pair
     *
     * @param driverReplicationPair
     * @param sourceStorageSystem
     * @return
     */
    private com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup
                       getReplicationGroupForDriverPair(RemoteReplicationPair driverReplicationPair, StorageSystem sourceStorageSystem) {
        // natively managed systems, as VMAX, do not have nativeId set, we will use serial number for identity
        String systemNativeId = (sourceStorageSystem.getNativeId() == null)? sourceStorageSystem.getSerialNumber() : sourceStorageSystem.getNativeId();

        String groupNativeId = driverReplicationPair.getReplicationGroupNativeId();
        if (groupNativeId != null) {
            // replication group is specified in the pair
            String groupNativeGuid = NativeGUIDGenerator.generateRemoteReplicationGroupNativeGuid(sourceStorageSystem.getSystemType(),
                    systemNativeId, groupNativeId);

            com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup systemGroup =
                    ExternalDeviceDiscoveryUtils.checkRemoteReplicationGroupExistsInDB(groupNativeGuid, _dbClient);
            if (systemGroup == null) {
                String message = String.format("Cannot find replication group %s for replication pair %s in database",
                        groupNativeGuid, driverReplicationPair.getNativeId());
                _log.error(message);
                // this is error condition
                throw new RuntimeException(message);
            }
            return systemGroup;
        } else {
            // no group specified in the pair
            return null;
        }
    }

    /**
     * Return system replication set for driver replication pair
     *
     * @param driverReplicationPair
     * @param systemType
     * @return
     */
    private com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet
        getReplicationSetForDriverPair(RemoteReplicationPair driverReplicationPair, String systemType) {

        String setNativeId = driverReplicationPair.getReplicationSetNativeId();
        String setNativeGuid = NativeGUIDGenerator.generateRemoteReplicationSetNativeGuid(systemType, setNativeId);
        com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet systemSet =
                ExternalDeviceDiscoveryUtils.checkRemoteReplicationSetExistsInDB(setNativeGuid, _dbClient);
        if (systemSet == null) {
            String message = String.format("Cannot find replication set %s for replication pair %s in database",
                    setNativeGuid, driverReplicationPair.getNativeId());
            _log.error(message);
            // this is error condition
            throw new RuntimeException(message);
        }
        return systemSet;
    }
}
