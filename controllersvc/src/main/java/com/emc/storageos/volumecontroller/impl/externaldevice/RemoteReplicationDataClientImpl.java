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
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.volumecontroller.impl.plugins.ExternalDeviceDiscoveryUtils;

public class RemoteReplicationDataClientImpl implements RemoteReplicationDataClient {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationDataClientImpl.class);
    private DbClient _dbClient;

    @Override
    public void processRemoteReplicationSetsForStorageSystem(StorageSystem storageSystem, List<RemoteReplicationSet> replicationSets) {
        _log.info("processRemoteReplicationSetsForStorageSystem: processing sets for storage system {}  with nativeId {}, type {}",
                storageSystem.getId(), storageSystem.getNativeId(), storageSystem.getSystemType());

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

            _log.info("processRemoteReplicationGroupsForStorageSystem: processing groups for storage system {}, type {} - start",
                    storageSystem.getId(), storageSystem.getSystemType());

            List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup> systemRRGroups =
                    ExternalDeviceDiscoveryUtils.processDriverRRGroups(replicationGroups, storageSystem.getNativeId(), objectsToCreate, objectsToUpdate,
                            storageSystem.getSystemType(), _dbClient);

            List<URI> updatedObjectUrisList = URIUtil.toUris(objectsToUpdate);
            Set<URI> updatedObjectUris = new HashSet<URI>(updatedObjectUrisList);
            // find remote replication groups for this storage system which should be marked as not reachable
            // get all remote replication groups where this system is either source or target system
            List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup> rrGroupsForSystem =
                    queryActiveResourcesByRelation(_dbClient,  storageSystem.getId(), com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup.class,
                            "sourceSystem");

            List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup> rrGroupsForTargetSystem =
                    queryActiveResourcesByRelation(_dbClient, storageSystem.getId(), com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup.class,
                            "targetSystem");
            rrGroupsForSystem.addAll(rrGroupsForTargetSystem);
            _log.info("Found existing groups for storage system {}, groups {}", storageSystem.getNativeId(), rrGroupsForSystem);

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
                    storageSystem.getId(), storageSystem.getNativeGuid());
            _log.info(message);
        } catch (Exception e) {
            String message = String.format("Failed to process remote replication groups for storage system %s with nativeId %s. Error: %s",
                    storageSystem.getId(), storageSystem.getNativeGuid(), e.getMessage());
            _log.error(message, e);
            throw e;
        }
    }


    @Override
    public void createRemoteReplicationPair(RemoteReplicationPair replicationPair) throws DatabaseException {

    }

    @Override
    public void updateRemoteReplicationPair(RemoteReplicationPair replicationPair) throws DatabaseException {

    }

    @Override
    public void deleteRemoteReplicationPair(RemoteReplicationPair replicationPair) throws DatabaseException {

    }
}
