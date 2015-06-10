/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm.xiv;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MultiVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMCIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMSmisConstants;

/*
 * Handle SMI-S result.
 */
public class XIVSmisStorageDevicePostProcessor {
    private static final Logger _log = LoggerFactory
            .getLogger(XIVSmisStorageDevicePostProcessor.class);
    private XIVSmisCommandHelper _helper;
    private IBMCIMObjectPathFactory _cimPath;
    private CIMConnectionFactory _cimConnection = null;
    private DbClient _dbClient = null;

    public void setCimObjectPathFactory(
            IBMCIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setSmisCommandHelper(XIVSmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setCimConnectionFactory(CIMConnectionFactory connectionFactory) {
        _cimConnection = connectionFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /*
     * (non-Javadoc) Update DB with volume creation output from SMI-S.
     * 
     * @see
     * com.emc.storageos.volumecontroller.impl.smis.job.SmisAbstractCreateVolumeJob
     * #updateStatus
     */
    public Set<URI> processVolumeCreation(StorageSystem storageSystem,
            URI storagePoolURI, List<Volume> volumes, CIMArgument[] outArgs)
            throws Exception {
        Set<URI> volumeURIs = new HashSet<URI>(volumes.size());
        StringBuilder logMsgBuilder = new StringBuilder(
                String.format("Processing volume creation"));
        CimConnection connection = _cimConnection.getConnection(storageSystem);
        WBEMClient client = connection.getCimClient();
        Calendar now = Calendar.getInstance();

        StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                storagePoolURI);
        updateStoragePoolCapacity(client, storagePool);
        StringMap reservationMap = storagePool.getReservedCapacityMap();
        for (Volume volume : volumes) {
            reservationMap.remove(volume.getId().toString());
        }

        _dbClient.persistObject(storagePool);

        CIMObjectPath[] elements = (CIMObjectPath[]) _cimPath
                .getFromOutputArgs(outArgs, IBMSmisConstants.CP_THE_ELEMENTS);
        UnsignedInteger32[] returnCoedes = (UnsignedInteger32[]) _cimPath
                .getFromOutputArgs(outArgs, IBMSmisConstants.CP_RETURN_CODES);
        List<Volume> volumesToSave = new ArrayList<Volume>(elements.length);
        if (elements != null && returnCoedes != null) {
            for (int i = 0; i < elements.length; i++) {
                URI volumeId = volumes.get(i).getId();
                Volume volume = _dbClient.queryObject(Volume.class, volumeId);
                volumesToSave.add(volume);
                volumeURIs.add(volumeId);

                boolean isSuccess = false;
                CIMObjectPath volumePath = elements[i];
                if (volumePath != null) {
                    CIMProperty<String> deviceID = (CIMProperty<String>) volumePath
                            .getKey(IBMSmisConstants.CP_DEVICE_ID);
                    if (deviceID != null) {
                        String nativeID = deviceID.getValue();
                        if ((nativeID != null) && (nativeID.length() != 0)) {
                            isSuccess = true;
                            volume.setPool(storagePoolURI);
                            processVolume(volumePath, nativeID, volume, client,
                                    logMsgBuilder, now);
                        }
                    }
                }

                if (!isSuccess) {
                    logMsgBuilder.append("\n");
                    logMsgBuilder.append(String.format(
                            "Failed to create volume: %s with return code: %s",
                            volumeId, returnCoedes[i].toString()));
                    volume.setInactive(true);
                }
            }
        }

        if (!volumesToSave.isEmpty()) {
            _dbClient.persistObject(volumesToSave);
        }

        _log.info(logMsgBuilder.toString());
        return volumeURIs;
    }

    /*
     * (non-Javadoc) Update storage pool capacity.
     * 
     * totalCapacity - Current total storage capacity held by the pool.
     * freeCapacity - Total free capacity available for allocating volumes from
     * the pool. subscribedCapacity - In case of ThinPools, this would indicate
     * how much real storage is being used by the allocated devices in the pool.
     * 
     * Note - EMC_VirtualProvisioningPool.EMCSubscribedCapacity - The amount of
     * storage capacity subscribed by all elements in the storage pool.
     * 
     * IBMTSDS_VirtualPool.VirtualSpaceConsumed - The consumed amount of raw
     * storage (in bytes) from the ConsumedVirtualSpace of this StoragePool.
     * IBMTSDS_VirtualPool.VirtualSpaceReserved - The total amount of raw
     * storage (in bytes) reserved for Virtual Space on this StoragePool,
     * corresponding to value of the property 'hard_size_MiB' of XCLI command
     * pool_list. IBMTSDS_VirtualPool.TotalManagedSpace - The total amount of
     * virtual capacity (in bytes) managed by this StoragePool, corresponding to
     * value of property 'soft_size_MiB' of XCLI command pool_list.
     * IBMTSDS_VirtualPool.RemainingManagedSpace - The remaining amount of
     * virtual capacity (in bytes) from the TotalManagedSpace of this
     * StoragePool, corresponding to value of property 'soft_size_MiB.pool_list
     * - used_by_volumes_MiB - used_by_snapshots_MiB' of XCLI command pool_list.
     * This represents the amount of space not allocated to any volumes and
     * snapshots.
     */
    private void updateStoragePoolCapacity(WBEMClient client,
            StoragePool storagePool) {
        try {
            _log.info(String
                    .format("Old storage pool capacity data for pool %s - free capacity: %s; subscribed capacity: %s",
                            storagePool.getNativeId(),
                            storagePool
                                    .calculateFreeCapacityWithoutReservations(),
                            storagePool.getSubscribedCapacity()));

            CIMObjectPath poolPath = _cimPath.getStoragePoolPath(storagePool);
            CIMInstance poolInstance = client.getInstance(poolPath, true,
                    false, IBMSmisConstants.PS_POOL_CAPACITIES);

            // get capacity properties and update storage pool
            Long freePoolCapacity = SmisUtils.getFreeCapacity(poolInstance);
            storagePool.setFreeCapacity(freePoolCapacity);
            String subscribedCapacity = SmisUtils.getCIMPropertyValue(
                    poolInstance, IBMSmisConstants.CP_VIRTUAL_SPACE_CONSUMED);
            if (null != subscribedCapacity) {
                storagePool.setSubscribedCapacity(ControllerUtils
                        .convertBytesToKBytes(subscribedCapacity));
            }

            _log.info(String
                    .format("New storage pool capacity data for pool %s - free capacity: %s; subscribed capacity: %s",
                            storagePool.getNativeId(),
                            storagePool.getFreeCapacity(),
                            storagePool.getSubscribedCapacity()));

            _dbClient.persistObject(storagePool);
        } catch (Throwable th) {
            _log.error(
                    String.format(
                            "Failed to update capacity of storage pool after volume provisioning operation. Storage pool %s .",
                            storagePool.getNativeId()), th);
        }
    }

    /*
     * (non-Javadoc) Processes a newly created volume.
     * 
     * @param volumePath The CIM object path for the volume.
     * 
     * @param nativeID The native volume identifier.
     * 
     * @param volumeId The Bourne volume id.
     * 
     * @param client The CIM client.
     * 
     * @param logMsgBuilder Holds a log message.
     * 
     * @param creationTime Holds the date-time for the volume creation
     * 
     * @throws java.io.IOException When an error occurs querying the database.
     * 
     * @throws DatabaseException
     */
    private void processVolume(CIMObjectPath volumePath, String nativeID,
            Volume volume, WBEMClient client, StringBuilder logMsgBuilder,
            Calendar creationTime) throws DatabaseException {
        CIMInstance volumeInstance = null;
        try {
            volume.setCreationTime(creationTime);
            volume.setNativeId(nativeID);
            volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(
                    _dbClient, volume));
            volumeInstance = client.getInstance(volumePath, true, false, null);
            if (volumeInstance != null) {
                String wwn = CIMPropertyFactory.getPropertyValue(
                        volumeInstance, IBMSmisConstants.CP_NAME);
                volume.setAlternateName(wwn);
                volume.setWWN(wwn.toUpperCase());
                String elementName = CIMPropertyFactory.getPropertyValue(
                        volumeInstance, IBMSmisConstants.CP_ELEMENT_NAME);
                // should have already been set outside controller, just to make
                // sure there is no mismatch
                volume.setLabel(elementName);
                volume.setDeviceLabel(elementName);

                volume.setProvisionedCapacity(getProvisionedCapacityInformation(volumeInstance));
                volume.setAllocatedCapacity(getAllocatedCapacityInformation(
                        client, volumeInstance));

                volume.setInactive(false);
            } else {
                volume.setInactive(true);
            }
        } catch (IOException e) {
            _log.error(
                    "Caught an exception while trying to update volume attributes",
                    e);
        } catch (WBEMException e) {
            _log.error(
                    "Caught an exception while trying to update volume attributes",
                    e);
        }

        if (logMsgBuilder.length() != 0) {
            logMsgBuilder.append("\n");
        }
        logMsgBuilder.append(String.format(
                "Created volume successfully .. NativeId: %s", nativeID));
    }

    /*
     * (non-Javadoc) Get provisioned capacity.
     * 
     * @see SmisReplicaCreationJobs#getProvisionedCapacityInformation
     */
    private long getProvisionedCapacityInformation(CIMInstance volumeInstance) {
        Long provisionedCapacity = 0L;
        try {
            if (volumeInstance != null) {
                CIMProperty consumableBlocks = volumeInstance
                        .getProperty(IBMSmisConstants.CP_CONSUMABLE_BLOCKS);
                CIMProperty blockSize = volumeInstance
                        .getProperty(IBMSmisConstants.CP_BLOCK_SIZE);
                // calculate provisionedCapacity = consumableBlocks * block size
                provisionedCapacity = Long.valueOf(consumableBlocks.getValue()
                        .toString())
                        * Long.valueOf(blockSize.getValue().toString());
            }
        } catch (Exception e) {
            _log.error("Updating ProvisionedCapacity failed for Volume {}",
                    volumeInstance.getObjectPath(), e);
        }
        return provisionedCapacity;
    }

    /*
     * (non-Javadoc) Get allocated capacity.
     * 
     * @see SmisReplicaCreationJobs#getAllocatedCapacityInformation
     */
    protected long getAllocatedCapacityInformation(WBEMClient client,
            CIMInstance volumeInstance) {
        Long allocatedCapacity = 0L;
        CloseableIterator<CIMInstance> iterator = null;
        // TODO - use VirtualSpaceConsumed from SEVolume
        try {
            if (volumeInstance != null) {
                iterator = client.referenceInstances(
                        volumeInstance.getObjectPath(),
                        IBMSmisConstants.CIM_ALLOCATED_FROM_STORAGEPOOL, null,
                        false, IBMSmisConstants.PS_SPACE_CONSUMED);
                if (iterator.hasNext()) {
                    CIMInstance allocatedFromStoragePoolPath = iterator.next();
                    CIMProperty spaceConsumed = allocatedFromStoragePoolPath
                            .getProperty(IBMSmisConstants.CP_SPACE_CONSUMED);
                    allocatedCapacity = Long.valueOf(spaceConsumed.getValue()
                            .toString());
                }
            }
        } catch (Exception e) {
            _log.error("Updating Allocated Capacity failed for Volume {}",
                    volumeInstance.getObjectPath(), e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }

        return allocatedCapacity;
    }

    /**
     * Update DB with SMI-S output.
     */
    public void processVolumeExpansion(StorageSystem storageSystem,
            URI storagePoolURI, URI volumeId, CIMArgument[] outArgs)
            throws Exception {
        StringBuilder logMsgBuilder = new StringBuilder(
                String.format("Processing volume expansion - "));
        CimConnection connection = _cimConnection.getConnection(storageSystem);
        WBEMClient client = connection.getCimClient();

        StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                storagePoolURI);
        StringMap reservationMap = storagePool.getReservedCapacityMap();
        reservationMap.remove(volumeId.toString());

        updateStoragePoolCapacity(client, storagePool);
        _dbClient.persistObject(storagePool);

        Volume volume = _dbClient.queryObject(Volume.class, volumeId);
        CIMObjectPath volumePath = (CIMObjectPath) _cimPath.getFromOutputArgs(
                outArgs, IBMSmisConstants.CP_THE_ELEMENT);
        boolean isSuccess = false;
        if (volumePath != null) {
            CIMInstance volumeInstance = client.getInstance(volumePath, true,
                    false, null);
            if (volumeInstance != null) {
                isSuccess = true;
                volume.setProvisionedCapacity(getProvisionedCapacityInformation(volumeInstance));
                volume.setAllocatedCapacity(getAllocatedCapacityInformation(
                        client, volumeInstance));
                _dbClient.persistObject(volume);
                logMsgBuilder
                        .append(String
                                .format("\n   Capacity: %s, Provisioned capacity: %s, Allocated Capacity: %s",
                                        volume.getCapacity(),
                                        volume.getProvisionedCapacity(),
                                        volume.getAllocatedCapacity()));
            }
        }

        if (!isSuccess) {
            UnsignedInteger32 returnCoede = (UnsignedInteger32) _cimPath
                    .getFromOutputArgs(outArgs, IBMSmisConstants.CP_RETURN_CODE);
            logMsgBuilder.append("\n");
            logMsgBuilder.append(String.format(
                    "Failed to expand volume: %s with return code: %s",
                    volume.getId(), returnCoede.toString()));
        }

        _log.info(logMsgBuilder.toString());
    }

    /**
     * Update DB with SMI-S output, also set task completer status.
     */
    public List<Volume> processVolumeDeletion(StorageSystem storageSystem,
            List<Volume> volumes, CIMArgument[] outArgs,
            MultiVolumeTaskCompleter multiVolumeTaskCompleter) throws Exception {
        CimConnection connection = _cimConnection.getConnection(storageSystem);
        WBEMClient client = connection.getCimClient();
        List<Volume> volumesToProcess = new ArrayList<Volume>();
        for (Volume vol : volumes) {
            Volume volume = _dbClient.queryObject(Volume.class, vol.getId());
            volumesToProcess.add(volume);
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                    volume.getPool());
            updateStoragePoolCapacity(client, storagePool);
        }

        StringBuilder logMsgBuilder = new StringBuilder();
        UnsignedInteger32[] returnCoedes = (UnsignedInteger32[]) _cimPath
                .getFromOutputArgs(outArgs, IBMSmisConstants.CP_RETURN_CODES);
        List<Volume> volumesToSave = new ArrayList<Volume>(returnCoedes.length);
        for (int i = 0; i < returnCoedes.length; i++) {
            Volume volume = volumesToProcess.get(i);
            VolumeTaskCompleter deleteTaskCompleter = multiVolumeTaskCompleter
                    .skipTaskCompleter(volume.getId());
            if (returnCoedes[i].longValue() == 0L) {
                volume.setInactive(true);
                volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                _dbClient.updateAndReindexObject(volume);
                deleteTaskCompleter.ready(_dbClient);

                if (logMsgBuilder.length() != 0) {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format(
                        "Successfully deleted volume %s", volume.getId()));
            } else {
                // cannot delete volume
                String errorMessage = String
                        .format("Failed to delete volume: %s , nativeId: %s with return code: %s",
                                volume.getId(), volume.getNativeId(),
                                returnCoedes[i].toString());
                ServiceError error = DeviceControllerErrors.smis.methodFailed(
                        "doDeleteVolume", errorMessage);
                deleteTaskCompleter.error(_dbClient, error);

                if (logMsgBuilder.length() != 0) {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(errorMessage);
            }
        }

        if (logMsgBuilder.length() > 0) {
            _log.info(logMsgBuilder.toString());
        }

        return volumesToSave;
    }
    
    @SuppressWarnings("rawtypes")
    public void processSnapshotCreation(StorageSystem storageSystem,
            URI snapshotURI, boolean wantSyncActive, CIMArgument[] outArgs,
            BlockSnapshotCreateCompleter taskCompleter) throws Exception {
        StringBuilder logMsgBuilder = new StringBuilder(
                String.format("Processing snapshot creation - "));
        CimConnection connection = _cimConnection.getConnection(storageSystem);
        WBEMClient client = connection.getCimClient();
        BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class,
                snapshotURI);

        CIMObjectPath syncVolumePath = (CIMObjectPath) _cimPath
                .getFromOutputArgs(outArgs, IBMSmisConstants.CP_TARGET_ELEMENT);
        if (syncVolumePath != null) {
            // Get the sync volume native device id
            CIMInstance syncVolume = client.getInstance(syncVolumePath, false,
                    false, null);
            String syncDeviceID = syncVolumePath
                    .getKey(IBMSmisConstants.CP_DEVICE_ID).getValue()
                    .toString();
            String elementName = CIMPropertyFactory.getPropertyValue(
                    syncVolume, IBMSmisConstants.CP_ELEMENT_NAME);
            String wwn = CIMPropertyFactory.getPropertyValue(syncVolume,
                    IBMSmisConstants.CP_NAME);

            snapshot.setNativeId(syncDeviceID);
            snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(
                    storageSystem, snapshot));
            snapshot.setLabel(elementName);
            snapshot.setDeviceLabel(elementName);
            snapshot.setInactive(false);
            snapshot.setIsSyncActive(wantSyncActive);
            snapshot.setCreationTime(Calendar.getInstance());
            snapshot.setWWN(wwn.toUpperCase());
            snapshot.setAlternateName(wwn);

            snapshot.setProvisionedCapacity(getProvisionedCapacityInformation(syncVolume));
            snapshot.setAllocatedCapacity(getAllocatedCapacityInformation(
                    client, syncVolume));

            logMsgBuilder
                    .append(String
                            .format("For sync volume path %1$s, going to set blocksnapshot %2$s nativeId to %3$s.",
                                    syncVolumePath.toString(), snapshot.getId()
                                            .toString(), syncDeviceID));

            taskCompleter.ready(_dbClient);
        } else {
            snapshot.setInactive(true);
            ServiceError error = DeviceControllerErrors.smis
                    .unableToFindSynchPath("");
            logMsgBuilder.append("Failed due to no target element path");
            taskCompleter.error(_dbClient, error);
        }

