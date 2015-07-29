/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.ArrayList;
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
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
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
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.base.Joiner;

public class XtremIOStorageDevice extends DefaultBlockStorageDevice {

    private static final Logger _log = LoggerFactory.getLogger(XtremIOStorageDevice.class);
    private static final String VOLUME = "volume";
    private static final String SNAPSHOT = "snapshot";
    private static final String VOLUMES_SUBFOLDER = "/volumes";
    private static final String SNAPSHOTS_SUBFOLDER = "/snapshots";

    XtremIOClientFactory xtremioRestClientFactory;
    DbClient dbClient;

    @Autowired
    private DataSourceFactory dataSourceFactory;
    @Autowired
    private CustomConfigHandler customConfigHandler;

    private XtremIOExportOperations xtremioExportOperationHelper;
    private NameGenerator _nameGenerator;

    private static final String noOpOnThisStorageArrayString = "No operation to perform on this storage array...";

    public void setXtremioExportOperationHelper(XtremIOExportOperations xtremioExportOperationHelper) {
        this.xtremioExportOperationHelper = xtremioExportOperationHelper;
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
        // find the project this volume belongs to, and find the volume folder corresponding to this project
        // if not found ,create new folder, else reuse this folder and add volume to it.
        List<String> failedVolumes = new ArrayList<String>();
        XtremIOClient client = null;
        try {
            client = getXtremIOClient(storage);
            URI projectUri = volumes.get(0).getProject().getURI();
            String volumesFolderName = createVolumeFolders(client, projectUri, storage).get(VOLUME);
            Random randomNumber = new Random();
            for (Volume volume : volumes) {
                try {
                    XtremIOVolume createdVolume = null;

                    String userDefinedLabel = _nameGenerator.generate("", volume.getLabel(), "",
                            '_', XtremIOConstants.XTREMIO_MAX_VOL_LENGTH);
                    volume.setLabel(userDefinedLabel);
                    while (null != XtremIOProvUtils.isVolumeAvailableInArray(client,
                            volume.getLabel())) {
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
                            volumesFolderName);
                    createdVolume = client.getVolumeDetails(volume.getLabel());
                    _log.info("Created volume details {}", createdVolume.toString());

                    volume.setNativeId(createdVolume.getVolInfo().get(0));
                    volume.setWWN(createdVolume.getVolInfo().get(0));
                    volume.setDeviceLabel(volume.getLabel());
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
            Long sizeInGB = new Long(sizeInBytes / (1024 * 1024 * 1024));
            // XtremIO Rest API supports only expansion in GBs.
            String capacityInGBStr = String.valueOf(sizeInGB).concat("g");
            client.expandVolume(volume.getLabel(), capacityInGBStr);
            XtremIOVolume createdVolume = client.getVolumeDetails(volume.getLabel());
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
            URI projectUri = volumes.get(0).getProject().getURI();
            URI poolUri = volumes.get(0).getPool();

            for (Volume volume : volumes) {
                try {
                    if (null != XtremIOProvUtils
                            .isVolumeAvailableInArray(client, volume.getLabel())) {
                        client.deleteVolume(volume.getLabel());
                    }
                    volume.setInactive(true);
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
                cleanupVolumeFoldersIfNeeded(client, projectUri, storageSystem);
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
            Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("SnapShot Creation..... Started");
        try {
            List<BlockSnapshot> failedSnapshots = new ArrayList<BlockSnapshot>();
            StringBuffer errorMsg = new StringBuffer();
            XtremIOClient client = getXtremIOClient(storage);
            BlockSnapshot snap = null;
            for (URI snapUri : snapshotList) {
                try {
                    snap = dbClient.queryObject(BlockSnapshot.class, snapUri);
                    Volume parentVolume = dbClient.queryObject(Volume.class, snap.getParent().getURI());
                    URI projectUri = snap.getProject().getURI();
                    String snapFolderName = createVolumeFolders(client, projectUri, storage).get(SNAPSHOT);
                    String generatedLabel = _nameGenerator.generate("", snap.getLabel(), "",
                            '_', XtremIOConstants.XTREMIO_MAX_VOL_LENGTH);
                    snap.setLabel(generatedLabel);
                    client.createSnapshot(parentVolume.getLabel(), generatedLabel, snapFolderName);
                    XtremIOVolume createdSnap = client.getSnapShotDetails(snap.getLabel());
                    snap.setNativeId(createdSnap.getVolInfo().get(0));
                    snap.setWWN(createdSnap.getVolInfo().get(0));
                    // if created nsap wwn is not empty then update the wwn
                    if (!createdSnap.getWwn().isEmpty()) {
                        snap.setWWN(createdSnap.getWwn());
                    }

                    String nativeGuid = NativeGUIDGenerator
                            .getNativeGuidforSnapshot(storage, storage.getSerialNumber(), snap.getNativeId());
                    snap.setNativeGuid(nativeGuid);
                    snap.setIsSyncActive(true);
                    dbClient.persistObject(snap);
                } catch (Exception e) {
                    _log.error("Snapshot creation failed", e);
                    snap.setInactive(true);
                    failedSnapshots.add(snap);
                    errorMsg.append("Failed to create snapshot:").append(snap.getLabel()).
                            append(e.getMessage()).append("\n");
                }
            }
            if (!failedSnapshots.isEmpty()) {
                dbClient.persistObject(failedSnapshots);
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(errorMsg.toString(), null);
                taskCompleter.error(dbClient, serviceError);
                return;
            }
            taskCompleter.ready(dbClient);
            _log.info("SnapShot Creation..... End");
        } catch (Exception e) {
            _log.error("Create Snapshot Operations failed :", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("SnapShot Deletion..... Started");
        try {
            XtremIOClient client = getXtremIOClient(storage);
            BlockSnapshot snap = dbClient.queryObject(BlockSnapshot.class, snapshot);
            client.deleteSnapshot(snap.getLabel());
            snap.setInactive(true);
            dbClient.persistObject(snap);
            taskCompleter.ready(dbClient);
            _log.info("SnapShot Deletion..... End");
        } catch (Exception e) {
            _log.error("Delete Snapshot Operations failed ", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage, final URI consistencyGroupId,
            Boolean markInactive, final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("doDeleteConsistencyGroup: " + noOpOnThisStorageArrayString);
        taskCompleter.ready(dbClient);
    }

    @Override
    public void doCreateConsistencyGroup(StorageSystem storage, URI consistencyGroup,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("doCreateConsistencyGroup: " + noOpOnThisStorageArrayString);
        taskCompleter.ready(dbClient);
    }

    @Override
    public void doAddToConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("doAddToConsistencyGroup: " + noOpOnThisStorageArrayString);
        taskCompleter.ready(dbClient);
    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) {
        return new HashMap<String, Set<URI>>();
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        return mask;
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        return true;
    }

    private XtremIOClient getXtremIOClient(StorageSystem system) {
        XtremIOClient client = (XtremIOClient) xtremioRestClientFactory.getRESTClient(
                URI.create(XtremIOConstants.getXIOBaseURI(system.getIpAddress(),
                        system.getPortNumber())), system.getUsername(), system.getPassword(), true);
        return client;
    }

    private Map<String, String> createVolumeFolders(XtremIOClient client, URI projectURI, StorageSystem storage)
            throws Exception {

        List<String> folderNames = client.getVolumeFolderNames();
        _log.info("Volume folder Names found on Array : {}", Joiner.on("; ").join(folderNames));
        Map<String, String> folderNamesMap = new HashMap<String, String>();
        String tempRootFolderName = getVolumeFolderName(projectURI, storage);
        String rootFolderName = XtremIOConstants.ROOT_FOLDER.concat(tempRootFolderName);
        _log.info("rootVolumeFolderName: {}", rootFolderName);
        String volumesFolderName = rootFolderName.concat(VOLUMES_SUBFOLDER);
        String snapshotsFolderName = rootFolderName.concat(SNAPSHOTS_SUBFOLDER);
        folderNamesMap.put(VOLUME, volumesFolderName);
        folderNamesMap.put(SNAPSHOT, snapshotsFolderName);
        if (!folderNames.contains(rootFolderName)) {
            _log.info("Sending create root folder request {}", rootFolderName);
            client.createVolumeFolder(tempRootFolderName, "/");
        } else {
            _log.info("Found {} folder on the Array.", rootFolderName);
        }

        if (!folderNames.contains(volumesFolderName)) {
            _log.info("Sending create volume folder request {}", volumesFolderName);
            client.createVolumeFolder("volumes", rootFolderName);
        } else {
            _log.info("Found {} folder on the Array.", volumesFolderName);
        }

        if (!folderNames.contains(snapshotsFolderName)) {
            _log.info("Sending create snapshot folder request {}", snapshotsFolderName);
            client.createVolumeFolder("snapshots", rootFolderName);
        } else {
            _log.info("Found {} folder on the Array.", snapshotsFolderName);
        }

        return folderNamesMap;
    }

    private String getVolumeFolderName(URI projectURI, StorageSystem storage) {
        String volumeGroupFolderName = "";
        Project project = dbClient.queryObject(Project.class, projectURI);
        DataSource dataSource = dataSourceFactory.createXtremIOVolumeFolderNameDataSource(project, storage);
        volumeGroupFolderName = customConfigHandler.getComputedCustomConfigValue(
                CustomConfigConstants.XTREMIO_VOLUME_FOLDER_NAME, storage.getSystemType(), dataSource);

        return volumeGroupFolderName;
    }

    private void cleanupVolumeFoldersIfNeeded(XtremIOClient client, URI projectURI,
            StorageSystem storageSystem) throws Exception {

        String tempRootFolderName = getVolumeFolderName(projectURI, storageSystem);
        String rootFolderName = XtremIOConstants.ROOT_FOLDER.concat(tempRootFolderName);
        String volumesFolderName = rootFolderName.concat(VOLUMES_SUBFOLDER);
        String snapshotsFolderName = rootFolderName.concat(SNAPSHOTS_SUBFOLDER);

        // Find the # volumes in folder, if the Volume folder is empty,
        // then delete the folder too
        try {
            int numberOfVolumes = client.getNumberOfVolumesInFolder(rootFolderName);
            if (numberOfVolumes == 0) {
                try {
                    _log.info("Deleting Volumes Folder ...");
                    client.deleteVolumeFolder(volumesFolderName);
                } catch (Exception e) {
                    _log.warn("Deleting volumes folder {} failed", volumesFolderName, e);
                }

                try {
                    _log.info("Deleting Snapshots Folder ...");
                    client.deleteVolumeFolder(snapshotsFolderName);
                } catch (Exception e) {
                    _log.warn("Deleting snapshots folder {} failed", snapshotsFolderName, e);
                }

                _log.info("Deleting Root Folder ...");
                client.deleteVolumeFolder(rootFolderName);
            }
        } catch (Exception e) {
            _log.warn("Deleting root folder {} failed", rootFolderName, e);
        }
    }

}
