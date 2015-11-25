/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.externaldevice;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.LockManagerImpl;
import com.emc.storageos.storagedriver.impl.RegistryImpl;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;


public class ExternalBlockStorageDevice extends DefaultBlockStorageDevice {

    private Logger _log = LoggerFactory.getLogger(ExternalBlockStorageDevice.class);
    private Map<String, AbstractStorageDriver> drivers;
    private DbClient dbClient;
    private ControllerLockingService locker;
    // Initialized drivers map
    private Map<String, BlockStorageDriver> blockDrivers  = new HashMap<>();


    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setLocker(ControllerLockingService locker) {
        this.locker = locker;
    }

    public void setDrivers(Map<String, AbstractStorageDriver> drivers) {
        this.drivers = drivers;
    }

    private BlockStorageDriver getDriver(String driverType) {
        // look up driver
        BlockStorageDriver storageDriver = blockDrivers.get(driverType);
        if (storageDriver != null) {
            return storageDriver;
        } else {
            // init driver
            AbstractStorageDriver driver = drivers.get(driverType);
            if (driver == null) {
                _log.error("No driver entry defined for device type: {} . ", driverType);
                throw ExternalDeviceException.exceptions.noDriverDefinedForDevice(driverType);
            }
            init(driver);
            blockDrivers.put(driverType, driver);
            return driver;
        }
    }

    private void init(AbstractStorageDriver driver) {
        Registry driverRegistry = RegistryImpl.getInstance();
        driver.setDriverRegistry(driverRegistry);
        LockManager lockManager = LockManagerImpl.getInstance(locker);
        driver.setLockManager(lockManager);
    }



        /**
         *  {@inheritDoc}
         *
         */
    @Override
    public void doCreateVolumes(StorageSystem storageSystem, StoragePool storagePool,
                                String opId, List<Volume> volumes,
                                VirtualPoolCapabilityValuesWrapper capabilities,
                                TaskCompleter taskCompleter) throws DeviceControllerException {

        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());

        List<StorageVolume> driverVolumes = new ArrayList<>();
        Map<StorageVolume, Volume> driverVolumeToVolumeMap = new HashMap<>();
        Set<URI> consistencyGroups = new HashSet<>();
        try {
            for (Volume volume : volumes) {
                StorageVolume driverVolume = new StorageVolume();
                driverVolume.setStorageSystemId(storageSystem.getNativeId());
                driverVolume.setStoragePoolId(storagePool.getNativeId());
                driverVolume.setRequestedCapacity(volume.getCapacity());
                driverVolume.setThinlyProvisioned(volume.getThinlyProvisioned());
                if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                    BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());
                    driverVolume.setConsistencyGroup(cg.getLabel());
                    consistencyGroups.add(volume.getConsistencyGroup());
                }
                // Todo complete attribute setting.

