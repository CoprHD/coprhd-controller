/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.ceph;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.ceph.CephClient;
import com.emc.storageos.ceph.CephClientFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;


/**
 * Clone related operation for Ceph cluster
 *
 * See http://docs.ceph.com/docs/master/rbd/rbd-snapshot/#layering for clone feature details.
 *
 * The implementation is based on Ceph Hammer feature set.
 *
 */
public class CephCloneOperations implements CloneOperations {

    private static Logger _log = LoggerFactory.getLogger(CephCloneOperations.class);
    private DbClient _dbClient;
    private CephClientFactory _cephClientFactory;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCephClientFactory(CephClientFactory cephClientFactory) {
        _cephClientFactory = cephClientFactory;
    }

    @Override
    public void createSingleClone(StorageSystem storageSystem, URI source, URI cloneVolume, Boolean createInactive,
            TaskCompleter taskCompleter) {
        _log.info("START createSingleClone operation");
        try (CephClient cephClient = getClient(storageSystem)) {
        	Volume cloneObject = _dbClient.queryObject(Volume.class, cloneVolume);

        	BlockObject sourceObject = BlockObject.fetch(_dbClient, source);
        	BlockSnapshot sourceSnapshot = null;
        	Volume parentVolume = null;
            if (sourceObject instanceof BlockSnapshot) {
            	// Use source snapshot as clone source
                sourceSnapshot = (BlockSnapshot)sourceObject;
                parentVolume = _dbClient.queryObject(Volume.class, sourceSnapshot.getParent());
            } else if (sourceObject instanceof Volume) {
                // Use interim snapshot as clone source, since Ceph can clone snapshots only
                // http://docs.ceph.com/docs/master/rbd/rbd-snapshot/#getting-started-with-layering
                parentVolume = (Volume)sourceObject;
            	sourceSnapshot = prepareInternalSnapshotForVolume(parentVolume);
            } else {
                String msg = String.format("Unsupported block object type URI %s", source);
                ServiceCoded code = DeviceControllerErrors.ceph.operationFailed("createSingleClone", msg);
                taskCompleter.error(_dbClient, code);
                return;
            }

            StoragePool pool = _dbClient.queryObject(StoragePool.class, parentVolume.getPool());
            String poolId = pool.getPoolName();
            String parentVolumeId = parentVolume.getNativeId();
            String snapshotId = sourceSnapshot.getNativeId();
            String cloneId = null;

            try {
                if (snapshotId == null || snapshotId.isEmpty()) {
                    // Create Ceph snapshot of volume requested to clone
                	snapshotId = CephUtils.createNativeId(sourceSnapshot);
                	cephClient.createSnap(poolId, parentVolumeId, snapshotId);
                	sourceSnapshot.setNativeId(snapshotId);
                	sourceSnapshot.setDeviceLabel(snapshotId);
                	sourceSnapshot.setIsSyncActive(true);
                	sourceSnapshot.setParent(new NamedURI(parentVolume.getId(), parentVolume.getLabel()));
                    _dbClient.updateObject(sourceSnapshot);
                    _log.info("Interim shapshot {} created for clone {}", sourceSnapshot.getId(), cloneObject.getId());
                }

                // Ceph requires cloning snapshot to be protected (from deleting)
                if (!cephClient.snapIsProtected(poolId, parentVolumeId, snapshotId)) {
                	cephClient.protectSnap(poolId, parentVolumeId, snapshotId);
                }

                // Do cloning
                String cloneVolumeId = CephUtils.createNativeId(cloneObject);
                cephClient.cloneSnap(poolId, parentVolumeId, snapshotId, cloneVolumeId);
                cloneId = cloneVolumeId;

                // Update clone object
                cloneObject.setDeviceLabel(cloneId);
                cloneObject.setNativeId(cloneId);
                cloneObject.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, cloneObject));
                cloneObject.setProvisionedCapacity(parentVolume.getProvisionedCapacity());
                cloneObject.setAllocatedCapacity(parentVolume.getAllocatedCapacity());
                cloneObject.setAssociatedSourceVolume(sourceSnapshot.getId());
                _dbClient.updateObject(cloneObject);

                // Finish task
                taskCompleter.ready(_dbClient);
            } catch (Exception e) {
                // Clean up created objects
                cleanUpCloneObjects(cephClient, poolId, cloneId, snapshotId, parentVolumeId,
                        sourceSnapshot);
                throw e;
            }
        } catch (Exception e) {
        	BlockObject obj = BlockObject.fetch(_dbClient, cloneVolume);
            if (obj != null) {
            	obj.setInactive(true);
                _dbClient.updateObject(obj);
            }
            _log.error("Encountered an exception", e);
            ServiceCoded code = DeviceControllerErrors.ceph.operationFailed("createSingleClone", e.getMessage());
            taskCompleter.error(_dbClient, code);
        }
    }

    @Override
    public void detachSingleClone(StorageSystem storageSystem, URI cloneVolume, TaskCompleter taskCompleter) {
        _log.info("START detachSingleClone operation");
        try (CephClient cephClient = getClient(storageSystem)) {
            Volume cloneObject = _dbClient.queryObject(Volume.class, cloneVolume);
            String cloneId = cloneObject.getNativeId();
            StoragePool pool = _dbClient.queryObject(StoragePool.class, cloneObject.getPool());
            String poolId = pool.getPoolName();
            BlockSnapshot sourceSnapshot = _dbClient.queryObject(BlockSnapshot.class, cloneObject.getAssociatedSourceVolume());
            String snapshotId = sourceSnapshot.getNativeId();
            Volume parentVolume = _dbClient.queryObject(Volume.class, sourceSnapshot.getParent());
            String parentVolumeId = parentVolume.getNativeId();

            try {
                // Flatten image (detach Ceph volume from Ceph snapshot)
                // http://docs.ceph.com/docs/master/rbd/rbd-snapshot/#getting-started-with-layering
                cephClient.flattenImage(poolId, cloneId);

                // Detach links
                ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(cloneObject, _dbClient);
                cloneObject.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
                cloneObject.setReplicaState(ReplicationState.DETACHED.name());
                _dbClient.updateObject(cloneObject);

                // Un-protect snapshot if it was the last child and delete internal interim snapshot
                List<String> children = cephClient.getChildren(poolId, parentVolumeId, snapshotId);
                if (children.isEmpty()) {
                	// Unprotect snapshot to enable deleting
                	if (cephClient.snapIsProtected(poolId, parentVolumeId, snapshotId)) {
                		cephClient.unprotectSnap(poolId, parentVolumeId, snapshotId);
                	}

                    // Interim snapshot is created to 'clone volume from volume' only
                    // and should be deleted at the step of detaching during full copy creation workflow
                    if (sourceSnapshot.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                		cephClient.deleteSnap(poolId, parentVolumeId, snapshotId);
                		// Set to null to prevent handling in cleanUpCloneObjects
                		snapshotId = null;
                		_dbClient.markForDeletion(sourceSnapshot);
                    }
                } else if (sourceSnapshot.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                    // If the snapshot (not interim) still has children, it may be used for another cloning right now
                    // So that log the warning for interim snapshot only
                    _log.warn("Could not delete interim snapshot {} because its Ceph snapshot {}@{} unexpectedly had another child",
                            sourceSnapshot.getId(), parentVolumeId, snapshotId);
                }

                taskCompleter.ready(_dbClient);
            } catch (Exception e) {
                // Although detachSingleClone may be again called on error, it is better to remove objects now.
                cleanUpCloneObjects(cephClient, poolId, cloneId, snapshotId, parentVolumeId, sourceSnapshot);
                throw e;
            }
	    } catch (Exception e) {
        	BlockObject obj = BlockObject.fetch(_dbClient, cloneVolume);
            if (obj != null) {
            	obj.setInactive(true);
                _dbClient.updateObject(obj);
            }
	        _log.error("Encountered an exception", e);
	        ServiceCoded code = DeviceControllerErrors.ceph.operationFailed("detachSingleClone", e.getMessage());
	        taskCompleter.error(_dbClient, code);
	    }
    }

    @Override
    public void activateSingleClone(StorageSystem storageSystem, URI fullCopy, TaskCompleter taskCompleter) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void restoreFromSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void fractureSingleClone(StorageSystem storageSystem, URI sourceVolume,
            URI clone, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void resyncSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void createGroupClone(StorageSystem storage, List<URI> cloneList,
            Boolean createInactive, TaskCompleter taskCompleter) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void activateGroupClones(StorageSystem storage, List<URI> clone, TaskCompleter taskCompleter) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void restoreGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void fractureGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void resyncGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void detachGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void establishVolumeCloneGroupRelation(StorageSystem storage, URI sourceVolume, URI clone, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Get a client object to communicate with Ceph cluster referenced by given Storage System
     *
     * @param storage [in] - Storage System object
     * @return CephClient object
     */
    private CephClient getClient(StorageSystem storage) {
        return CephUtils.connectToCeph(_cephClientFactory, storage);
    }

    /**
     * Generate BlockSnapshot object to store info about interim snapshot used to clone given volume.
     * The object is created on createSingleClone, and is deleted on detachSingleClone,
     * where the stored info is used. Corresponding Ceph snapshot is created and deleted simultaneously.
     *
     * @param volume [in] Volume object
     * @return generated BlockSnapshot object
     */
    private BlockSnapshot prepareInternalSnapshotForVolume(Volume volume) {
        BlockSnapshot snapshot = new BlockSnapshot();
        snapshot.setId(URIUtil.createId(BlockSnapshot.class));
        snapshot.setLabel(String.format("temp-for-cloning-%s", UUID.randomUUID().toString()));
        snapshot.setSourceNativeId(CephUtils.createNativeId(snapshot));
        snapshot.setParent(new NamedURI(volume.getId(), volume.getLabel()));
        snapshot.setStorageController(volume.getStorageController());
        snapshot.setSystemType(volume.getSystemType());
        snapshot.setVirtualArray(volume.getVirtualArray());
        snapshot.setProtocol(new StringSet());
        snapshot.getProtocol().addAll(volume.getProtocol());
        snapshot.setProject(new NamedURI(volume.getProject().getURI(), volume.getProject().getName()));
        snapshot.setSnapsetLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(snapshot.getLabel(),
                SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));
        // Since this BlockSnapshot object is interim, it is hidden from users with INTERNAL_OBJECT flag
        snapshot.addInternalFlags(Flag.INTERNAL_OBJECT);
        return snapshot;
    }

    /**
     * Safely remove transient snapshot with dependencies.
     * Intended for cleaning up on error of clone operations to prevent invisible transient snapshot to block
     * source volume deletion.
     *
     * @param cephClient [in] Ceph Client object
     * @param poolId [in] Ceph pool name
     * @param cloneVolumeId [in] Ceph volume name of clone
     * @param snapshotId [in] Ceph snapshot name of snapshot used to clone (transient or permanent)
     * @param sourceVolumeId [in] Ceph volume name of source volume, which owns the snapshot
     * @param snapshot [in] Transient BlockSnapshot object
     */
    private void cleanUpCloneObjects(CephClient cephClient, String poolId, String cloneVolumeId, String snapshotId,
            String sourceVolumeId, BlockSnapshot snapshot) {
        try {
            if (cloneVolumeId != null) {
                cephClient.deleteImage(poolId, cloneVolumeId);
            }
            if (snapshotId != null) {
                List<String> children = cephClient.getChildren(poolId, sourceVolumeId, snapshotId);
                if (children.isEmpty()) {
                    if (cephClient.snapIsProtected(poolId, sourceVolumeId, snapshotId)) {
                        cephClient.unprotectSnap(poolId, sourceVolumeId, snapshotId);
                    }
                    if (snapshot != null && snapshot.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                        cephClient.deleteSnap(poolId, sourceVolumeId, snapshotId);
                    }
                }
            }
        } catch (Exception e) {
            _log.error(String.format("Could not clean up volumes %s, %s, interim snapshot %s from Ceph pool %s, "
                    + "handling exception of a clone operation",
                    cloneVolumeId, sourceVolumeId, snapshotId, poolId), e);
        }
        try {
            if (snapshot != null && snapshot.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                _dbClient.markForDeletion(snapshot);
            }
        } catch (Exception e) {
            _log.error(String.format("Could not clean up interim snapshot %s, "
                    + "handling exception of a clone operation",
                    snapshot.getId()), e);
        }
    }

}
