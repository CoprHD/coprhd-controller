/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.XtremIOConstants.XTREMIO_ENTITY_TYPE;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.base.Joiner;

public class XtremIOStorageDevice extends DefaultBlockStorageDevice {

    private static final Logger _log = LoggerFactory.getLogger(XtremIOStorageDevice.class);

    XtremIOClientFactory xtremioRestClientFactory;
    DbClient dbClient;

    @Autowired
    private DataSourceFactory dataSourceFactory;
    @Autowired
    private CustomConfigHandler customConfigHandler;

    private XtremIOExportOperations xtremioExportOperationHelper;
    private XtremIOSnapshotOperations snapshotOperations;
    private NameGenerator _nameGenerator;

    private static final String noOpOnThisStorageArrayString = "No operation to perform on this storage array...";

    public void setXtremioExportOperationHelper(XtremIOExportOperations xtremioExportOperationHelper) {
        this.xtremioExportOperationHelper = xtremioExportOperationHelper;
    }

    public void setSnapshotOperations(XtremIOSnapshotOperations snapshotOperations) {
        this.snapshotOperations = snapshotOperations;
    }

    public void setXtremioRestClientFactory(XtremIOClientFactory xtremioRestClientFactory) {
        this.xtremioRestClientFactory = xtremioRestClientFactory;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setNameGenerator(final NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    @Override
    public void doCreateVolumes(StorageSystem storage, StoragePool storagePool, String opId,
            List<Volume> volumes, VirtualPoolCapabilityValuesWrapper capabilities,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        List<String> failedVolumes = new ArrayList<String>();
        XtremIOClient client = null;
        try {
            client = getXtremIOClient(storage);
            BlockConsistencyGroup cgObj = null;
            boolean isCG = false;
            Volume vol = volumes.get(0);
            if (vol.getConsistencyGroup() != null && !vol.checkForRp()) {
                cgObj = dbClient.queryObject(BlockConsistencyGroup.class, vol.getConsistencyGroup());
                isCG = true;
            }
            // find the project this volume belongs to.
            URI projectUri = volumes.get(0).getProject().getURI();
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            // For version 1 APIs, find the volume folder corresponding to this project
            // if not found ,create new folder, else reuse this folder and add volume to it.

            // For version 2 APIs, find tags corresponding to this project
            // if not found ,create new tag, else reuse this tag and tag the volume.
            boolean isVersion2 = client.isVersion2();
            String volumesFolderName = "";
            if (isVersion2) {
                volumesFolderName = XtremIOProvUtils.createTagsForVolumeAndSnaps(client, getVolumeFolderName(projectUri, storage),
                        clusterName).
                        get(XtremIOConstants.VOLUME_KEY);
            } else {
                volumesFolderName = XtremIOProvUtils.createFoldersForVolumeAndSnaps(client, getVolumeFolderName(projectUri, storage)).
                        get(XtremIOConstants.VOLUME_KEY);
            }
            Random randomNumber = new Random();
            for (Volume volume : volumes) {
                try {
                    XtremIOVolume createdVolume = null;

                    String userDefinedLabel = _nameGenerator.generate("", volume.getLabel(), "",
                            '_', XtremIOConstants.XTREMIO_MAX_VOL_LENGTH);
                    volume.setLabel(userDefinedLabel);
                    while (null != XtremIOProvUtils.isVolumeAvailableInArray(client,
                            volume.getLabel(), clusterName)) {
                        _log.info("Volume with name {} already exists", volume.getLabel());
                        String tempLabel = userDefinedLabel.concat("_").concat(
                                String.valueOf(randomNumber.nextInt(1000)));
                        volume.setLabel(tempLabel);
                        _log.info("Retrying volume creation with label {}", tempLabel);
                    }

                    // If the volume is a protected Vplex backend volume the capacity has already been
                    // adjusted in the RPBlockServiceApiImpl class therefore there is no need to adjust it here.
                    // If it is not, add 1 MB extra to make up the missing bytes due to divide by 1024
                    int amountToAdjustCapacity = Volume.checkForProtectedVplexBackendVolume(dbClient, volume) ? 0 : 1;
                    Long capacityInMB = new Long(volume.getCapacity() / (1024 * 1024) + amountToAdjustCapacity);
                    String capacityInMBStr = String.valueOf(capacityInMB).concat("m");
                    _log.info("Sending create volume request with name: {}, size: {}",
                            volume.getLabel(), capacityInMBStr);
                    client.createVolume(volume.getLabel(), capacityInMBStr,
                            volumesFolderName, clusterName);
                    createdVolume = client.getVolumeDetails(volume.getLabel(), clusterName);
                    _log.info("Created volume details {}", createdVolume.toString());
                    // For version 2, tag the created volume
                    if (isVersion2) {
                        client.tagObject(volumesFolderName, XTREMIO_ENTITY_TYPE.Volume.name(), volume.getLabel(), clusterName);
                    }
                    if (isCG) {
                        client.addVolumeToConsistencyGroup(volume.getLabel(), cgObj.getLabel(), clusterName);
                    }
                    volume.setNativeId(createdVolume.getVolInfo().get(0));
                    volume.setWWN(createdVolume.getVolInfo().get(0));
                    volume.setDeviceLabel(volume.getLabel());
                    volume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                    // When a volume is created, the WWN field will be empty, hence use the volume's native Id as WWN
                    // If the REST API wwn field is populated, then use it.
                    if (!createdVolume.getWwn().isEmpty()) {
                        volume.setWWN(createdVolume.getWwn());
                    }

                    String nativeGuid = NativeGUIDGenerator.generateNativeGuid(dbClient, volume);

                    volume.setNativeGuid(nativeGuid);
                    volume.setProvisionedCapacity(Long.parseLong(createdVolume
                            .getAllocatedCapacity()) * 1024);
                    volume.setAllocatedCapacity(Long.parseLong(createdVolume.getAllocatedCapacity()) * 1024);
                    dbClient.updateAndReindexObject(volume);
                } catch (Exception e) {
                    failedVolumes.add(volume.getLabel());
                    _log.error("Error during volume create.", e);
                }
            }

            if (!failedVolumes.isEmpty()) {
                String errMsg = "Failed to create volumes: ".concat(Joiner.on(", ").join(
                        failedVolumes));
                ServiceError error = DeviceControllerErrors.xtremio.createVolumeFailure(errMsg);
                taskCompleter.error(dbClient, error);
            } else {
                taskCompleter.ready(dbClient);
            }

        } catch (Exception e) {
            _log.error("Error while creating volumes", e);
            ServiceError error = DeviceControllerErrors.xtremio.createVolumeFailure(e.getMessage());
            taskCompleter.error(dbClient, error);
        }

        // update StoragePool capacity
        try {
            XtremIOProvUtils.updateStoragePoolCapacity(client, dbClient, storagePool);
        } catch (Exception e) {
            _log.warn("Error while updating pool capacity", e);
        }
    }

    @Override
    public void doExpandVolume(StorageSystem storage, StoragePool pool, Volume volume,
            Long sizeInBytes, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("Expand Volume..... Started");
        try {
            XtremIOClient client = getXtremIOClient(storage);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            Long sizeInGB = new Long(sizeInBytes / (1024 * 1024 * 1024));
            // XtremIO Rest API supports only expansion in GBs.
            String capacityInGBStr = String.valueOf(sizeInGB).concat("g");
            client.expandVolume(volume.getLabel(), capacityInGBStr, clusterName);
            XtremIOVolume createdVolume = client.getVolumeDetails(volume.getLabel(), clusterName);
            volume.setProvisionedCapacity(Long.parseLong(createdVolume
                    .getAllocatedCapacity()) * 1024);
            volume.setAllocatedCapacity(Long.parseLong(createdVolume.getAllocatedCapacity()) * 1024);
            volume.setCapacity(Long.parseLong(createdVolume.getAllocatedCapacity()) * 1024);
            dbClient.persistObject(volume);
            // update StoragePool capacity
            try {
                XtremIOProvUtils.updateStoragePoolCapacity(client, dbClient, pool);
            } catch (Exception e) {
                _log.warn("Error while updating pool capacity", e);
            }
            taskCompleter.ready(dbClient);
            _log.info("Expand Volume..... End");
        } catch (Exception e) {
            _log.error("Error while expanding volumes", e);
            ServiceError error = DeviceControllerErrors.xtremio.expandVolumeFailure(e);
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void doDeleteVolumes(StorageSystem storageSystem, String opId, List<Volume> volumes,
            TaskCompleter completer) throws DeviceControllerException {

        List<String> failedVolumes = new ArrayList<String>();
        try {
            XtremIOClient client = getXtremIOClient(storageSystem);
            String clusterName = client.getClusterDetails(storageSystem.getSerialNumber()).getName();

            URI projectUri = volumes.get(0).getProject().getURI();
            URI poolUri = volumes.get(0).getPool();

            for (Volume volume : volumes) {
                try {
                    if (null != XtremIOProvUtils.isVolumeAvailableInArray(client, volume.getLabel(), clusterName)) {
                        if (volume.getConsistencyGroup() != null) {
                            BlockConsistencyGroup consistencyGroupObj = dbClient.queryObject(BlockConsistencyGroup.class,
                                    volume.getConsistencyGroup());
                            // first remove the volume from cg and then delete
                            _log.info("Removing the volume {} from consistency group {}", volume.getLabel(), consistencyGroupObj.getLabel());
                            client.removeVolumeFromConsistencyGroup(volume.getLabel(), consistencyGroupObj.getLabel(), clusterName);
                        }
                        _log.info("Deleting the volume {}", volume.getLabel());
                        client.deleteVolume(volume.getLabel(), clusterName);
                    }
                    volume.setInactive(true);
                    volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    dbClient.persistObject(volume);
                } catch (Exception e) {
                    failedVolumes.add(volume.getLabel());
                    _log.error("Error during volume {} delete.", volume.getLabel(), e);
                }
            }

            if (!failedVolumes.isEmpty()) {
                String errMsg = "Failed to delete volumes: ".concat(Joiner.on(", ").join(
                        failedVolumes));
                ServiceError error = DeviceControllerErrors.xtremio.deleteVolumeFailure(errMsg);
                completer.error(dbClient, error);
            } else {
                String volumeFolderName = getVolumeFolderName(projectUri, storageSystem);
                XtremIOProvUtils.cleanupVolumeFoldersIfNeeded(client, clusterName, volumeFolderName, storageSystem);
                completer.ready(dbClient);
            }

            // update StoragePool capacity for pools changed
            StoragePool pool = dbClient.queryObject(StoragePool.class, poolUri);
            try {
                _log.info("Updating Pool {} Capacity", pool.getNativeGuid());
                XtremIOProvUtils.updateStoragePoolCapacity(client, dbClient, pool);
            } catch (Exception e) {
                _log.warn("Error while updating pool capacity for pool {} ", poolUri, e);
            }

        } catch (Exception e) {
            _log.error("Error while deleting volumes", e);
            ServiceError error = DeviceControllerErrors.xtremio.deleteVolumeFailure(e.getMessage());
            completer.error(dbClient, error);
        }
    }

    @Override
    public void doExportGroupCreate(StorageSystem storage, ExportMask exportMask,
            Map<URI, Integer> volumeMap, List<Initiator> initiators, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        /**
         * - Starting point: List of ViPR Initiator Records updated via Discovery - Find initiator
         * using Initiator label. - If not found, POST to IG and use IG-name to POST to Initiator
         * with IG (IG per Host) - If found, find IG - Maintain an in-memory Set of IGs - Lookup
         * Volumes - Create LUNMap foreach (IG, V)
         * 
         * 0. Discover Initiators and update the corresponding labels if not set. 0a. If label is
         * not matching with user given, then keep a local data structure in memory and use it only
         * during this process. 1. Look up IG by name and find IGs 2. Store IGs in list 3. If list
         * empty, then create a new IG-folder with host or cluster Name 4. Create initiators add
         * them to a new IG, and add them to IG-folder 5. If list partial, 5a . Then check whether
         * existing IGs is having a complete subset of expected initiators of the same host 5b. If
         * yes, then add the remaining initiators to the any one of the initiator Group which
         * matches above criteria 5c. Else, create a new initiator group and add the remaining
         * initiators. 6. If complete, no action 7. Create LunMaps for each Volume and IG.
         * 
         * 
         */
        _log.info("{} doExportGroupCreate START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumeMap, dbClient);
        xtremioExportOperationHelper.createExportMask(storage, exportMask.getId(), volumeLunArray,
                targets, initiators, taskCompleter);
        _log.info("{} doExportGroupCreate END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportGroupDelete(StorageSystem storage, ExportMask exportMask,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportGroupDelete START ...", storage.getSerialNumber());
        xtremioExportOperationHelper.deleteExportMask(storage, exportMask.getId(),
                new ArrayList<URI>(), new ArrayList<URI>(), new ArrayList<Initiator>(),
                taskCompleter);
        _log.info("{} doExportGroupDelete END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddVolume(StorageSystem storage, ExportMask exportMask, URI volume,
            Integer lun, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        map.put(volume, lun);

        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), map, dbClient);
        xtremioExportOperationHelper.addVolume(storage, exportMask.getId(), volumeLunArray,
                taskCompleter);
        _log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddVolumes(StorageSystem storage, ExportMask exportMask,
            Map<URI, Integer> volumes, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddVolumes START ...", storage.getSerialNumber());

        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumes, dbClient);
        xtremioExportOperationHelper.addVolume(storage, exportMask.getId(), volumeLunArray,
                taskCompleter);
        _log.info("{} doExportAddVolumes END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveVolume(StorageSystem storage, ExportMask exportMask, URI volume,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveVolumes START ...", storage.getSerialNumber());
        List<URI> volumeUris = new ArrayList<URI>();
        volumeUris.add(volume);
        xtremioExportOperationHelper.removeVolume(storage, exportMask.getId(), volumeUris,
                taskCompleter);
        _log.info("{} doExportRemoveVolumes END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveVolumes(StorageSystem storage, ExportMask exportMask,
            List<URI> volumes, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveVolumes START ...", storage.getSerialNumber());
        xtremioExportOperationHelper.removeVolume(storage, exportMask.getId(), volumes,
                taskCompleter);
        _log.info("{} doExportRemoveVolumes END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddInitiator(StorageSystem storage, ExportMask exportMask,
            Initiator initiator, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        List<Initiator> initiatorList = new ArrayList<Initiator>();
        initiatorList.add(initiator);
        xtremioExportOperationHelper.addInitiator(storage, exportMask.getId(), initiatorList,
                targets, taskCompleter);
        _log.info("{} doExportAddInitiators END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddInitiators(StorageSystem storage, ExportMask exportMask,
            List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddInitiators START ...", storage.getSerialNumber());
        xtremioExportOperationHelper.addInitiator(storage, exportMask.getId(), initiators, targets,
                taskCompleter);
        _log.info("{} doExportAddInitiators END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveInitiator(StorageSystem storage, ExportMask exportMask,
            Initiator initiator, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        List<Initiator> initiatorList = new ArrayList<Initiator>();
        initiatorList.add(initiator);
        xtremioExportOperationHelper.removeInitiator(storage, exportMask.getId(), initiatorList,
                targets, taskCompleter);
        _log.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveInitiators(StorageSystem storage, ExportMask exportMask,
            List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiators START ...", storage.getSerialNumber());
        xtremioExportOperationHelper.removeInitiator(storage, exportMask.getId(), initiators,
                targets, taskCompleter);
        _log.info("{} doExportRemoveInitiators END ...", storage.getSerialNumber());
    }

    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("SnapShot Creation..... Started");
        List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, snapshotList);
        if (ControllerUtils.inReplicationGroup(snapshots, dbClient)) {
            snapshotOperations.createGroupSnapshots(storage, snapshotList, createInactive, readOnly, taskCompleter);
        } else {
            URI snapshot = snapshots.get(0).getId();
            snapshotOperations.createSingleVolumeSnapshot(storage, snapshot, createInactive,
                    readOnly, taskCompleter);
        }
        _log.info("SnapShot Creation..... End");
    }

    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("SnapShot Deletion..... Started");
        List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));
        if (ControllerUtils.inReplicationGroup(snapshots, dbClient)) {
            snapshotOperations.deleteGroupSnapshots(storage, snapshot, taskCompleter);
        } else {
            snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);
        }
        _log.info("SnapShot Deletion..... End");
    }

    @Override
    public void doRestoreFromSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("SnapShot Restore..... Started");
        List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));
        if (ControllerUtils.inReplicationGroup(snapshots, dbClient)) {
            snapshotOperations.restoreGroupSnapshots(storage, volume, snapshot, taskCompleter);
        } else {
            snapshotOperations.restoreSingleVolumeSnapshot(storage, volume, snapshot, taskCompleter);
        }
        _log.info("SnapShot Restore..... End");
    }

