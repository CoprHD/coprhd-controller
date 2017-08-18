/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.common.failureinjector.InvokeTestFailure;
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
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
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
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.collect.Lists;

public class XtremIOStorageDevice extends DefaultBlockStorageDevice {

    private static final Logger _log = LoggerFactory.getLogger(XtremIOStorageDevice.class);

    // Max retries for RecoverPoint volume delete operation
    private static final int MAX_RP_RETRIES = 30;
    // Wait 30 seconds before attempting another call to delete the RecoverPoint associated XIO volume
    private static final int RP_WAIT_FOR_RETRY = 30000;

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
        Map<String, String> failedVolumes = new HashMap<String, String>();
        XtremIOClient client = null;
        try {
            client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            BlockConsistencyGroup cgObj = null;
            boolean isCG = false;
            Volume vol = volumes.get(0);
            
            // If the volume is regular volume and in CG
            if (!NullColumnValueGetter.isNullURI(vol.getConsistencyGroup())) {
                cgObj = dbClient.queryObject(BlockConsistencyGroup.class, vol.getConsistencyGroup());
                if (cgObj != null
                        && cgObj.created(storage.getId())) {
                    // Only set this flag to true if the CG reference is valid
                    // and it is already created on the storage system.                  
                    isCG = true;
                    
                    //RP back-end volumes DO NOT have personality flag set. All RP volumes will satisfy checkForRP
                    //Find out out if this is a RP volume that is not a back-end volume to a RP+VPLEX volume.
                    boolean excludeRPNotBackendVolumes = vol.checkForRp() && (NullColumnValueGetter.isNotNullValue(vol.getPersonality()));
                   
                    // If Vplex backed volume does not have replicationGroupInstance value, should not add the volume into back-end cg
                    // Only XIO volumes that are back-end volumes to RP+VPLEX volumes will be in an array CG if arrayConsistency is chosen.
                   if (excludeRPNotBackendVolumes || (Volume.checkForVplexBackEndVolume(dbClient, vol)
                            && NullColumnValueGetter.isNullValue(vol.getReplicationGroupInstance()))) {
                        isCG = false;
                    }
                }
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
                        clusterName).get(XtremIOConstants.VOLUME_KEY);
            } else {
                volumesFolderName = XtremIOProvUtils.createFoldersForVolumeAndSnaps(client, getVolumeFolderName(projectUri, storage))
                        .get(XtremIOConstants.VOLUME_KEY);
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

                    // If the volume is a recoverpoint protected volume, the capacity has already been
                    // adjusted in the RPBlockServiceApiImpl class therefore there is no need to adjust it here.
                    // If it is not, add 1 MB extra to make up the missing bytes due to divide by 1024 (Usecase: XIO on HA side of VPLEX Distributed)
                    // If there are no additional bytes requested, don't add that extra 1 MB.
                    int amountToAdjustCapacity = 1;
                    if (Volume.checkForProtectedVplexBackendVolume(dbClient, volume) || volume.checkForRp()
                            || ((volume.getCapacity().doubleValue() / (1024 * 1024)) % 1) == 0) {
                        amountToAdjustCapacity = 0;
                    }

                    Long capacityInMB = new Long(volume.getCapacity() / (1024 * 1024) + amountToAdjustCapacity);
                    String capacityInMBStr = String.valueOf(capacityInMB).concat("m");
                    _log.info("Sending create volume request with name: {}, size: {}",
                            volume.getLabel(), capacityInMBStr);
                    client.createVolume(volume.getLabel(), capacityInMBStr,
                            volumesFolderName, clusterName);
                    createdVolume = client.getVolumeDetails(volume.getLabel(), clusterName);
                    _log.info("Created volume details {}", createdVolume.toString());                    

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
                    dbClient.updateObject(volume);
                    
                    // For version 2, tag the created volume
                    if (isVersion2) {
                        client.tagObject(volumesFolderName, XTREMIO_ENTITY_TYPE.Volume.name(), volume.getLabel(), clusterName);
                        // Do not add RP+VPlex journal or target backing volumes to consistency groups.
                        // This causes issues with local array snapshots of RP+VPlex volumes.
                        String cgName = volume.getReplicationGroupInstance();
                        if (isCG && !RPHelper.isAssociatedToRpVplexType(volume, dbClient,
                                PersonalityTypes.METADATA, PersonalityTypes.TARGET) &&
                                NullColumnValueGetter.isNotNullValue(cgName)) {
                            client.addVolumeToConsistencyGroup(volume.getLabel(), cgName, clusterName);
                        }
                    }
                } catch (Exception e) {
                    failedVolumes.put(volume.getLabel(), ControllerUtils.getMessage(e));
                    _log.error("Error during volume create.", e);
                }
            }

            if (!failedVolumes.isEmpty()) {
                StringBuffer errMsg = new StringBuffer("Failed to create volumes: ");
                for (String failedVolume : failedVolumes.keySet()) {
                    errMsg.append(failedVolume).append(":").append(failedVolumes.get(failedVolume));
                }
                ServiceError error = DeviceControllerErrors.xtremio.createVolumeFailure(errMsg.toString());
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
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            Long sizeInGB = new Long(sizeInBytes / (1024 * 1024 * 1024));
            // XtremIO Rest API supports only expansion in GBs.
            String capacityInGBStr = String.valueOf(sizeInGB).concat("g");
            client.expandVolume(volume.getDeviceLabel(), capacityInGBStr, clusterName);
            XtremIOVolume createdVolume = client.getVolumeDetails(volume.getDeviceLabel(), clusterName);
            volume.setProvisionedCapacity(Long.parseLong(createdVolume
                    .getAllocatedCapacity()) * 1024);
            volume.setAllocatedCapacity(Long.parseLong(createdVolume.getAllocatedCapacity()) * 1024);
            volume.setCapacity(Long.parseLong(createdVolume.getAllocatedCapacity()) * 1024);
            dbClient.updateObject(volume);
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

        Map<String, String> failedVolumes = new HashMap<String, String>();
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storageSystem, xtremioRestClientFactory);
            String clusterName = client.getClusterDetails(storageSystem.getSerialNumber()).getName();

            URI projectUri = volumes.get(0).getProject().getURI();
            URI poolUri = volumes.get(0).getPool();

            for (Volume volume : volumes) {
            	String volumeName = volume.getDeviceLabel() != null ? volume.getDeviceLabel() : volume.getLabel();
                try {                	
                    if (null != XtremIOProvUtils.isVolumeAvailableInArray(client, volumeName, clusterName)) {
                        // Remove the volume from the consistency group
                        if (client.isVersion2() && !NullColumnValueGetter.isNullURI(volume.getConsistencyGroup()) &&
                                NullColumnValueGetter.isNotNullValue(volume.getReplicationGroupInstance())) {
                            BlockConsistencyGroup consistencyGroupObj = dbClient.queryObject(BlockConsistencyGroup.class,
                                    volume.getConsistencyGroup());
                            String cgName = volume.getReplicationGroupInstance();

                            XtremIOConsistencyGroup xioCG = XtremIOProvUtils.isCGAvailableInArray(client,
                                    cgName, clusterName);
                            // Check if CG has volumes
                            if (null != xioCG && null != xioCG.getVolList() && !xioCG.getVolList().isEmpty()) {
                                boolean isVolRemovedFromCG = false;
                                // Verify if the volumes is part of the CG or not. If Exists always remove from CG
                                if (checkIfVolumeExistsInCG(xioCG.getVolList(), volumeName)) {
                                    _log.info("Removing volume {} from consistency group {}", volumeName,
                                            cgName);
                                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_038);
                                    client.removeVolumeFromConsistencyGroup(volumeName, cgName, clusterName);
                                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_039);
                                    isVolRemovedFromCG = true;
                                } else {
                                    _log.info("Volume {} doesn't exist on CG {}", volumeName, cgName);
                                }
                                // Perform remove CG only when we removed the volume from CG.
                                if (isVolRemovedFromCG) {
                                    // Query the CG to reflect the latest data on array.
                                    xioCG = XtremIOProvUtils.isCGAvailableInArray(client, cgName, clusterName);
                                    if (null == xioCG.getVolList() || xioCG.getVolList().isEmpty()) {
                                        InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_038);
                                        client.removeConsistencyGroup(cgName, clusterName);
                                        InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_039);
                                        _log.info("CG is empty on array. Remove array association from the CG");
                                        consistencyGroupObj.removeSystemConsistencyGroup(storageSystem.getId().toString(),
                                                cgName);
                                        // clear the LOCAL type
                                        StringSet types = consistencyGroupObj.getTypes();
                                        if (types != null) {
                                            types.remove(Types.LOCAL.name());
                                            consistencyGroupObj.setTypes(types);
                                        }

                                        dbClient.updateObject(consistencyGroupObj);
                                    }
                                }
                            }
                        }
                        // If this is a RecoverPoint volume (straight RP protection XIO volume or XIO VPlex
                        // backing volume).
                        if (volume.checkForRp() || RPHelper.isAssociatedToAnyRpVplexTypes(volume, dbClient)) {
                            int attempt = 0;
                            while (attempt++ <= MAX_RP_RETRIES) {
                                try {
                                    _log.info(String.format("Deleting RecoverPoint volume %s (attempt %s/%s)", volumeName,
                                            attempt,
                                            MAX_RP_RETRIES));
                                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_040);
                                    client.deleteVolume(volumeName, clusterName);
                                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_041);
                                    break;
                                } catch (XtremIOApiException e) {
                                    // RP/XIO source/target volumes are placed in consistency groups generated by XIO and RP, not ViPR.
                                    // There is occasionally some lag or delay in the communication between RP and XIO when a replication
                                    // set is removed. This results in the XIO volume not being removed from its CG before the delete
                                    // call is invoked which throws an exception. We isolate this exception below so we can wait and
                                    // retry the operation in hopes the proper communication has taken place between RP and XIO and the
                                    // volume removed from the consistency group.
                                    if (attempt != MAX_RP_RETRIES
                                            && e.getMessage().contains("cannot_remove_volume_that_is_in_consistency_group")) {
                                        _log.warn(String
                                                .format("Encountered exception attempting delete RP volume %s.  Waiting %s milliseconds before trying again.  Error: %s",
                                                        volumeName, RP_WAIT_FOR_RETRY, e.getMessage()));
                                        try {
                                            Thread.sleep(RP_WAIT_FOR_RETRY);
                                        } catch (InterruptedException e1) {
                                            Thread.currentThread().interrupt();
                                        }
                                    } else {
                                        // re-throw the exception if this is not the one we care about
                                        throw e;
                                    }
                                }
                            }
                        } else {
                            _log.info("Deleting the volume {}", volumeName);
                            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_040);
                            client.deleteVolume(volumeName, clusterName);
                            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_041);
                        }
                    }
                } catch (Exception e) {
                    _log.error("Error during volume {} delete.", volumeName, e);
                    failedVolumes.put(volumeName, ControllerUtils.getMessage(e));
                }
            }

            if (!failedVolumes.isEmpty()) {
                StringBuffer errMsg = new StringBuffer("Failed to delete volumes: ");
                for (String failedVolume : failedVolumes.keySet()) {
                    errMsg.append(failedVolume).append(":").append(failedVolumes.get(failedVolume));
                }
                ServiceError error = DeviceControllerErrors.xtremio.deleteVolumeFailure(errMsg.toString());
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

    /**
     * Verify whether the given volume exists in the CG Volume list or not.
     * If Exists return true, else false.
     *
     * @param volList - CG Volume List
     * @param volumeName - Volume name to check.
     * @return true if the volume Exists in CG
     *         false if the volume not found in CG.
     */
    private boolean checkIfVolumeExistsInCG(List<List<Object>> volList, String volumeName) {
        for (List<Object> vols : volList) {
            if (null != vols.get(1)) {
                // vols contains 3 volume related elements. The second element is the device
                // name, which we will use to match against the provided volume's label.
                String cgVolLabel = vols.get(1).toString();
                if (cgVolLabel.equalsIgnoreCase(volumeName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void doExportCreate(StorageSystem storage, ExportMask exportMask,
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
        _log.info("{} doExportCreate START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumeMap, dbClient);
        xtremioExportOperationHelper.createExportMask(storage, exportMask.getId(), volumeLunArray,
                targets, initiators, taskCompleter);
        _log.info("{} doExportCreate END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportDelete(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, List<URI> initiatorURIs, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportDelete START ...", storage.getSerialNumber());

        List<Initiator> initiators = Lists.newArrayList();
        if (initiatorURIs != null) {
            initiators.addAll(dbClient.queryObject(Initiator.class, initiatorURIs));
        }
        xtremioExportOperationHelper.deleteExportMask(storage, exportMask.getId(),
                volumeURIs, new ArrayList<URI>(), initiators,
                taskCompleter);
        _log.info("{} doExportDelete END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddVolume(StorageSystem storage, ExportMask exportMask, URI volume,
            Integer lun, List<Initiator> initiators, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        map.put(volume, lun);

        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), map, dbClient);
        xtremioExportOperationHelper.addVolumes(storage, exportMask.getId(), volumeLunArray,
                initiators, taskCompleter);
        _log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddVolumes(StorageSystem storage, ExportMask exportMask,
            List<Initiator> initiators, Map<URI, Integer> volumes, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _log.info("{} doExportAddVolumes START ...", storage.getSerialNumber());

        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumes, dbClient);
        xtremioExportOperationHelper.addVolumes(storage, exportMask.getId(), volumeLunArray,
                initiators, taskCompleter);
        _log.info("{} doExportAddVolumes END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveVolume(StorageSystem storage, ExportMask exportMask, URI volume,
            List<Initiator> initiators, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveVolumes START ...", storage.getSerialNumber());
        List<URI> volumeUris = new ArrayList<URI>();
        volumeUris.add(volume);
        xtremioExportOperationHelper.removeVolumes(storage, exportMask.getId(), volumeUris,
                initiators, taskCompleter);
        _log.info("{} doExportRemoveVolumes END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveVolumes(StorageSystem storage, ExportMask exportMask,
            List<URI> volumes, List<Initiator> initiators, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveVolumes START ...", storage.getSerialNumber());
        xtremioExportOperationHelper.removeVolumes(storage, exportMask.getId(), volumes,
                initiators, taskCompleter);
        _log.info("{} doExportRemoveVolumes END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddInitiator(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, Initiator initiator, List<URI> targets, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _log.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        List<Initiator> initiatorList = new ArrayList<Initiator>();
        initiatorList.add(initiator);
        xtremioExportOperationHelper.addInitiators(storage, exportMask.getId(), volumeURIs,
                initiatorList, targets, taskCompleter);
        _log.info("{} doExportAddInitiators END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddInitiators(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _log.info("{} doExportAddInitiators START ...", storage.getSerialNumber());
        xtremioExportOperationHelper.addInitiators(storage, exportMask.getId(), volumeURIs, initiators,
                targets, taskCompleter);
        _log.info("{} doExportAddInitiators END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveInitiator(StorageSystem storage, ExportMask exportMask,
            List<URI> volumes, Initiator initiator, List<URI> targets, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        List<Initiator> initiatorList = new ArrayList<Initiator>();
        initiatorList.add(initiator);
        xtremioExportOperationHelper.removeInitiators(storage, exportMask.getId(), volumes,
                initiatorList, targets, taskCompleter);
        _log.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveInitiators(StorageSystem storage, ExportMask exportMask,
            List<URI> volumes, List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiators START ...", storage.getSerialNumber());
        xtremioExportOperationHelper.removeInitiators(storage, exportMask.getId(), volumes,
                initiators, targets, taskCompleter);
        _log.info("{} doExportRemoveInitiators END ...", storage.getSerialNumber());
    }

    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("SnapShot Creation..... Started");
        List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, snapshotList);
        XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
        if (client.isVersion2() && ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, dbClient, taskCompleter)) {
            snapshotOperations.createGroupSnapshots(storage, snapshotList, createInactive, readOnly, taskCompleter);
        } else {
            for (URI snapshotURI : snapshotList) {
                snapshotOperations.createSingleVolumeSnapshot(storage, snapshotURI, createInactive,
                        readOnly, taskCompleter);
            }
        }
        _log.info("SnapShot Creation..... End");
    }

    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("SnapShot Deletion..... Started");
        List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));
        XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
        if (client.isVersion2() && ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, dbClient, taskCompleter)) {
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
        XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
        if (client.isVersion2() && ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, dbClient, taskCompleter)) {
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
        XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
        if (client.isVersion2() && ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, dbClient, taskCompleter)) {
            snapshotOperations.resyncGroupSnapshots(storage, volume, snapshot, taskCompleter);
        } else {
            snapshotOperations.resyncSingleVolumeSnapshot(storage, volume, snapshot, taskCompleter);
        }
        _log.info("SnapShot resync..... End");
    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage, final URI consistencyGroupId,
            String replicationGroupName, Boolean keepRGName, Boolean markInactive, final TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _log.info("{} doDeleteConsistencyGroup START ...", storage.getSerialNumber());

        ServiceError serviceError = null;

        try {
            // Check if the consistency group exists
            BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);
            URI systemURI = storage.getId();
            if (consistencyGroup == null || consistencyGroup.getInactive()) {
                _log.info(String.format("%s is inactive or deleted", consistencyGroupId));
                return;
            }

            String groupName = replicationGroupName != null ? replicationGroupName : consistencyGroup.getLabel();

            // This will be null, if consistencyGroup references no system CG's for storage.
            if (groupName == null) {
                _log.info(String.format("%s contains no system CG for %s.  Assuming it has already been deleted.",
                        consistencyGroupId, systemURI));
                // Clean up the system consistency group references
                BlockConsistencyGroupUtils.cleanUpCGAndUpdate(consistencyGroup, storage.getId(), groupName, markInactive, dbClient);
                return;
            }

            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            // We still need throw exception for standard CG.
            if (!client.isVersion2() && consistencyGroup.isProtectedCG()) {
                StringSet cgTypes = consistencyGroup.getTypes();
                cgTypes.remove(BlockConsistencyGroup.Types.LOCAL.name());
                consistencyGroup.setTypes(cgTypes);
                _log.info("{} Operation deleteConsistencyGroup not supported for the xtremio array version");
            } else {
                String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
                Project cgProject = dbClient.queryObject(Project.class, consistencyGroup.getProject());

                if (null != XtremIOProvUtils.isCGAvailableInArray(client, groupName, clusterName)) {
                    client.removeConsistencyGroup(groupName, clusterName);
                }

                if (null != XtremIOProvUtils.isTagAvailableInArray(client, cgProject.getLabel(),
                        XtremIOConstants.XTREMIO_ENTITY_TYPE.ConsistencyGroup.name(), clusterName)) {
                    client.deleteTag(cgProject.getLabel(), XtremIOConstants.XTREMIO_ENTITY_TYPE.ConsistencyGroup.name(), clusterName);
                }

                if (keepRGName) {
                    return;
                }

                // Clean up the system consistency group references
                BlockConsistencyGroupUtils.cleanUpCG(consistencyGroup, storage.getId(), groupName, markInactive, dbClient);
            }
            dbClient.updateObject(consistencyGroup);
            _log.info("{} doDeleteConsistencyGroup END ...", storage.getSerialNumber());
        } catch (Exception e) {
            _log.error(String.format("Delete Consistency Group operation failed %s", e));
            serviceError = DeviceControllerException.errors.jobFailed(e);
        } finally {
            if (serviceError != null) {
                taskCompleter.error(dbClient, serviceError);
            } else {
                taskCompleter.ready(dbClient);
            }
        }
    }

    @Override
    public void doCreateConsistencyGroup(StorageSystem storage, URI consistencyGroupId, String replicationGroupName,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doCreateConsistencyGroup START ...", storage.getSerialNumber());
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);
            boolean isXioV2 = client.isVersion2();
            if (!isXioV2 && consistencyGroup.isProtectedCG()) {
                _log.info("{} Operation createConsistencyGroup not supported for the xtremio array version");
            } else {
                String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
                Project cgProject = dbClient.queryObject(Project.class, consistencyGroup.getProject());
                String cgName = replicationGroupName != null ? replicationGroupName : consistencyGroup.getLabel();
                client.createConsistencyGroup(cgName, clusterName);
                String cgTagName = XtremIOProvUtils.createTagsForConsistencyGroup(client, cgProject.getLabel(), clusterName);
                consistencyGroup.addSystemConsistencyGroup(storage.getId().toString(), cgName);
                consistencyGroup.addConsistencyGroupTypes(Types.LOCAL.name());
                client.tagObject(cgTagName, XTREMIO_ENTITY_TYPE.ConsistencyGroup.name(), cgName, clusterName);
                if (!consistencyGroup.isProtectedCG() && NullColumnValueGetter.isNullURI(consistencyGroup.getStorageController())) {
                    consistencyGroup.setStorageController(storage.getId());
                }
            }
            dbClient.updateObject(consistencyGroup);
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
            URI consistencyGroupId, String replicationGroupName, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doAddToConsistencyGroup START ...", storage.getSerialNumber());
        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);
        try {
            // Check if the consistency group exists
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            if (!client.isVersion2()) {
                _log.info("Nothing to add to consistency group {}", consistencyGroup.getLabel());
                taskCompleter.ready(dbClient);
                return;
            }
            String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            String cgName = replicationGroupName != null ? replicationGroupName : consistencyGroup.getLabel();

            XtremIOConsistencyGroup cg = XtremIOProvUtils.isCGAvailableInArray(client, cgName, clusterName);
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
                    if (blockObject.getClass().isInstance(Volume.class)) {
                        Volume volume = (Volume) blockObject;
                        if (volume.checkForRp()
                                || RPHelper.isAssociatedToRpVplexType(volume, dbClient,
                                        PersonalityTypes.METADATA, PersonalityTypes.TARGET)) {
                            // Do not add RP+VPlex journal or target backing volumes to consistency groups.
                            // This causes issues with local array snapshots of RP+VPlex volumes.
                            continue;
                        }
                    }
                    client.addVolumeToConsistencyGroup(blockObject.getLabel(), cgName, clusterName);
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
        String groupName = null;
        try {
            // get the group name from one of the block objects; we expect all of them to be the same group
            Iterator<URI> itr = blockObjects.iterator();
            while (itr.hasNext()) {
                BlockObject blockObject = BlockObject.fetch(dbClient, itr.next());
                if (blockObject != null && !blockObject.getInactive()
                        && !NullColumnValueGetter.isNullValue(blockObject.getReplicationGroupInstance())) {
                    groupName = blockObject.getReplicationGroupInstance();
                    break;
                }
            }

            // Check if the replication group exists
            if (groupName != null) {
                // Check if the consistency group exists
                XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
                if (!client.isVersion2()) {
                    _log.info("Nothing to remove from consistency group {}", consistencyGroup.getLabel());
                    taskCompleter.ready(dbClient);
                    return;
                }

                String clusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
                XtremIOConsistencyGroup cg = XtremIOProvUtils.isCGAvailableInArray(client, groupName, clusterName);
                if (cg == null) {
                    _log.error("The consistency group does not exist in the array: {}", storage.getSerialNumber());
                    taskCompleter.error(dbClient, DeviceControllerException.exceptions
                            .consistencyGroupNotFound(groupName,
                                    consistencyGroup.getCgNameOnStorageSystem(storage.getId())));
                    return;
                }
                for (URI uri : blockObjects) {
                    BlockObject blockObject = BlockObject.fetch(dbClient, uri);
                    if (blockObject != null) {
                        client.removeVolumeFromConsistencyGroup(blockObject.getLabel(),
                                blockObject.getReplicationGroupInstance(), clusterName);
                        blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                        blockObject.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                        dbClient.updateObject(blockObject);
                    }
                }
            }

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
    public void doDeleteConsistencyGroup(StorageSystem storage, final URI consistencyGroupId,
            String replicationGroupName, Boolean keepRGName, Boolean markInactive,
            String sourceReplicationGroup, final TaskCompleter taskCompleter) throws DeviceControllerException {
        doDeleteConsistencyGroup(storage, consistencyGroupId, replicationGroupName, keepRGName, markInactive, taskCompleter);
    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) throws DeviceControllerException {
        return new HashMap<String, Set<URI>>();
    }

    @Override
    public Set<Integer> findHLUsForInitiators(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts) {
        return xtremioExportOperationHelper.findHLUsForInitiators(storage, initiatorNames, mustHaveAllPorts);
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) throws DeviceControllerException {
        return xtremioExportOperationHelper.refreshExportMask(storage, mask);
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        return true;
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
