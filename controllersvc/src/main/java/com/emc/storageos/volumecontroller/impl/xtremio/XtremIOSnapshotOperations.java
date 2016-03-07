/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyGroupUtils;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.XtremIOConstants.XTREMIO_ENTITY_TYPE;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;

public class XtremIOSnapshotOperations extends XtremIOOperations implements SnapshotOperations {

    private static final Logger _log = LoggerFactory.getLogger(XtremIOSnapshotOperations.class);

    private NameGenerator nameGenerator;

    public void setNameGenerator(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    @Override
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter) throws DeviceControllerException {

        BlockSnapshot snap = dbClient.queryObject(BlockSnapshot.class, snapshot);
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            String generatedLabel = nameGenerator.generate("", snap.getLabel(), "",
                    '_', XtremIOConstants.XTREMIO_MAX_VOL_LENGTH);
            snap.setLabel(generatedLabel);
            XtremIOVolume createdSnap;
            if (client.isVersion2()) {
                createdSnap = createV2Snapshot(client, storage, xioClusterName, snap, generatedLabel, readOnly, taskCompleter);
            } else {
                createdSnap = createV1Snapshot(client, storage, snap, generatedLabel, readOnly, taskCompleter);
            }

            if (createdSnap != null) {
                processSnapshot(createdSnap, snap, storage);
            }

            dbClient.updateObject(snap);
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot creation failed", e);
            snap.setInactive(true);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    private XtremIOVolume createV1Snapshot(XtremIOClient client, StorageSystem storage, BlockSnapshot snap, String snapLabel,
            Boolean readOnly, TaskCompleter taskCompleter) throws Exception {
        Volume parentVolume = dbClient.queryObject(Volume.class, snap.getParent().getURI());
        URI projectUri = snap.getProject().getURI();
        String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

        String snapFolderName = XtremIOProvUtils.createFoldersForVolumeAndSnaps(client, getVolumeFolderName(projectUri, storage))
                .get(XtremIOConstants.SNAPSHOT_KEY);
        String snapshotType = readOnly ? XtremIOConstants.XTREMIO_READ_ONLY_TYPE : XtremIOConstants.XTREMIO_REGULAR_TYPE;

        client.createVolumeSnapshot(parentVolume.getLabel(), snapLabel, snapFolderName, snapshotType, clusterName);
        XtremIOVolume createdSnap = client.getSnapShotDetails(snap.getLabel(), clusterName);

        return createdSnap;
    }

    private XtremIOVolume createV2Snapshot(XtremIOClient client, StorageSystem storage, String xioClusterName, BlockSnapshot snap,
            String snapLabel, Boolean readOnly, TaskCompleter taskCompleter) throws Exception {
        Volume parentVolume = dbClient.queryObject(Volume.class, snap.getParent().getURI());
        URI projectUri = snap.getProject().getURI();
        String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

        String snapTagName = XtremIOProvUtils.createTagsForVolumeAndSnaps(client, getVolumeFolderName(projectUri, storage), clusterName)
                .get(XtremIOConstants.SNAPSHOT_KEY);
        String snapshotType = readOnly ? XtremIOConstants.XTREMIO_READ_ONLY_TYPE : XtremIOConstants.XTREMIO_REGULAR_TYPE;
        client.createVolumeSnapshot(parentVolume.getLabel(), snapLabel, snapTagName, snapshotType, clusterName);
        // Get the snapset details
        XtremIOConsistencyGroup snapset = client.getSnapshotSetDetails(snapLabel, xioClusterName);
        List<Object> snapDetails = snapset.getVolList().get(0);
        XtremIOVolume xioSnap = client.getSnapShotDetails(snapDetails.get(1).toString(), xioClusterName);
        // tag the created the snap
        client.tagObject(snapTagName, XTREMIO_ENTITY_TYPE.SnapshotSet.name(), snapLabel, clusterName);
        return xioSnap;

    }

    @Override
    public void createGroupSnapshots(StorageSystem storage, List<URI> snapshotList, Boolean createInactive,
            Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {

        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            List<BlockSnapshot> snapObjs = dbClient.queryObject(BlockSnapshot.class, snapshotList);
            BlockSnapshot snapshotObj = snapObjs.get(0);
            BlockConsistencyGroup blockCG = dbClient.queryObject(BlockConsistencyGroup.class, snapshotObj.getConsistencyGroup());

            // Check if the CG for which we are creating snapshot exists on the array
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            String snapsetLabel = snapshotObj.getSnapsetLabel();
            String cgName = ConsistencyGroupUtils.getSourceConsistencyGroupName(snapshotObj, dbClient);
            XtremIOConsistencyGroup cg = XtremIOProvUtils.isCGAvailableInArray(client, cgName, clusterName);
            if (cg == null) {
                _log.error("The consistency group does not exist in the array: {}", storage.getSerialNumber());
                taskCompleter.error(dbClient, DeviceControllerException.exceptions
                        .consistencyGroupNotFound(cgName,
                                blockCG.getCgNameOnStorageSystem(storage.getId())));
                return;
            }

            String snapType = readOnly ? XtremIOConstants.XTREMIO_READ_ONLY_TYPE : XtremIOConstants.XTREMIO_REGULAR_TYPE;
            String snapshotSetTagName = XtremIOProvUtils.createTagsForVolumeAndSnaps(client,
                    getVolumeFolderName(snapshotObj.getProject().getURI(), storage), clusterName)
                    .get(XtremIOConstants.SNAPSHOT_KEY);
            client.createConsistencyGroupSnapshot(cgName, snapsetLabel, "", snapType, clusterName);
            // tag the created the snapshotSet
            client.tagObject(snapshotSetTagName, XTREMIO_ENTITY_TYPE.SnapshotSet.name(), snapsetLabel, clusterName);

            _log.info("Snapset label :{}", snapsetLabel);
            // Create mapping of volume.deviceLabel to BlockSnapshot object
            Map<String, BlockSnapshot> volumeToSnapMap = new HashMap<String, BlockSnapshot>();
            for (BlockSnapshot snapshot : snapObjs) {
                Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());
                volumeToSnapMap.put(volume.getDeviceLabel(), snapshot);
            }

            // Get the snapset details
            XtremIOConsistencyGroup snapset = client.getSnapshotSetDetails(snapsetLabel, clusterName);
            for (List<Object> snapDetails : snapset.getVolList()) {
                XtremIOVolume xioSnap = client.getSnapShotDetails(snapDetails.get(1).toString(), clusterName);
                _log.info("XIO Snap : {}", xioSnap);
                BlockSnapshot snapshot = volumeToSnapMap.get(xioSnap.getAncestoVolInfo().get(1));
                processSnapshot(xioSnap, snapshot, storage);
                snapshot.setReplicationGroupInstance(snapsetLabel);
                dbClient.updateObject(snapshot);
            }
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot creation failed", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    private void processSnapshot(XtremIOVolume xioSnap, BlockSnapshot snapObj, StorageSystem storage) {
        snapObj.setWWN(xioSnap.getVolInfo().get(0));
        // if created snap wwn is not empty then update the wwn
        if (!xioSnap.getWwn().isEmpty()) {
            snapObj.setWWN(xioSnap.getWwn());
        }
        snapObj.setDeviceLabel(xioSnap.getVolInfo().get(1));
        snapObj.setNativeId(xioSnap.getVolInfo().get(0));
        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(storage, snapObj);
        snapObj.setNativeGuid(nativeGuid);
        boolean isReadOnly = XtremIOConstants.XTREMIO_READ_ONLY_TYPE.equals(xioSnap.getSnapshotType()) ? Boolean.TRUE : Boolean.FALSE;
        snapObj.setIsReadOnly(isReadOnly);
        snapObj.setIsSyncActive(true);
        snapObj.setProvisionedCapacity(Long.parseLong(xioSnap.getAllocatedCapacity()) * 1024);
        snapObj.setAllocatedCapacity(Long.parseLong(xioSnap.getAllocatedCapacity()) * 1024);
    }

    @Override
    public void activateSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void activateGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            if (null != XtremIOProvUtils.isSnapAvailableInArray(client, snapshotObj.getDeviceLabel(), clusterName)) {
                client.deleteSnapshot(snapshotObj.getDeviceLabel(), clusterName);
            }
            snapshotObj.setIsSyncActive(false);
            snapshotObj.setInactive(true);
            dbClient.updateObject(snapshotObj);
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot deletion failed", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    @Override
    public void deleteGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            // We should use snapsetLabel to get the snapset name because in case of ingested snaps, the replicationGroupInstance
            // will be populated with the CG name corresponding to the snapset.
            String snapsetName = snapshotObj.getSnapsetLabel();
            if (null != XtremIOProvUtils.isSnapsetAvailableInArray(client, snapsetName, clusterName)) {
                client.deleteSnapshotSet(snapsetName, clusterName);
            }
            // Set inactive=true for all snapshots in the snap
            List<BlockSnapshot> snapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshotObj, dbClient);
            for (BlockSnapshot snap : snapshots) {
                snap.setIsSyncActive(false);
                snap.setInactive(true);
                dbClient.updateObject(snap);
            }
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot deletion failed", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    @Override
    public void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            Volume sourceVol = dbClient.queryObject(Volume.class, snapshotObj.getParent());
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            client.restoreVolumeFromSnapshot(clusterName, sourceVol.getLabel(), snapshotObj.getDeviceLabel());
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot restore failed", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    @Override
    public void restoreGroupSnapshots(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            BlockConsistencyGroup group = dbClient.queryObject(BlockConsistencyGroup.class, snapshotObj.getConsistencyGroup());

            // Check if the CG exists on the array
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            String cgName = ConsistencyGroupUtils.getSourceConsistencyGroupName(snapshotObj, dbClient);
            XtremIOConsistencyGroup cg = XtremIOProvUtils.isCGAvailableInArray(client, cgName, clusterName);
            if (cg == null) {
                _log.error("The consistency group does not exist in the array: {}", storage.getSerialNumber());
                taskCompleter.error(dbClient, DeviceControllerException.exceptions
                        .consistencyGroupNotFound(cgName,
                                group.getCgNameOnStorageSystem(storage.getId())));
                return;
            }
            // We should use snapsetLabel to get the snapset name because in case of ingested snaps, the replicationGroupInstance
            // will be populated with the CG name corresponding to the snapset.
            client.restoreCGFromSnapshot(clusterName, cgName, snapshotObj.getSnapsetLabel());
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot restore failed", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void resyncSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            Volume sourceVol = dbClient.queryObject(Volume.class, snapshotObj.getParent());
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            client.refreshSnapshotFromVolume(clusterName, sourceVol.getLabel(), snapshotObj.getDeviceLabel());
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot resync failed", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    @Override
    public void resyncGroupSnapshots(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            BlockConsistencyGroup group = dbClient.queryObject(BlockConsistencyGroup.class, snapshotObj.getConsistencyGroup());

            // Check if the CG exists on the array
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            String cgName = ConsistencyGroupUtils.getSourceConsistencyGroupName(snapshotObj, dbClient);
            XtremIOConsistencyGroup cg = XtremIOProvUtils.isCGAvailableInArray(client, cgName, clusterName);
            if (cg == null) {
                _log.error("The consistency group does not exist in the array: {}", storage.getSerialNumber());
                taskCompleter.error(dbClient, DeviceControllerException.exceptions
                        .consistencyGroupNotFound(cgName,
                                group.getCgNameOnStorageSystem(storage.getId())));
                return;
            }
            // We should use snapsetLabel to get the snapset name because in case of ingested snaps, the replicationGroupInstance
            // will be populated with the CG name corresponding to the snapset.
            client.refreshSnapshotFromCG(clusterName, cgName, snapshotObj.getSnapsetLabel());
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot resync failed", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    @Override
    public void copySnapshotToTarget(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void copyGroupSnapshotsToTarget(StorageSystem storage, List<URI> snapshotList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void terminateAnyRestoreSessions(StorageSystem storage, BlockObject from, URI volume, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    private String getVolumeFolderName(URI projectURI, StorageSystem storage) {
        String volumeGroupFolderName = "";
        Project project = dbClient.queryObject(Project.class, projectURI);
        DataSource dataSource = dataSourceFactory.createXtremIOVolumeFolderNameDataSource(project, storage);
        volumeGroupFolderName = customConfigHandler.getComputedCustomConfigValue(
                CustomConfigConstants.XTREMIO_VOLUME_FOLDER_NAME, storage.getSystemType(), dataSource);

        return volumeGroupFolderName;
    }

    @Override
    public void establishVolumeSnapshotGroupRelation(StorageSystem storage, URI sourceVolume,
            URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createGroupSnapshotSession(StorageSystem system, URI snapSessionURI, String groupName, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            String copyMode, Boolean targetExists, TaskCompleter completer)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void linkSnapshotSessionTargetGroup(StorageSystem system, URI snapshotSessionURI, List<URI> snapSessionSnapshotURIs,
            String copyMode, Boolean targetsExist, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkSnapshotSessionTarget(StorageSystem system, URI tgtSnapSessionURI, URI snapshotURI,
            TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkSnapshotSessionTargetGroup(StorageSystem system, URI tgtSnapSessionURI, URI snapshotURI,
            TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlinkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            Boolean deleteTarget, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSnapshotSession(StorageSystem system, URI snapSessionURI, String groupName, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}
