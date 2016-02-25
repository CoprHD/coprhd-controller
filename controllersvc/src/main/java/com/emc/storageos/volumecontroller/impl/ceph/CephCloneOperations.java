/*
 * Copyright (c) 2014 EMC Corporation
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
    public void createSingleClone(StorageSystem storageSystem, URI source, URI clone, Boolean createInactive,
            TaskCompleter taskCompleter) {
        _log.info("START createSingleClone operation");
        try {
        	Volume cloneVolume = _dbClient.queryObject(Volume.class, clone);
        	String cloneVolumeLabel = CephUtils.createNativeId(cloneVolume);

        	BlockObject sourceObject = BlockObject.fetch(_dbClient, source);
        	BlockSnapshot sourceSnapshot = null;
        	Volume parentVolume = null;
            if (sourceObject instanceof BlockSnapshot) {
            	sourceSnapshot = (BlockSnapshot)sourceObject;
            	parentVolume = _dbClient.queryObject(Volume.class, sourceSnapshot.getParent());
            } else if (sourceObject instanceof Volume) {
            	parentVolume = (Volume)sourceObject;
            	sourceSnapshot = prepareInternalSnapshotForVolume(parentVolume);
            } else {
            	String msg = String.format("Unsupported block object type URI %", parentVolume);
                ServiceCoded code = DeviceControllerErrors.ceph.operationFailed("createSingleClone", msg);
                taskCompleter.error(_dbClient, code);
                return;
            }

            StoragePool pool = _dbClient.queryObject(StoragePool.class, parentVolume.getPool());
        	String poolId = pool.getPoolName();
            String parentVolumeId = parentVolume.getNativeId();
            String snapshotId = sourceSnapshot.getNativeId();
            CephClient cephClient = getClient(storageSystem);

            // Create snapshot if do volume cloning
            if (snapshotId == null || snapshotId.isEmpty()) {
            	snapshotId = CephUtils.createNativeId(sourceSnapshot);
            	cephClient.createSnap(poolId, parentVolumeId, snapshotId);
            	sourceSnapshot.setNativeId(snapshotId);
            	sourceSnapshot.setDeviceLabel(snapshotId);
            	sourceSnapshot.setIsSyncActive(true);
            	sourceSnapshot.setParent(new NamedURI(parentVolume.getId(), parentVolume.getLabel()));
                _dbClient.updateObject(sourceSnapshot);
            }

            // Protect snap if needed
            if (!cephClient.snapIsProtected(poolId, parentVolumeId, snapshotId)) {
            	cephClient.protectSnap(poolId, parentVolumeId, snapshotId);
            }

            // Do cloning
            cephClient.cloneSnap(poolId, parentVolumeId, snapshotId, cloneVolumeLabel);

            // Update objects
            cloneVolume.setDeviceLabel(cloneVolumeLabel);
            cloneVolume.setNativeId(cloneVolumeLabel);
            cloneVolume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, cloneVolume));
            if (cloneVolume.getCapacity() == null || cloneVolume.getCapacity() == 0) {
            	// Set capacity equal to parent volume, in case of snapshot copying it's empty
            	cloneVolume.setCapacity(parentVolume.getCapacity());
            }
            cloneVolume.setProvisionedCapacity(cloneVolume.getCapacity());
            cloneVolume.setAllocatedCapacity(cloneVolume.getCapacity());
            cloneVolume.setInactive(createInactive);
            cloneVolume.setAssociatedSourceVolume(sourceSnapshot.getId());
            _dbClient.updateObject(cloneVolume);

            // Finish task
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
        	BlockObject obj = BlockObject.fetch(_dbClient, clone);
            if (obj != null) {
            	obj.setInactive(true);
                _dbClient.updateObject(obj);
            }
            _log.error("Encountered an exception", e);
            ServiceCoded code = DeviceControllerErrors.ceph.operationFailed("createSingleClone", e.getMessage());
            taskCompleter.error(_dbClient, code);
        }
        _log.info("END createSingleClone operation");
    }

    @Override
    public void detachSingleClone(StorageSystem storageSystem, URI cloneVolume, TaskCompleter taskCompleter) {
    	String opMsg = String.format("detachSingleClone %s", cloneVolume);
    	_log.info("START %s", opMsg);
        try {
            Volume clone = _dbClient.queryObject(Volume.class, cloneVolume);
            String cloneId = clone.getNativeId();
            StoragePool pool = _dbClient.queryObject(StoragePool.class, clone.getPool());
            String poolId = pool.getPoolName();
            BlockSnapshot parentSnapshot = _dbClient.queryObject(BlockSnapshot.class, clone.getAssociatedSourceVolume());
            String snapshotId = parentSnapshot.getNativeId();
            Volume sourceVolume = _dbClient.queryObject(Volume.class, parentSnapshot.getParent());
            String sourceVolumeId = sourceVolume.getNativeId();
            CephClient cephClient = getClient(storageSystem);

            // Flatten image
        	_log.info(String.format("%s: flatten image %s/%s", opMsg, poolId, cloneId));
            cephClient.flattenImage(poolId, cloneId);

            // Detach links
        	_log.info("%s: detach links", opMsg);
            ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(clone, _dbClient);
            clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
            clone.setReplicaState(ReplicationState.DETACHED.name());
            _dbClient.updateObject(clone);

            // Un-protect snap if it was last child and delete internal interim snapshot
            List<String> children = cephClient.getChildren(poolId, sourceVolumeId, snapshotId);
            if (children.isEmpty()) {
            	_log.info(String.format("%s: no childrent for snapshot %s@%s", opMsg, sourceVolumeId, snapshotId));
            	// Unprotect if protected
            	if (cephClient.snapIsProtected(poolId, sourceVolumeId, snapshotId)) {
                	_log.info(String.format("%s: unprotect snapshot %s@%s", opMsg, sourceVolumeId, snapshotId));
            		cephClient.unprotectSnap(poolId, sourceVolumeId, snapshotId);
            	}

                // Remove snapshot if it's internal object
                if (parentSnapshot.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                	_log.info(String.format("%s: interim snapshot %s@%s is to be deleted", opMsg, sourceVolumeId, snapshotId));
                	// Interim snapshot is created to 'clone volume from volume'
                	// and should be deleted at the step of detaching during full copy creation workflow
                	String reference = parentSnapshot.canBeDeleted();
                	if (reference == null) {
                    	_log.info(String.format("%s: delete snapshot %s@%s", opMsg, sourceVolumeId, snapshotId));
                		cephClient.deleteSnap(poolId, sourceVolumeId, snapshotId);
                		_dbClient.markForDeletion(parentSnapshot);
                	}
                }
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
        _log.info("END %s", opMsg);
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

    private CephClient getClient(StorageSystem storage) {
        return CephUtils.connectToCeph(_cephClientFactory, storage);
    }

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
