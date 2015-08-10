/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.XtremIOConstants.XTREMIO_ENTITY_TYPE;
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
            XtremIOClient client = getXtremIOClient(storage);
            String generatedLabel = nameGenerator.generate("", snap.getLabel(), "",
                    '_', XtremIOConstants.XTREMIO_MAX_VOL_LENGTH);
            snap.setLabel(generatedLabel);
            XtremIOVolume createdSnap;
            if (client.isVersion2()) {
                createdSnap = createV2Snapshot(client, storage, snap, generatedLabel, readOnly, taskCompleter);
            } else {
                createdSnap = createV1Snapshot(client, storage, snap, generatedLabel, readOnly, taskCompleter);
            }
            if (createdSnap != null) {
                snap.setWWN(createdSnap.getVolInfo().get(0));
                // if created snap wwn is not empty then update the wwn
                if (!createdSnap.getWwn().isEmpty()) {
                    snap.setWWN(createdSnap.getWwn());
                }
                snap.setDeviceLabel(createdSnap.getVolInfo().get(1));
                snap.setNativeId(createdSnap.getVolInfo().get(0));
                String nativeGuid = NativeGUIDGenerator.getNativeGuidforSnapshot(storage, storage.getSerialNumber(), snap.getNativeId());
                snap.setNativeGuid(nativeGuid);
                snap.setIsSyncActive(true);
            }

            dbClient.persistObject(snap);
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

    private XtremIOVolume createV2Snapshot(XtremIOClient client, StorageSystem storage, BlockSnapshot snap, String snapLabel,
            Boolean readOnly, TaskCompleter taskCompleter) throws Exception {
        Volume parentVolume = dbClient.queryObject(Volume.class, snap.getParent().getURI());
        URI projectUri = snap.getProject().getURI();
        String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
        
        String snapTagName = XtremIOProvUtils.createTagsForVolumeAndSnaps(client, getVolumeFolderName(projectUri, storage), clusterName)
                .get(XtremIOConstants.SNAPSHOT_KEY);
        String snapshotType = readOnly ? XtremIOConstants.XTREMIO_READ_ONLY_TYPE : XtremIOConstants.XTREMIO_REGULAR_TYPE;
        client.createVolumeSnapshot(parentVolume.getLabel(), snapLabel, snapTagName, snapshotType, clusterName);
        XtremIOVolume createdSnap = client.getSnapShotDetails(snapLabel, clusterName);
        //tag the created the snap
        client.tagObject(snapTagName, XTREMIO_ENTITY_TYPE.SnapshotSet.name(), snapLabel, clusterName);
        return createdSnap;

    }

    @Override
    public void createGroupSnapshots(StorageSystem storage, List<URI> snapshotList, Boolean createInactive,
            Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {

        try {
            XtremIOClient client = getXtremIOClient(storage);
            URI snapshot = snapshotList.get(0);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            
            URI cgId = snapshotObj.getConsistencyGroup();
            if (cgId != null) {
                BlockConsistencyGroup group = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
                String snapType = readOnly ? XtremIOConstants.XTREMIO_READ_ONLY_TYPE : XtremIOConstants.XTREMIO_REGULAR_TYPE;
                client.createConsistencyGroupSnapshot(group.getLabel(), snapshotObj.getSnapsetLabel(), "", snapType, clusterName);
            }

            // TODO - Handle updating the snapshots in db with info from created xio snaps
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot creation failed", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

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
            XtremIOClient client = getXtremIOClient(storage);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            
            client.deleteSnapshot(snapshotObj.getLabel(), clusterName);
            snapshotObj.setIsSyncActive(false);
            snapshotObj.setInactive(true);
            dbClient.persistObject(snapshotObj);
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
            XtremIOClient client = getXtremIOClient(storage);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            
            client.deleteSnapshotSet(snapshotObj.getSnapsetLabel(), clusterName);
            // Set inactive=true for all snapshots in the snap
            List<BlockSnapshot> snapshots = ControllerUtils.getBlockSnapshotsBySnapsetLabelForProject(snapshotObj, dbClient);
            for (BlockSnapshot snap : snapshots) {
                snap.setIsSyncActive(false);
                snap.setInactive(true);
                dbClient.persistObject(snap);
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
            XtremIOClient client = getXtremIOClient(storage);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            Volume sourceVol = dbClient.queryObject(Volume.class, snapshotObj.getParent());
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            
            client.restoreVolumeFromSnapshot(clusterName, sourceVol.getLabel(), snapshotObj.getLabel());
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
            XtremIOClient client = getXtremIOClient(storage);
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
            BlockConsistencyGroup group = dbClient.queryObject(BlockConsistencyGroup.class, snapshotObj.getConsistencyGroup());
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            
            client.restoreCGFromSnapshot(clusterName, group.getLabel(), snapshotObj.getSnapsetLabel());
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error("Snapshot restore failed", e);
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

}
