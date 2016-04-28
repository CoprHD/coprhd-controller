/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.ceph;

import java.net.URI;
import java.util.List;

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
        try {
        	Volume cloneObject = _dbClient.queryObject(Volume.class, cloneVolume);
        	String cloneVolumeLabel = CephUtils.createNativeId(cloneObject);

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
            CephClient cephClient = getClient(storageSystem);

            // Create Ceph snapshot of volume requested to clone
            if (snapshotId == null || snapshotId.isEmpty()) {
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
            cephClient.cloneSnap(poolId, parentVolumeId, snapshotId, cloneVolumeLabel);

            // Update clone object
            cloneObject.setDeviceLabel(cloneVolumeLabel);
            cloneObject.setNativeId(cloneVolumeLabel);
            cloneObject.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, cloneObject));
            cloneObject.setProvisionedCapacity(parentVolume.getProvisionedCapacity());
            cloneObject.setAllocatedCapacity(parentVolume.getAllocatedCapacity());
            cloneObject.setAssociatedSourceVolume(sourceSnapshot.getId());
            _dbClient.updateObject(cloneObject);

            // Finish task
            taskCompleter.ready(_dbClient);
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
        try {
            Volume cloneObject = _dbClient.queryObject(Volume.class, cloneVolume);
            String cloneId = cloneObject.getNativeId();
            StoragePool pool = _dbClient.queryObject(StoragePool.class, cloneObject.getPool());
            String poolId = pool.getPoolName();
            BlockSnapshot parentSnapshot = _dbClient.queryObject(BlockSnapshot.class, cloneObject.getAssociatedSourceVolume());
            String snapshotId = parentSnapshot.getNativeId();
            Volume sourceVolume = _dbClient.queryObject(Volume.class, parentSnapshot.getParent());
            String sourceVolumeId = sourceVolume.getNativeId();
            CephClient cephClient = getClient(storageSystem);

            // Flatten image
            cephClient.flattenImage(poolId, cloneId);

            // Detach links
            ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(cloneObject, _dbClient);
            cloneObject.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
            cloneObject.setReplicaState(ReplicationState.DETACHED.name());
            _dbClient.updateObject(cloneObject);

            // Un-protect snapshot if it was the last child and delete internal interim snapshot
            List<String> children = cephClient.getChildren(poolId, sourceVolumeId, snapshotId);
            if (children.isEmpty()) {
            	// Unprotect snapshot to enable deleting
            	if (cephClient.snapIsProtected(poolId, sourceVolumeId, snapshotId)) {
            		cephClient.unprotectSnap(poolId, sourceVolumeId, snapshotId);
            	}

                // Interim snapshot is created to 'clone volume from volume' only
                // and should be deleted at the step of detaching during full copy creation workflow
                if (parentSnapshot.checkInternalFlags(Flag.INTERNAL_OBJECT) &&
                        parentSnapshot.canBeDeleted() == null) {
            		cephClient.deleteSnap(poolId, sourceVolumeId, snapshotId);
            		_dbClient.markForDeletion(parentSnapshot);
                }
            } else if (parentSnapshot.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                // If the snapshot (not interim) still has children, it may be used for another cloning right now
                // So that log the warning for interim snapshot only
                _log.warn("Could not delete interim snapshot {} because its Ceph snapshot {}@{} unexpectedly had another child",
                        parentSnapshot.getId(), sourceVolumeId, snapshotId);
            }

            taskCompleter.ready(_dbClient);
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
        // no support
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
        // no support
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
        // no support
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
     * Generate BlockSnapshot object to store info about interim snapshot used to clone given volume
     *
     * @param volume [in] Volume object
     * @return generated BlockSnapshot object
     */
    private BlockSnapshot prepareInternalSnapshotForVolume(Volume volume) {
        BlockSnapshot snapshot = new BlockSnapshot();
        snapshot.setId(URIUtil.createId(BlockSnapshot.class));
        URI cgUri = volume.getConsistencyGroup();
        if (cgUri != null) {
            snapshot.setConsistencyGroup(cgUri);
        }
        snapshot.setSourceNativeId(volume.getNativeId());
        snapshot.setParent(new NamedURI(volume.getId(), volume.getLabel()));
        snapshot.setLabel(String.format("temp-for-cloning-%s", snapshot.getId()));
        snapshot.setStorageController(volume.getStorageController());
        snapshot.setVirtualArray(volume.getVirtualArray());
        snapshot.setProtocol(new StringSet());
        snapshot.getProtocol().addAll(volume.getProtocol());
        snapshot.setProject(new NamedURI(volume.getProject().getURI(), volume.getProject().getName()));
        snapshot.setSnapsetLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(snapshot.getLabel(), SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));
        snapshot.addInternalFlags(Flag.INTERNAL_OBJECT);
        return snapshot;
    }

}