    @Override
    public void doResyncSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("SnapShot resync..... Started");
        List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));
        if (ControllerUtils.inReplicationGroup(snapshots, dbClient)) {
            snapshotOperations.resyncGroupSnapshots(storage, volume, snapshot, taskCompleter);
        } else {
            snapshotOperations.resyncSingleVolumeSnapshot(storage, volume, snapshot, taskCompleter);
        }
        _log.info("SnapShot resync..... End");
    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage, final URI consistencyGroupId,
            Boolean markInactive, final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doDeleteConsistencyGroup START ...", storage.getSerialNumber());
        try {
            // Check if the consistency group exists
            BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);
            XtremIOClient client = getXtremIOClient(storage);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            if (null != XtremIOProvUtils.isCGAvailableInArray(client, consistencyGroup.getLabel(), clusterName)) {
                client.removeConsistencyGroup(consistencyGroup.getLabel(), clusterName);
            }
            // Set the consistency group to inactive
            URI systemURI = storage.getId();
            consistencyGroup.removeSystemConsistencyGroup(systemURI.toString(),
                    consistencyGroup.getLabel());
            if (markInactive) {
                consistencyGroup.setInactive(true);
            }
            dbClient.persistObject(consistencyGroup);
            taskCompleter.ready(dbClient);
            _log.info("{} doDeleteConsistencyGroup END ...", storage.getSerialNumber());
        } catch (Exception e) {
            _log.error(String.format("Delete Consistency Group operation failed %s", e));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void doCreateConsistencyGroup(StorageSystem storage, URI consistencyGroupId,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doCreateConsistencyGroup START ...", storage.getSerialNumber());
        try {
            XtremIOClient client = getXtremIOClient(storage);
            BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            client.createConsistencyGroup(consistencyGroup.getLabel(), clusterName);
            consistencyGroup.addSystemConsistencyGroup(storage.getId().toString(), consistencyGroup.getLabel());
            consistencyGroup.addConsistencyGroupTypes(Types.LOCAL.name());
            if (NullColumnValueGetter.isNullURI(consistencyGroup.getStorageController())) {
                consistencyGroup.setStorageController(storage.getId());
            }
            dbClient.persistObject(consistencyGroup);
            taskCompleter.ready(dbClient);
            _log.info("{} doCreateConsistencyGroup END ...", storage.getSerialNumber());
        } catch (Exception e) {
            _log.error(String.format("Create Consistency Group operation failed %s", e));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void doAddToConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doAddToConsistencyGroup START ...", storage.getSerialNumber());
        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);
        try {
            // Check if the consistency group exists
            XtremIOClient client = getXtremIOClient(storage);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            XtremIOConsistencyGroup cg = XtremIOProvUtils.isCGAvailableInArray(client, consistencyGroup.getLabel(), clusterName);
            if (cg == null) {
                _log.error("The consistency group does not exist in the array: {}", storage.getSerialNumber());
                taskCompleter.error(dbClient, DeviceControllerException.exceptions
                        .consistencyGroupNotFound(consistencyGroup.getLabel(),
                                consistencyGroup.getCgNameOnStorageSystem(storage.getId())));
                return;
            }

            List<BlockObject> updatedBlockObjects = new ArrayList<BlockObject>();
            for (URI uri : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(dbClient, uri);
                if (blockObject != null) {
                    client.addVolumeToConsistencyGroup(blockObject.getLabel(), consistencyGroup.getLabel(), clusterName);
                    blockObject.setConsistencyGroup(consistencyGroupId);
                    updatedBlockObjects.add(blockObject);
                }
            }
            dbClient.updateAndReindexObject(updatedBlockObjects);
            taskCompleter.ready(dbClient);
            _log.info("{} doAddToConsistencyGroup END ...", storage.getSerialNumber());
        } catch (Exception e) {
            _log.error(String.format("Add To Consistency Group operation failed %s", e));
            // Remove any references to the consistency group
            for (URI blockObjectURI : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectURI);
                if (blockObject != null) {
                    blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
                dbClient.persistObject(blockObject);
            }
            taskCompleter.error(dbClient, DeviceControllerException.exceptions
                    .failedToAddMembersToConsistencyGroup(consistencyGroup.getLabel(),
                            consistencyGroup.getLabel(), e.getMessage()));
        }
    }

    @Override
    public void doRemoveFromConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doRemoveFromConsistencyGroup START ...", storage.getSerialNumber());
        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);
        try {
            // Check if the consistency group exists
            XtremIOClient client = getXtremIOClient(storage);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            XtremIOConsistencyGroup cg = XtremIOProvUtils.isCGAvailableInArray(client, consistencyGroup.getLabel(), clusterName);
            if (cg == null) {
                _log.error("The consistency group does not exist in the array: {}", storage.getSerialNumber());
                taskCompleter.error(dbClient, DeviceControllerException.exceptions
                        .consistencyGroupNotFound(consistencyGroup.getLabel(),
                                consistencyGroup.getCgNameOnStorageSystem(storage.getId())));
                return;
            }

            List<BlockObject> updatedBlockObjects = new ArrayList<BlockObject>();
            for (URI uri : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(dbClient, uri);
                if (blockObject != null) {
                    client.removeVolumeFromConsistencyGroup(blockObject.getLabel(), consistencyGroup.getLabel(), clusterName);
                    blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    updatedBlockObjects.add(blockObject);
                }
            }
            dbClient.updateAndReindexObject(updatedBlockObjects);
            taskCompleter.ready(dbClient);
            _log.info("{} doRemoveFromConsistencyGroup END ...", storage.getSerialNumber());
        } catch (Exception e) {
            _log.error(String.format("Remove from Consistency Group operation failed %s", e));
            taskCompleter.error(dbClient, DeviceControllerException.exceptions
                    .failedToRemoveMembersToConsistencyGroup(consistencyGroup.getLabel(),
                            consistencyGroup.getLabel(), e.getMessage()));
        }
    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) {
        return new HashMap<String, Set<URI>>();
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        xtremioExportOperationHelper.refreshExportMask(storage, mask);
        return mask;
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        return true;
    }

    private XtremIOClient getXtremIOClient(StorageSystem system) {
        xtremioRestClientFactory.setModel(system.getFirmwareVersion());
        XtremIOClient client = (XtremIOClient) xtremioRestClientFactory
                .getRESTClient(
                        URI.create(XtremIOConstants.getXIOBaseURI(system.getSmisProviderIP(),
                                system.getSmisPortNumber())), system.getSmisUserName(), system.getSmisPassword(), true);
        return client;
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