        _dbClient.persistObject(snapshot);
        _log.info(logMsgBuilder.toString());
    }

    @SuppressWarnings("rawtypes")
    public void processCloneCreation(StorageSystem storageSystem,
            URI volumeURI, CIMArgument[] outArgs,
            CloneCreateCompleter taskCompleter) throws Exception {
        StringBuilder logMsgBuilder = new StringBuilder(
                String.format("Processing clone creation - "));
        CimConnection connection = _cimConnection.getConnection(storageSystem);
        WBEMClient client = connection.getCimClient();
        Volume cloneVolume = _dbClient.queryObject(Volume.class, volumeURI);
        URI poolURI = cloneVolume.getPool();
        StoragePool pool = _dbClient.queryObject(StoragePool.class, poolURI);
        updateStoragePoolCapacity(client, pool);
        StringMap reservationMap = pool.getReservedCapacityMap();
        // remove from reservation map
        reservationMap.remove(cloneVolume.getId().toString());
        _dbClient.persistObject(pool);

        CIMObjectPath cloneVolumePath = (CIMObjectPath) _cimPath
                .getFromOutputArgs(outArgs, IBMSmisConstants.CP_TARGET_ELEMENT);
        if (cloneVolumePath != null) {
            // Get the sync volume native device id
            CIMInstance syncVolume = client.getInstance(cloneVolumePath, false,
                    false, null);
            String deviceID = cloneVolumePath
                    .getKey(IBMSmisConstants.CP_DEVICE_ID).getValue()
                    .toString();
            String elementName = CIMPropertyFactory.getPropertyValue(
                    syncVolume, IBMSmisConstants.CP_ELEMENT_NAME);
            String wwn = CIMPropertyFactory.getPropertyValue(syncVolume,
                    IBMSmisConstants.CP_NAME);

            cloneVolume.setNativeId(deviceID);
            cloneVolume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(
                    _dbClient, cloneVolume));
            cloneVolume.setLabel(elementName);
            cloneVolume.setDeviceLabel(elementName);
            cloneVolume.setInactive(false);
            cloneVolume.setCreationTime(Calendar.getInstance());
            cloneVolume.setWWN(wwn.toUpperCase());
            cloneVolume.setAlternateName(wwn);
            cloneVolume
                    .setProvisionedCapacity(getProvisionedCapacityInformation(syncVolume));
            cloneVolume.setAllocatedCapacity(getAllocatedCapacityInformation(
                    client, syncVolume));

            logMsgBuilder
                    .append(String
                            .format("For sync volume path %1$s, going to set blocksnapshot %2$s nativeId to %3$s.",
                                    cloneVolumePath.toString(), cloneVolume
                                            .getId().toString(), deviceID));

            taskCompleter.ready(_dbClient);
        } else {
            cloneVolume.setInactive(true);
            ServiceError error = DeviceControllerErrors.smis
                    .unableToFindSynchPath("");
            logMsgBuilder.append("Failed due to no target element path");
            taskCompleter.error(_dbClient, error);
        }

        _dbClient.persistObject(cloneVolume);
        _log.info(logMsgBuilder.toString());
    }

    @SuppressWarnings("rawtypes")
    public void processCGSnapshotCreation(StorageSystem storageSystem,
            List<URI> snapshotURIs, boolean wantSyncActive,
            String snapshotGroupName, BlockSnapshotCreateCompleter taskCompleter)
            throws Exception {
        _log.info("Processing CG snapshot creation");
        CloseableIterator<CIMObjectPath> volumeIter = null;
        CimConnection connection = _cimConnection.getConnection(storageSystem);
        WBEMClient client = connection.getCimClient();

        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(
                    BlockSnapshot.class, taskCompleter.getSnapshotURIs());
            // Create mapping of volume.nativeDeviceId to BlockSnapshot object
            Map<String, BlockSnapshot> volumeToSnapMap = new HashMap<String, BlockSnapshot>();
            for (BlockSnapshot snapshot : snapshots) {
                Volume volume = _dbClient.queryObject(Volume.class,
                        snapshot.getParent());
                volumeToSnapMap.put(volume.getNativeId(), snapshot);
            }

            // Iterate through the snapshot elements and try to match them up
            // with the appropriate BlockSnapshot
            
            // Note, NULL_IBM_CIM_OBJECT_PATH is used here. The snapshot group object will be looked up by snapshot group name
            List<CIMObjectPath> objectPaths = _helper.getSGMembers(storageSystem, SmisConstants.NULL_IBM_CIM_OBJECT_PATH, snapshotGroupName, snapshotURIs.size());
            List<BlockSnapshot> objectsToSave = new ArrayList<BlockSnapshot>(
                    snapshotURIs.size());
            Calendar now = Calendar.getInstance();
            for (CIMObjectPath syncVolumePath : objectPaths) {
                CIMInstance syncVolume = client.getInstance(syncVolumePath,
                        false, false, null);
                String syncDeviceID = syncVolumePath
                        .getKey(IBMSmisConstants.CP_DEVICE_ID).getValue()
                        .toString();
                String elementName = CIMPropertyFactory.getPropertyValue(
                        syncVolume, IBMSmisConstants.CP_ELEMENT_NAME);
                // Get the associated volume for this sync volume
                CIMObjectPath volumePath = null;
                volumeIter = client.associatorNames(syncVolumePath, null,
                        IBMSmisConstants.CIM_STORAGE_VOLUME, null, null);
                volumePath = volumeIter.next();
                if (_log.isDebugEnabled()) {
                    _log.debug("volumePath - " + volumePath);
                }

                volumeIter.close();
                String volumeDeviceID = volumePath
                        .getKey(IBMSmisConstants.CP_DEVICE_ID).getValue()
                        .toString();
                String wwn = CIMPropertyFactory.getPropertyValue(syncVolume,
                        IBMSmisConstants.CP_NAME);
                String alternativeName = wwn;
                // Lookup the associated snapshot based on the volume native
                // device id
                BlockSnapshot snapshot = volumeToSnapMap.get(volumeDeviceID);
                if (snapshot != null) {
                    snapshot.setNativeId(syncDeviceID);
                    snapshot.setNativeGuid(NativeGUIDGenerator
                            .generateNativeGuid(storageSystem, snapshot));
                    snapshot.setReplicationGroupInstance(snapshotGroupName);
                    snapshot.setLabel(elementName);
                    snapshot.setDeviceLabel(elementName);
                    snapshot.setInactive(false);
                    snapshot.setIsSyncActive(wantSyncActive);
                    snapshot.setCreationTime(now);
                    snapshot.setWWN(wwn.toUpperCase());
                    snapshot.setAlternateName(alternativeName);

                    snapshot.setProvisionedCapacity(getProvisionedCapacityInformation(syncVolume));
                    snapshot.setAllocatedCapacity(getAllocatedCapacityInformation(
                            client, syncVolume));

                    _log.info(String
                            .format("For sync volume path %1$s, going to set blocksnapshot %2$s nativeId to %3$s (%4$s). "
                                    + "Replication Group instance is %5$s. Associated volume is %6$s",
                                    syncVolumePath.toString(), snapshot.getId()
                                            .toString(), syncDeviceID,
                                    elementName, snapshotGroupName, volumePath.toString()));
                    objectsToSave.add(snapshot);
                }
            }

            if (objectsToSave.size() > 0) {
                _dbClient.persistObject(objectsToSave);
                taskCompleter.ready(_dbClient);
            } else {
                _log.info("Failed to create snapshot");
                for (BlockSnapshot snapshot : snapshots) {
                    snapshot.setInactive(true);
                }

                _dbClient.persistObject(snapshots);

                ServiceError error = DeviceControllerErrors.smis
                        .noBlockSnapshotsFound();
                taskCompleter.error(_dbClient, error);
            }
        } catch (Exception e) {
            _log.error(
                    "Caught an exception while trying to process CG snapshotCreation",
                    e);
            throw e;
        } finally {
            if (volumeIter != null) {
                volumeIter.close();
            }
        }
    }
}