                driverVolumes.add(driverVolume);
                driverVolumeToVolumeMap.put(driverVolume, volume);
            }

            DriverTask task = driver.createVolumes(driverVolumes, null);
            // TODO: this is short cut for now, assuming synchronous driver implementation
            // We will implement support for async case later.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                updateConsistencyGroupsWithStorageSystem(consistencyGroups, storageSystem);
                updateVolumesWithDriverVolumeInfo(dbClient, driverVolumeToVolumeMap);
                dbClient.updateObject(driverVolumeToVolumeMap.values());
                String msg = String.format("doCreateVolumes -- Created volumes: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                // failed
                // TODO support async
                // Set volumes to inactive state
                for (Volume volume : volumes) {
                    volume.setInactive(true);
                }
                dbClient.updateObject(volumes);
                String errorMsg = String.format("doCreateVolumes -- Failed to create volumes: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createVolumesFailed("doCreateVolumes", errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (IOException e) {
            _log.error("doCreateVolumes -- Failed to create volumes. ", e);
            ServiceError serviceError = ExternalDeviceException.errors.createVolumesFailed("doCreateVolumes", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
    }

    /**
     *  {@inheritDoc}
     *
     */
    @Override
    public void doExpandVolume(StorageSystem storageSystem, StoragePool storagePool,
                               Volume volume, Long size, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    public void doDeleteVolumes(StorageSystem storageSystem, String opId,
                                List<Volume> volumes, TaskCompleter taskCompleter) throws DeviceControllerException {

        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());

        List<Volume> deletedVolumes = new ArrayList<>();
        List<String> failedToDelete = new ArrayList<>();
        for (Volume volume : volumes) {
            StorageVolume driverVolume = new StorageVolume();
            driverVolume.setStorageSystemId(storageSystem.getNativeId());
            driverVolume.setNativeId(volume.getNativeId());
            DriverTask task = driver.deleteVolumes(Collections.singletonList(driverVolume));
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                volume.setInactive(true);
                deletedVolumes.add(volume);
            } else {
                failedToDelete.add(volume.getNativeId());
            }
        }
        if (!deletedVolumes.isEmpty()){
            _log.info("Deleted volumes on storage system {}, volumes: {} .",
                    storageSystem.getNativeId(), deletedVolumes.toString());
            dbClient.updateObject(deletedVolumes);
        }

        if(!failedToDelete.isEmpty()) {
            String errorMsg = String.format("Failed to delete volumes on storage system %s, volumes: %s .",
                    storageSystem.getNativeId(), failedToDelete.toString());
            _log.error(errorMsg);
            ServiceError serviceError = ExternalDeviceException.errors.deleteVolumesFailed("doDeleteVolumes", errorMsg);
            taskCompleter.error(dbClient, serviceError);
        } else {
            taskCompleter.ready(dbClient);
        }
    }

    @Override
    public void doCreateSingleSnapshot(StorageSystem storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {
        super.doCreateSingleSnapshot(storage, snapshotList, createInactive, readOnly, taskCompleter);
    }

    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            Iterator<BlockSnapshot> snapshots = dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotList);
            List<BlockSnapshot> blockSnapshots = new ArrayList<>();
            while (snapshots.hasNext()) {
               blockSnapshots.add(snapshots.next());
            }

            if (ControllerUtils.checkSnapshotsInConsistencyGroup(blockSnapshots, dbClient, taskCompleter)) {
                // all snapshots should be for the same CG (enforced by controller)
                createGroupSnapshots(storage, blockSnapshots, createInactive, readOnly, taskCompleter);
            } else {
                createVolumeSnapshots(storage, blockSnapshots, createInactive, readOnly, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String.format("IO exception when trying to create snapshot(s) on array %s",
                    storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doCreateSnapshot", e.getMessage());
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot,
                                 TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateConsistencyGroup(StorageSystem storageSystem, URI consistencyGroup, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("Creating consistency group for volumes.....");

        VolumeConsistencyGroup driverCG = new VolumeConsistencyGroup();
        BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroup);
        driverCG.setDisplayName(cg.getLabel());
        driverCG.setStorageSystemId(storageSystem.getNativeId());
        // call driver
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = driver.createConsistencyGroup(driverCG);
        // TODO: this is short cut for now, assuming synchronous driver implementation
        // We will implement support for async case later.
        if (task.getStatus() == DriverTask.TaskStatus.READY) {
            cg.setNativeId(driverCG.getNativeId());
            dbClient.updateObject(cg);
            String msg = String.format("doCreateConsistencyGroup -- Created consistency group: %s .", task.getMessage());
            _log.info(msg);
            taskCompleter.ready(dbClient);
        } else {
            cg.setInactive(true);
            dbClient.updateObject(cg);
            String errorMsg = String.format("doCreateConsistencyGroup -- Failed to create doCreateConsistencyGroup: %s .", task.getMessage());
            _log.error(errorMsg);
            ServiceError serviceError = ExternalDeviceException.errors.createConsistencyGroupFailed("doCreateConsistencyGroup", errorMsg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storageSystem,
                                         URI consistencyGroupId, Boolean markInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
    _log.info("Deleting consistency group: storage system {}, group {}", storageSystem.getNativeId(), consistencyGroupId );

        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);
        // prepare driver consistency group
        VolumeConsistencyGroup driverCG = new VolumeConsistencyGroup();
        driverCG.setDisplayName(consistencyGroup.getLabel());
        driverCG.setStorageSystemId(storageSystem.getNativeId());

        // call driver
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());

        // Todo complete, add to the driver api
        // DriverTask task = driver.deleteConsistencyGroup(driverCG);
        // TODO: this is short cut for now, assuming synchronous driver implementation
        // We will implement support for async case later.
        consistencyGroup.removeSystemConsistencyGroup(URIUtil.asString(storageSystem.getId()), consistencyGroup.getLabel());
        if (markInactive) {
            consistencyGroup.setInactive(true);
        }
        dbClient.updateObject(consistencyGroup);
        taskCompleter.ready(dbClient);
    }

    @Override
    public void doConnect(StorageSystem storageSystem) {
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        if (driver == null) {
            throw DeviceControllerException.exceptions.connectStorageFailedNoDevice(
                    storageSystem.getSystemType());
        }
        _log.info("doConnect to external device {} - start", storageSystem.getId());
        _log.info("doConnect to external device {} - end", storageSystem.getId());
    }


    private void updateVolumesWithDriverVolumeInfo(DbClient dbClient, Map<StorageVolume, Volume> driverVolumesMap)
                  throws IOException {
        for (Map.Entry driverVolumeToVolume : driverVolumesMap.entrySet()) {
            StorageVolume driverVolume = (StorageVolume)driverVolumeToVolume.getKey();
            Volume volume = (Volume)driverVolumeToVolume.getValue();
            volume.setNativeId(driverVolume.getNativeId());
            volume.setDeviceLabel(driverVolume.getDeviceLabel());
            volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));

            if (driverVolume.getWwn() != null) {
                volume.setWWN(String.format("%s%s", driverVolume.getStorageSystemId(), driverVolume.getNativeId()));
            }
            volume.setProvisionedCapacity(driverVolume.getProvisionedCapacity());
            volume.setAllocatedCapacity(driverVolume.getAllocatedCapacity());
        }
    }

    private void createVolumeSnapshots(StorageSystem storageSystem, List<BlockSnapshot> snapshots, Boolean createInactive, Boolean readOnly,
                                       TaskCompleter taskCompleter) {
        _log.info("Creating snapshots for volumes.....");
        List<VolumeSnapshot> driverSnapshots = new ArrayList<>();
        Map<VolumeSnapshot, BlockSnapshot> driverSnapshotToSnapshotMap = new HashMap<>();
        // Prepare driver snapshots
        String storageSystemNativeId = storageSystem.getNativeId();
        for (BlockSnapshot snapshot : snapshots) {
            Volume parent = dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
            VolumeSnapshot driverSnapshot = new VolumeSnapshot();
            driverSnapshot.setParentId(parent.getNativeId());
            driverSnapshot.setStorageSystemId(storageSystemNativeId);
            driverSnapshot.setDisplayName(snapshot.getLabel());
            if (readOnly) {
               driverSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_ONLY);
            } else {
                driverSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
            }
            driverSnapshotToSnapshotMap.put(driverSnapshot, snapshot);
            driverSnapshots.add(driverSnapshot);
        }
        // call driver
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = driver.createVolumeSnapshot(driverSnapshots, null);
        // TODO: this is short cut for now, assuming synchronous driver implementation
        // We will implement support for async case later.
        if (task.getStatus() == DriverTask.TaskStatus.READY) {
            // update snaphosts
            for (VolumeSnapshot driverSnapshot : driverSnapshotToSnapshotMap.keySet()) {
                BlockSnapshot snapshot = driverSnapshotToSnapshotMap.get(driverSnapshot);
                snapshot.setNativeId(driverSnapshot.getNativeId());
                snapshot.setDeviceLabel(driverSnapshot.getDeviceLabel());
            }
            dbClient.updateObject(driverSnapshotToSnapshotMap.values());
            String msg = String.format("createVolumeSnapshots -- Created snapshots: %s .", task.getMessage());
            _log.info(msg);
            taskCompleter.ready(dbClient);
        } else {
            for (BlockSnapshot snapshot : snapshots) {
                snapshot.setInactive(true);
            }
            dbClient.updateObject(snapshots);
            String errorMsg = String.format("doCreateSnapshot -- Failed to create snapshots: %s .", task.getMessage());
            _log.error(errorMsg);
            ServiceError serviceError = ExternalDeviceException.errors.createSnapshotsFailed("doCreateSnapshot", errorMsg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    private void createGroupSnapshots(StorageSystem storageSystem, List<BlockSnapshot> snapshots, Boolean createInactive, Boolean readOnly,
                                       TaskCompleter taskCompleter) {
      //  throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

        _log.info("Creating snapshot of consistency group .....");
        List<VolumeSnapshot> driverSnapshots = new ArrayList<>();
        Map<VolumeSnapshot, BlockSnapshot> driverSnapshotToSnapshotMap = new HashMap<>();
        URI cgUri = snapshots.get(0).getConsistencyGroup();
        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
        // Prepare driver snapshots
        String storageSystemNativeId = storageSystem.getNativeId();
        for (BlockSnapshot snapshot : snapshots) {
            Volume parent = dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
            VolumeSnapshot driverSnapshot = new VolumeSnapshot();
            driverSnapshot.setParentId(parent.getNativeId());
            driverSnapshot.setConsistencyGroup(consistencyGroup.getNativeId());
            driverSnapshot.setStorageSystemId(storageSystemNativeId);
            driverSnapshot.setDisplayName(snapshot.getLabel());
            if (readOnly) {
                driverSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_ONLY);
            } else {
                driverSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
            }
            driverSnapshotToSnapshotMap.put(driverSnapshot, snapshot);
            driverSnapshots.add(driverSnapshot);
        }

        // Prepare driver consistency group
        VolumeConsistencyGroup driverCG = new VolumeConsistencyGroup();
        driverCG.setNativeId(consistencyGroup.getNativeId());
        driverCG.setDisplayName(consistencyGroup.getLabel());
        driverCG.setStorageSystemId(storageSystem.getNativeId());
        // call driver
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = driver.createConsistencyGroupSnapshot(driverCG, driverSnapshots, null);
        // TODO: this is short cut for now, assuming synchronous driver implementation
        // We will implement support for async case later.
        if (task.getStatus() == DriverTask.TaskStatus.READY) {
            // update snaphosts
            for (VolumeSnapshot driverSnapshot : driverSnapshotToSnapshotMap.keySet()) {
                BlockSnapshot snapshot = driverSnapshotToSnapshotMap.get(driverSnapshot);
                snapshot.setNativeId(driverSnapshot.getNativeId());
                snapshot.setDeviceLabel(driverSnapshot.getDeviceLabel());
            }
            dbClient.updateObject(driverSnapshotToSnapshotMap.values());
            String msg = String.format("createGroupSnapshots -- Created snapshots: %s .", task.getMessage());
            _log.info(msg);
            taskCompleter.ready(dbClient);
        } else {
            for (BlockSnapshot snapshot : snapshots) {
                snapshot.setInactive(true);
            }
            dbClient.updateObject(snapshots);
            String errorMsg = String.format("doCreateSnapshot -- Failed to create snapshots: %s .", task.getMessage());
            _log.error(errorMsg);
            ServiceError serviceError = ExternalDeviceException.errors.createSnapshotsFailed("doCreateSnapshot", errorMsg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    private void updateConsistencyGroupsWithStorageSystem(Set<URI> consistencyGroups, StorageSystem storageSystem) {
        List<BlockConsistencyGroup> updateCGs = new ArrayList<>();
        Iterator<BlockConsistencyGroup> consistencyGroupIterator =
                dbClient.queryIterativeObjects(BlockConsistencyGroup.class, consistencyGroups, true);
        while (consistencyGroupIterator.hasNext()) {
            BlockConsistencyGroup consistencyGroup = consistencyGroupIterator.next();
            consistencyGroup.setStorageController(storageSystem.getId());
            consistencyGroup.addConsistencyGroupTypes(BlockConsistencyGroup.Types.LOCAL.name());
            consistencyGroup.addSystemConsistencyGroup(storageSystem.getId().toString(), consistencyGroup.getLabel());
            updateCGs.add(consistencyGroup);
        }
        dbClient.updateAndReindexObject(updateCGs);
    }

}
