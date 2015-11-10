/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.model.HDSHost;
import com.emc.storageos.hds.model.Pool;
import com.emc.storageos.hds.model.ReplicationInfo;
import com.emc.storageos.hds.model.SnapshotGroup;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.DefaultSnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;

public class HDSSnapshotOperations extends DefaultSnapshotOperations {

    private static final Logger log = LoggerFactory.getLogger(HDSSnapshotOperations.class);
    private DbClient dbClient;
    private HDSApiFactory hdsApiFactory;
    private HDSProtectionOperations hdsProtectionOperations;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    public void setHdsProtectionOperations(
            HDSProtectionOperations hdsProtectionOperations) {
        this.hdsProtectionOperations = hdsProtectionOperations;
    }

    /**
     * Creates ThinImage instance on HDS.
     * 1. Find pair management server.
     * 2. Find ViPR-Snapshot-Group instance from storage system
     * 3. Find ThinImage pool.
     * 4. Create Snapshot instance on ThinImage Pool.
     * 5. Add DummyLunPath into Snapshot.
     * 6. Create ThinImage pair
     */
    @Override
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("Create Single Volume Snapshot Started");

        boolean isSnapshotCreated = false, isDummyLunPathAdded = false;
        HDSApiClient hdsApiClient = null;
        HDSHost pairMgmtServer = null;
        try {
            hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storage), storage.getSmisUserName(),
                    storage.getSmisPassword());
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            log.info("createSingleVolumeSnapshot operation START");
            Volume volume = dbClient.queryObject(Volume.class, snapshotObj.getParent());

            pairMgmtServer = hdsApiClient.getSnapshotGroupPairManagementServer(storage.getSerialNumber());

            if (pairMgmtServer == null) {
                log.error("Unable to find snapshot group information/pair management server for Thin Image");
                throw HDSException.exceptions.snapshotGroupNotAvailable(storage.getNativeGuid());
            }
            String systemObjectId = HDSUtils.getSystemObjectID(storage);
            log.debug("StorageSystem Object Id :{}", systemObjectId);
            List<Pool> thinImagePoolList = hdsApiClient.getThinImagePoolList(systemObjectId);
            if (thinImagePoolList == null || thinImagePoolList.isEmpty()) {
                log.error("ThinImage Pool is not available on Storage System :{}", storage.getNativeGuid());
                throw HDSException.exceptions.thinImagePoolNotAvailable(storage.getNativeGuid());
            }

            Pool selectedThinImagePool = selectThinImagePoolForPlacement(thinImagePoolList, snapshotObj);

            if (selectedThinImagePool == null) {
                log.error("No ThinImage Pool is having enough free capcity to create snapshot on storage system :{}",
                        storage.getNativeGuid());
                throw HDSException.exceptions.notEnoughFreeCapacityOnthinImagePool(storage.getNativeGuid());
            }

            // Create snapshot volume

            hdsProtectionOperations.createSecondaryVolumeForSnapshot(storage, volume, snapshotObj);
            isSnapshotCreated = true;

            snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);

            // Add Dummy lun path

            hdsProtectionOperations.addDummyLunPath(hdsApiClient, snapshotObj);
            isDummyLunPathAdded = true;
            String snapShotGrpId = getViPRSnapshotGroup(pairMgmtServer, storage.getSerialNumber()).getObjectID();
            // Create Thin Image pair
            hdsApiClient.createThinImagePair(snapShotGrpId, pairMgmtServer.getObjectID(), volume.getNativeId(),
                    snapshotObj.getNativeId(), selectedThinImagePool.getPoolID(), storage.getModel());

            taskCompleter.ready(dbClient);

        } catch (Exception e) {
            try {
                rollbackMethodForCreateSnapshot(isSnapshotCreated, isDummyLunPathAdded, hdsApiClient, storage, snapshot);
            } catch (Exception e1) {
                log.error("Exception occured while roll back snap creation", e1);
            }
            String errorMsg = String.format(CREATE_ERROR_MSG_FORMAT, snapshot);
            log.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("createSingleVolumeSnapshot",
                    e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }

        log.info("Create Single Volume Snapshot Completed");
    }

    /**
     * Roll back method to clean up stale snapshot volume on storage system
     * 
     * @param isSnapshotCreated
     * @param isDummyLunPathAdded
     * @param hdsApiClient
     * @param storage
     * @param snapshot
     * @throws Exception
     */
    private void rollbackMethodForCreateSnapshot(boolean isSnapshotCreated,
            boolean isDummyLunPathAdded, HDSApiClient hdsApiClient, StorageSystem storage, URI snapshot) throws Exception {

        if (isDummyLunPathAdded) {
            log.info("Remove dummy path while doing roll back");
            // Remove dummy lun path
            hdsProtectionOperations.removeDummyLunPath(storage, snapshot);
        }
        if (isSnapshotCreated) {
            log.info("Remove snapshot volume for roll back");
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            // Delete snapshot vollume
            String systemObjectID = HDSUtils.getSystemObjectID(storage);
            String logicalUnitObjId = HDSUtils.getLogicalUnitObjectId(snapshotObj.getNativeId(), storage);
            hdsApiClient.deleteSnapshotVolume(systemObjectID, logicalUnitObjId, storage.getModel());
        }

    }

    private SnapshotGroup getViPRSnapshotGroup(HDSHost pairMgmtServer, String systemSerialNumber) {
        SnapshotGroup snapShotGrp = null;

        if (pairMgmtServer != null && pairMgmtServer.getSnapshotGroupList() != null) {
            for (SnapshotGroup snapshotGroup : pairMgmtServer.getSnapshotGroupList()) {
                if (snapshotGroup != null &&
                        HDSConstants.VIPR_SNAPSHOT_GROUP_NAME.equalsIgnoreCase(snapshotGroup.getGroupName())
                        && systemSerialNumber.equalsIgnoreCase(snapshotGroup.getSerialNumber())) {
                    snapShotGrp = snapshotGroup;
                    log.info("Snapshot Group Id :{}", snapShotGrp.getObjectID());
                    break;
                }
            }
        }
        return snapShotGrp;
    }

    private Pool selectThinImagePoolForPlacement(List<Pool> thinImagePoolList, BlockSnapshot snapshot) {
        Pool selectedPool = null;
        for (Pool pool : thinImagePoolList) {
            if (pool.getFreeCapacity() >= snapshot.getAllocatedCapacity()) {
                selectedPool = pool;
                log.info("ThinImage Pool {} has enough space to create snapshot", pool.getObjectID());
                break;
            }
        }
        return selectedPool;
    }

    /**
     * Wrapper for setting the BlockSnapshot.inactive value
     * 
     * @param snapshotURI [in] - BlockSnapshot object to update
     * @param value [in] - Value to assign to inactive
     */
    protected void setInactive(URI snapshotURI, boolean value) {
        try {
            if (snapshotURI != null) {
                BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotURI);
                snapshot.setInactive(value);
                dbClient.persistObject(snapshot);
            }
        } catch (DatabaseException e) {
            log.error("IOException when trying to update snapshot.inactive value", e);
        }
    }

    /**
     * Wrapper for setting the BlockSnapshot.inactive value
     * 
     * @param snapshotURIs [in] - List of BlockSnapshot objects to update
     * @param value [in] - Value to assign to inactive
     */
    protected void setInactive(List<URI> snapshotURIs, boolean value) {
        try {
            if (snapshotURIs != null) {
                for (URI uri : snapshotURIs) {
                    BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, uri);
                    snapshot.setInactive(value);
                    dbClient.persistObject(snapshot);
                }
            }
        } catch (DatabaseException e) {
            log.error("IOException when trying to update snapshot.inactive value", e);
        }
    }

    /**
     * 1. Delete ThinImage Pair
     * 2. Delete Dummy lun path from snap volume
     * 3. Delete Snapshot
     */
    @Override
    public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {

        log.info("Delete Single Volume Snapshot Started");
        try {
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            log.info("deleteSingleVolumeSnapshot operation START");

            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storage), storage.getSmisUserName(),
                    storage.getSmisPassword());

            // Get pair management server
            HDSHost pairMgmtServer = hdsApiClient.getSnapshotGroupPairManagementServer(storage.getSerialNumber());

            if (null == pairMgmtServer) {
                log.error("Unable to find snapshot group information/pair management server for Thin Image");
                throw HDSException.exceptions.snapshotGroupNotAvailable(storage.getNativeGuid());
            }

            // Get snapshot group id
            SnapshotGroup snapshotGroup = getViPRSnapshotGroup(pairMgmtServer, storage.getSerialNumber());
            String snapShotGrpId = snapshotGroup.getObjectID();

            // Get replication object ids
            Volume volume = dbClient.queryObject(Volume.class, snapshotObj.getParent());
            ReplicationInfo replicationInfo = getReplicationInfo(snapshotGroup, volume.getNativeId(), snapshotObj.getNativeId());
            if (replicationInfo != null) {
                String replicationInfoObjId = replicationInfo.getObjectID();

                // Delete ThinImage pair between volume and snapshot
                hdsApiClient.deleteThinImagePair(pairMgmtServer.getObjectID(), snapShotGrpId, replicationInfoObjId, storage.getModel());
            } else {
                log.info("Pair has been deleted already on storage system");
            }

            // Remove dummy lun path
            hdsProtectionOperations.removeDummyLunPath(storage, snapshot);

            // Delete snapshot vollume
            hdsProtectionOperations.deleteSecondaryVolumeSnapshot(storage, snapshotObj, taskCompleter);
            log.info("Delete Single Volume Snapshot Completed");
        } catch (Exception e) {
            String errorMsg = String.format(DELETE_ERROR_MSG_FORMAT, snapshot);
            log.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("deleteSingleVolumeSnapshot",
                    e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
    }

    /**
     * 1. Find pair management server.
     * 2. Get SnapshotGroup's Object Id.
     * 3. Get ReplicationInfo's Object Id.
     * 4. Perform ReplicationInfo Restore operation.
     */
    @Override
    public void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume,
            URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            BlockSnapshot from = dbClient.queryObject(BlockSnapshot.class, snapshot);
            Volume to = dbClient.queryObject(Volume.class, volume);
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storage), storage.getSmisUserName(),
                    storage.getSmisPassword());
            HDSHost pairMgmtServer = hdsApiClient.getSnapshotGroupPairManagementServer(storage.getSerialNumber());

            if (pairMgmtServer == null) {
                log.error("Unable to find snapshot group information/pair management server for Thin Image");
                throw HDSException.exceptions.snapshotGroupNotAvailable(storage.getNativeGuid());
            }
            SnapshotGroup snapShotGrp = getViPRSnapshotGroup(pairMgmtServer, storage.getSerialNumber());
            log.debug("to.getNativeId() :{}", to.getNativeId());
            log.debug("from.getNativeId() :{}", from.getNativeId());

            ReplicationInfo repInfo = getReplicationInfo(snapShotGrp, to.getNativeId(), from.getNativeId());
            hdsApiClient.restoreThinImagePair(pairMgmtServer.getObjectID(), snapShotGrp.getObjectID(), repInfo.getObjectID(),
                    storage.getModel());
            taskCompleter.ready(dbClient);
            log.info("Restore Snapshot volume completed");
        } catch (Exception e) {
            String message = String.format("Generic exception when trying to restore from snapshot %s on array %s",
                    snapshot.toString(), storage.getSerialNumber());
            log.error(message, e);
            ServiceError error = DeviceControllerErrors.hds.methodFailed("restoreSingleVolumeSnapshot", e.getMessage());
            taskCompleter.error(dbClient, error);
        }
    }

    /**
     * Get replicationInfo object for the given pvol and svol from SnapshotGroup instance.
     */
    private ReplicationInfo getReplicationInfo(SnapshotGroup snapShotGrp,
            String pvoldevnum, String svoldevnum) {

        ReplicationInfo repInfo = null;
        if (snapShotGrp != null && snapShotGrp.getReplicationInfoList() != null) {
            log.info("rep list size :{}", snapShotGrp.getReplicationInfoList().size());
            for (ReplicationInfo replicationInfo : snapShotGrp.getReplicationInfoList()) {
                if (null != replicationInfo) {
                    log.debug("Rep Info :{}", replicationInfo.toXMLString());
                    if (pvoldevnum.equals(replicationInfo.getPvolDevNum())
                            && svoldevnum.equals(replicationInfo.getSvolDevNum())) {
                        log.info("Found replication info object :{}", replicationInfo.getObjectID());
                        repInfo = replicationInfo;
                        break;
                    }
                }
            }
        }
        return repInfo;
    }

    @Override
    public void establishVolumeSnapshotGroupRelation(StorageSystem storage, URI sourceVolume,
            URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}
