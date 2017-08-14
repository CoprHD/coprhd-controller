/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.externaldevice;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationController.RemoteReplicationOperations;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.services.util.Strings;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.RemoteReplicationDriver;
import com.emc.storageos.storagedriver.StorageDriver;
import com.emc.storageos.storagedriver.impl.LockManagerImpl;
import com.emc.storageos.storagedriver.impl.RegistryImpl;
import com.emc.storageos.storagedriver.model.StorageBlockObject;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationOperationContext;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType;
import com.emc.storageos.storagedriver.storagecapabilities.AutoTieringPolicyCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.CommonStorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.DataProtectionServiceOption;
import com.emc.storageos.storagedriver.storagecapabilities.DeduplicationCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.HostIOLimitsCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.RemoteReplicationAttributes;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilitiesUtils;
import com.emc.storageos.storagedriver.storagecapabilities.VolumeCompressionCapabilityDefinition;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.externaldevice.job.CreateGroupCloneExternalDeviceJob;
import com.emc.storageos.volumecontroller.impl.externaldevice.job.CreateVolumeCloneExternalDeviceJob;
import com.emc.storageos.volumecontroller.impl.externaldevice.job.ExpandVolumeExternalDeviceJob;
import com.emc.storageos.volumecontroller.impl.externaldevice.job.RestoreFromCloneExternalDeviceJob;
import com.emc.storageos.volumecontroller.impl.externaldevice.job.RestoreFromGroupCloneExternalDeviceJob;
import com.emc.storageos.volumecontroller.impl.externaldevice.job.RestoreFromSnapshotExternalDeviceJob;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationFailoverCompleter;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.plugins.ExternalDeviceCommunicationInterface;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Joiner;

/**
 * BlockStorageDevice implementation for device drivers.
 * Note: If references to driver model instances are used in internal hash maps, wrap  collections in unmodifiable view when calling
 * driver. For example: use Collections.unmodifiableList(modifiableList) for List collections.
 */
public class ExternalBlockStorageDevice extends DefaultBlockStorageDevice implements RemoteReplicationDevice {

    private static final Logger _log = LoggerFactory.getLogger(ExternalBlockStorageDevice.class);
    // Storage drivers for block  devices
    private Map<String, AbstractStorageDriver> drivers;
    private DbClient dbClient;
    private ControllerLockingService locker;
    private ExportMaskOperations exportMaskOperationsHelper;

    // Initialized drivers map
    private static Map<String, BlockStorageDriver> blockDrivers  = new HashMap<>();


    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setLocker(ControllerLockingService locker) {
        this.locker = locker;
    }

    public void setDrivers(Map<String, AbstractStorageDriver> drivers) {
        this.drivers = drivers;
    }

    public Map<String, AbstractStorageDriver> getDrivers() {
        return drivers;
    }

    public void setExportMaskOperationsHelper(ExportMaskOperations exportMaskOperationsHelper) {
        this.exportMaskOperationsHelper = exportMaskOperationsHelper;
    }

    /**
     * Get storage driver for remote replication pair.
     *
     * @param rrPair
     * @return storage driver for remote replication pair
     */
    public RemoteReplicationDriver getDriver(RemoteReplicationPair rrPair) {
           Volume sourceVolume = dbClient.queryObject(Volume.class, rrPair.getSourceElement());
           StorageSystem system = dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());
           return (RemoteReplicationDriver)getDriver(system.getSystemType());
    }

    public synchronized BlockStorageDriver getDriver(String driverType) {
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
            blockDrivers.put(driverType, (BlockStorageDriver)driver);
            return (BlockStorageDriver)driver;
        }
    }

    private void init(AbstractStorageDriver driver) {
        Registry driverRegistry = RegistryImpl.getInstance(dbClient);
        driver.setDriverRegistry(driverRegistry);
        LockManager lockManager = LockManagerImpl.getInstance(locker);
        driver.setLockManager(lockManager);
        driver.setSdkVersionNumber(StorageDriver.SDK_VERSION_NUMBER);
    }


    @Override
    public void doCreateVolumes(StorageSystem storageSystem, StoragePool storagePool,
                                String opId, List<Volume> volumes,
                                VirtualPoolCapabilityValuesWrapper capabilities,
                                TaskCompleter taskCompleter) throws DeviceControllerException {

        List<StorageVolume> driverVolumes = new ArrayList<>();
        Map<StorageVolume, Volume> driverVolumeToVolumeMap = new HashMap<>();
        Set<URI> consistencyGroups = new HashSet<>();
        StorageCapabilities storageCapabilities = null;
        DriverTask task = null;
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        try {
            for (Volume volume : volumes) {
                if (storageCapabilities == null) {
                    // All volumes created in a request will have the same capabilities.
                    storageCapabilities = new StorageCapabilities();
                    CommonStorageCapabilities commonCapabilities = storageCapabilities.getCommonCapabilities();
                    if (commonCapabilities == null) {
                        commonCapabilities = new CommonStorageCapabilities();
                        storageCapabilities.setCommonCapabilities(commonCapabilities);
                    }
                    addAutoTieringPolicyCapability(commonCapabilities, volume.getAutoTieringPolicyUri());
                    addDeduplicationCapability(commonCapabilities, volume.getIsDeduplicated());
                    addHostIOLimitsCapability(commonCapabilities, volume.getVirtualPool());
                    addVolumeCompressionCapability(commonCapabilities, volume.getVirtualPool());
                }
                StorageVolume driverVolume = new StorageVolume();
                driverVolume.setStorageSystemId(storageSystem.getNativeId());
                driverVolume.setStoragePoolId(storagePool.getNativeId());
                driverVolume.setRequestedCapacity(volume.getCapacity());
                driverVolume.setThinlyProvisioned(volume.getThinlyProvisioned());
                driverVolume.setDisplayName(volume.getLabel());
                if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                    BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());
                    driverVolume.setConsistencyGroup(cg.getNativeId());
                }

                driverVolumes.add(driverVolume);
                driverVolumeToVolumeMap.put(driverVolume, volume);
            }
            // Call driver
            task = driver.createVolumes(Collections.unmodifiableList(driverVolumes), storageCapabilities);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY || task.getStatus() == DriverTask.TaskStatus.PARTIALLY_FAILED ) {

                updateVolumesWithDriverVolumeInfo(dbClient, driverVolumeToVolumeMap, consistencyGroups);
                dbClient.updateObject(driverVolumeToVolumeMap.values());
                updateConsistencyGroupsWithStorageSystem(consistencyGroups, storageSystem);
                String msg = String.format("doCreateVolumes -- Created volumes: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
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
        } catch (Exception e) {
            _log.error("doCreateVolumes -- Failed to create volumes. ", e);
            ServiceError serviceError = ExternalDeviceException.errors.createVolumesFailed("doCreateVolumes", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        } finally {
            try {
                if (task == null || isTaskInTerminalState(task.getStatus())) {
                    updateStoragePoolCapacity(storagePool, storageSystem,
                            URIUtil.toUris(volumes), dbClient);
                }
            } catch (Exception ex) {
                _log.error("Failed to update storage pool {} after create volumes operation completion.", storagePool.getId(), ex);
            }
        }
    }

    /**
     * Create the auto tiering policy capability and add it to the passed
     * common storage capabilities
     *
     * @param storageCapabilities A reference to common storage capabilities.
     * @param autoTieringPolicyURI The URI of the AutoTieringPolicy or null.
     */
    private void addAutoTieringPolicyCapability(CommonStorageCapabilities storageCapabilities, URI autoTieringPolicyURI) {
        if (!NullColumnValueGetter.isNullURI(autoTieringPolicyURI)) {
            AutoTieringPolicy autoTieringPolicy = dbClient.queryObject(AutoTieringPolicy.class, autoTieringPolicyURI);
            if (autoTieringPolicy == null) {
                throw DeviceControllerException.exceptions.objectNotFound(autoTieringPolicyURI);
            }

            // Create the auto tiering policy capability.
            AutoTieringPolicyCapabilityDefinition capabilityDefinition = new AutoTieringPolicyCapabilityDefinition();
            Map<String, List<String>> capabilityProperties = new HashMap<>();
            capabilityProperties.put(AutoTieringPolicyCapabilityDefinition.PROPERTY_NAME.POLICY_ID.name(),
                    Collections.singletonList(autoTieringPolicy.getPolicyName()));
            capabilityProperties.put(AutoTieringPolicyCapabilityDefinition.PROPERTY_NAME.PROVISIONING_TYPE.name(),
                    Collections.singletonList(autoTieringPolicy.getProvisioningType()));
            CapabilityInstance autoTieringCapability = new CapabilityInstance(capabilityDefinition.getId(),
                    autoTieringPolicy.getPolicyName(), capabilityProperties);

            StorageCapabilitiesUtils.addDataStorageServiceOption(storageCapabilities, Collections.singletonList(autoTieringCapability));
        }
    }


    /**
     * Create new hostIO Limits capability insance and it to the passed common capabilities
     * @param storageCapabilities common capabilities
     * @param vpoolUri virtual pool URI
     */
    private void addHostIOLimitsCapability(CommonStorageCapabilities storageCapabilities, URI vpoolUri) {
        VirtualPool virtualPool = dbClient.queryObject(VirtualPool.class, vpoolUri);
        String msg = String.format("Processing hostIOLimits for vpool %s / %s : bandwidth: %s, iops: %s",
                virtualPool.getLabel(), virtualPool.getId(), virtualPool.getHostIOLimitBandwidth(), virtualPool.getHostIOLimitIOPs());
        _log.info(msg);
        if (virtualPool.isHostIOLimitBandwidthSet() || virtualPool.isHostIOLimitIOPsSet()) {
            // Create the host io limits capability.
            HostIOLimitsCapabilityDefinition capabilityDefinition = new HostIOLimitsCapabilityDefinition();
            Map<String, List<String>> capabilityProperties = new HashMap<>();
            if (virtualPool.isHostIOLimitBandwidthSet()) {
                capabilityProperties.put(HostIOLimitsCapabilityDefinition.PROPERTY_NAME.HOST_IO_LIMIT_BANDWIDTH.name(),
                        Collections.singletonList(virtualPool.getHostIOLimitBandwidth().toString()));
            }
            if (virtualPool.isHostIOLimitIOPsSet()) {
                capabilityProperties.put(HostIOLimitsCapabilityDefinition.PROPERTY_NAME.HOST_IO_LIMIT_IOPS.name(),
                        Collections.singletonList(virtualPool.getHostIOLimitIOPs().toString()));
            }
            CapabilityInstance hostIOLimitsCapability = new CapabilityInstance(capabilityDefinition.getId(),
                    capabilityDefinition.getId(), capabilityProperties);

            StorageCapabilitiesUtils.addDataStorageServiceOption(storageCapabilities, Collections.singletonList(hostIOLimitsCapability));
        }
    }

    /**
     * Create deduplication capability and add it to the passed
     * common storage capabilities
     *
     * @param storageCapabilities reference to common storage capabilities
     * @param deduplication indicates if deduplication is required
     */
    private void addDeduplicationCapability(CommonStorageCapabilities storageCapabilities, Boolean deduplication) {
        if (deduplication) {
            // Create the deduplicated capability.
            DeduplicationCapabilityDefinition capabilityDefinition = new DeduplicationCapabilityDefinition();
            Map<String, List<String>> capabilityProperties = new HashMap<>();
            capabilityProperties.put(DeduplicationCapabilityDefinition.PROPERTY_NAME.ENABLED.name(),
                    Collections.singletonList(Boolean.TRUE.toString()));
            CapabilityInstance dedupCapability = new CapabilityInstance(capabilityDefinition.getId(),
                    capabilityDefinition.getId(), capabilityProperties);

            StorageCapabilitiesUtils.addDataStorageServiceOption(storageCapabilities, Collections.singletonList(dedupCapability));
        }
    }

    /**
     * Create volume compression capability and pass it to the passed common storage capabilities
     *
     * @param storageCapabilities
     * @param vpoolUri
     */
    private void addVolumeCompressionCapability(CommonStorageCapabilities storageCapabilities, URI vpoolUri) {
        VirtualPool virtualPool = dbClient.queryObject(VirtualPool.class, vpoolUri);
        String msg = String.format("Processing volume compression capability for vpool %s / %s : compression enabled: %s",
                virtualPool.getLabel(), virtualPool.getId(), virtualPool.getCompressionEnabled());
        _log.info(msg);
        if (virtualPool.getCompressionEnabled()) {
            Map<String, List<String>> capabilityProperties = new HashMap<>();
            // Create volume compression capability
            VolumeCompressionCapabilityDefinition capabilityDefinition = new VolumeCompressionCapabilityDefinition();
            capabilityProperties.put(VolumeCompressionCapabilityDefinition.PROPERTY_NAME.ENABLED.name(),
                    Collections.singletonList(Boolean.TRUE.toString()));
            CapabilityInstance volumeCompressionCapability = new CapabilityInstance(capabilityDefinition.getId(),
                    capabilityDefinition.getId(), capabilityProperties);

            StorageCapabilitiesUtils.addDataStorageServiceOption(storageCapabilities, Collections.singletonList(volumeCompressionCapability));
        }
    }

    @Override
    public void doExpandVolume(StorageSystem storageSystem, StoragePool storagePool,
                               Volume volume, Long size, TaskCompleter taskCompleter)
            throws DeviceControllerException {

        _log.info("Volume expand ..... Started");
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = null;

        try {
            // Prepare driver volume
            StorageVolume driverVolume = new StorageVolume();
            driverVolume.setNativeId(volume.getNativeId());
            driverVolume.setDeviceLabel(volume.getDeviceLabel());
            driverVolume.setStorageSystemId(storageSystem.getNativeId());
            driverVolume.setStoragePoolId(storagePool.getNativeId());
            driverVolume.setRequestedCapacity(volume.getCapacity());
            driverVolume.setThinlyProvisioned(volume.getThinlyProvisioned());
            driverVolume.setDisplayName(volume.getLabel());
            driverVolume.setAllocatedCapacity(volume.getAllocatedCapacity());
            driverVolume.setProvisionedCapacity(volume.getProvisionedCapacity());
            driverVolume.setWwn(volume.getWWN());

            // call driver
            task = driver.expandVolume(driverVolume, size);
            if (!isTaskInTerminalState(task.getStatus())) {
                // If the task is not in a terminal state and will be completed asynchronously
                // create a job to monitor the progress of the request and update the volume and
                // call the completer as appropriate based on the result of the request.
                ExpandVolumeExternalDeviceJob job = new ExpandVolumeExternalDeviceJob(
                        storageSystem.getId(), volume.getId(), task.getTaskId(), taskCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(job));
            } else if (task.getStatus() == DriverTask.TaskStatus.READY) {
                String msg = String.format("doExpandVolume -- Expanded volume: %s .", task.getMessage());
                _log.info(msg);
                ExternalDeviceUtils.updateExpandedVolume(volume, driverVolume, dbClient);
                taskCompleter.ready(dbClient);
            } else {
                // operation failed
                String errorMsg = String.format("doExpandVolume -- Failed to expand volume: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.expandVolumeFailed("doExpandVolume", errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            _log.error("doExpandVolume -- Failed to expand volume. ", e);
            ServiceError serviceError = ExternalDeviceException.errors.expandVolumeFailed("doExpandVolume", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        } finally {
            try {
                if (task == null || isTaskInTerminalState(task.getStatus())) {
                    updateStoragePoolCapacity(storagePool, storageSystem,
                            URIUtil.toUris(Collections.singletonList(volume)), dbClient);
                }
            } catch (Exception ex) {
                _log.error("Failed to update storage pool {} after expand volume operation completion.", storagePool.getId(), ex);
            }
        }
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
        List<String> failedToDeleteVolumes = new ArrayList<>();
        List<Volume> deletedClones = new ArrayList<>();
        List<String> failedToDeleteClones = new ArrayList<>();
        boolean exception = false;

        StringBuffer errorMsgForVolumes = new StringBuffer();
        StringBuffer errorMsgForClones = new StringBuffer();
        try {
            for (Volume volume : volumes) {
                DriverTask task = null;
                // Check if this is regular volume or this is volume clone
                if (!NullColumnValueGetter.isNullURI(volume.getAssociatedSourceVolume())) {
                    // this is clone
                    _log.info("Deleting volume clone on storage system {}, clone: {} .",
                            storageSystem.getNativeId(), volume.getNativeId());
                    BlockObject sourceVolume = BlockObject.fetch(dbClient, volume.getAssociatedSourceVolume());
                    VolumeClone driverClone = new VolumeClone();
                    driverClone.setStorageSystemId(storageSystem.getNativeId());
                    driverClone.setNativeId(volume.getNativeId());
                    driverClone.setDeviceLabel(volume.getDeviceLabel());
                    driverClone.setParentId(sourceVolume.getNativeId());
                    driverClone.setConsistencyGroup(volume.getReplicationGroupInstance());
                    // check for exports
                    if (hasExports(driver, driverClone)) {
                        failedToDeleteClones.add(volume.getNativeId());
                        String errorMsgClone = String.format("Cannot delete clone %s on storage system %s, clone has exports on array.",
                                driverClone.getNativeId(), storageSystem.getNativeId());
                        _log.error(errorMsgClone);
                        errorMsgForClones.append(errorMsgClone +"\n");
                        continue;
                    }
                    task = driver.deleteVolumeClone(driverClone);
                } else {
                    // this is regular volume
                    _log.info("Deleting volume on storage system {}, volume: {} .",
                            storageSystem.getNativeId(), volume.getNativeId());
                    StorageVolume driverVolume = new StorageVolume();
                    driverVolume.setStorageSystemId(storageSystem.getNativeId());
                    driverVolume.setNativeId(volume.getNativeId());
                    driverVolume.setDeviceLabel(volume.getDeviceLabel());
                    driverVolume.setConsistencyGroup(volume.getReplicationGroupInstance());
                    // check for exports
                    if (hasExports(driver, driverVolume)) {
                        failedToDeleteVolumes.add(volume.getNativeId());
                        String errorMsgVolume = String.format("Cannot delete volume %s on storage system %s, volume has exports on array.",
                                driverVolume.getNativeId(), storageSystem.getNativeId());
                        _log.error(errorMsgVolume);
                        errorMsgForVolumes.append(errorMsgVolume + "\n");
                        continue;
                    }
                    task = driver.deleteVolume(driverVolume);
                }
                if (task.getStatus() == DriverTask.TaskStatus.READY) {
                    volume.setInactive(true);
                    if (volume.getAssociatedSourceVolume() != null) {
                        deletedClones.add(volume);
                    } else {
                        deletedVolumes.add(volume);
                    }
                } else {
                    if (volume.getAssociatedSourceVolume() != null) {
                        failedToDeleteClones.add(volume.getNativeId());
                    } else {
                        failedToDeleteVolumes.add(volume.getNativeId());
                    }
                }
            }
        } catch (Exception e) {
            exception = true;
            _log.error("doDeleteVolumes -- Failed to delete volumes. ", e);
            ServiceError serviceError = ExternalDeviceException.errors.deleteVolumesFailed("doDeleteVolumes", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        } finally {
            if (!deletedVolumes.isEmpty()){
                _log.info("Deleted volumes on storage system {}, volumes: {} .",
                        storageSystem.getNativeId(), deletedVolumes.toString());
                dbClient.updateObject(deletedVolumes);
            }

            if (!deletedClones.isEmpty()){
                _log.info("Deleted volume clones on storage system {}, clones: {} .",
                        storageSystem.getNativeId(), deletedClones.toString());
                dbClient.updateObject(deletedClones);
            }

            if(!(failedToDeleteVolumes.isEmpty() && failedToDeleteClones.isEmpty())) {
                if(!failedToDeleteVolumes.isEmpty()) {
                    String errorMsgVolumes = String.format("Failed to delete volumes on storage system %s, volumes: %s . ",
                            storageSystem.getNativeId(), failedToDeleteVolumes.toString());
                    _log.error(errorMsgVolumes);
                } else {
                    String errorMsgClones = String.format("Failed to delete volume clones on storage system %s, clones: %s .",
                            storageSystem.getNativeId(), failedToDeleteClones.toString());
                    _log.error(errorMsgClones);
                }

                ServiceError serviceError = ExternalDeviceException.errors.deleteVolumesFailed("doDeleteVolumes",
                        errorMsgForVolumes.append(errorMsgForClones).toString());
                taskCompleter.error(dbClient, serviceError);
            } else if (!exception){
                taskCompleter.ready(dbClient);
            }
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
            ServiceError error = ExternalDeviceException.errors.createSnapshotsFailed("doCreateSnapshot", e.getMessage());
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void doRestoreFromSnapshot(StorageSystem storageSystem, URI volume,
                                      URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {

        String storageSystemNativeId = storageSystem.getNativeId();
        _log.info("Snapshot Restore..... Started");
        BlockConsistencyGroup parentVolumeConsistencyGroup = null;
        try {
            List<BlockSnapshot> snapshotsToRestore = new ArrayList<>();
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);
            List<BlockSnapshot> groupSnapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(blockSnapshot, dbClient);
            if (groupSnapshots.size() > 1 &&
                    ControllerUtils.checkSnapshotsInConsistencyGroup(Arrays.asList(blockSnapshot), dbClient, taskCompleter)) {
                // make sure we restore only snapshots from the same consistency group
                for (BlockSnapshot snap : groupSnapshots) {
                    if (snap.getConsistencyGroup().equals(blockSnapshot.getConsistencyGroup())) {
                        snapshotsToRestore.add(snap);
                    }
                }
                URI cgUri = blockSnapshot.getConsistencyGroup();
                parentVolumeConsistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
                _log.info("Restore group snapshot: group {}, snapshot set: {}, snapshots to restore: "
                                + Joiner.on("\t").join(snapshotsToRestore), parentVolumeConsistencyGroup.getNativeId(),
                        blockSnapshot.getReplicationGroupInstance());
            } else {
                Volume sourceVolume = getSnapshotParentVolume(blockSnapshot);
                snapshotsToRestore.add(blockSnapshot);
                _log.info("Restore single volume snapshot: volume {}, snapshot: {}", sourceVolume.getNativeId(), blockSnapshot.getNativeId());
            }
            // Prepare driver snapshots
            List<VolumeSnapshot> driverSnapshots = new ArrayList<>();
            for (BlockSnapshot snap : snapshotsToRestore) {
                VolumeSnapshot driverSnapshot = new VolumeSnapshot();
                Volume sourceVolume = getSnapshotParentVolume(snap);
                driverSnapshot.setParentId(sourceVolume.getNativeId());
                driverSnapshot.setNativeId(snap.getNativeId());
                driverSnapshot.setStorageSystemId(storageSystemNativeId);
                driverSnapshot.setDisplayName(snap.getLabel());
                if (parentVolumeConsistencyGroup != null) {
                    driverSnapshot.setConsistencyGroup(snap.getReplicationGroupInstance());
                }
                driverSnapshots.add(driverSnapshot);
            }

            // Call driver to execute this request
            BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
            DriverTask task = driver.restoreSnapshot(driverSnapshots);
            if (!isTaskInTerminalState(task.getStatus())) {
                // If the task is not in a terminal state and will be completed asynchronously
                // create a job to monitor the progress of the request and call the completer as
                // appropriate based on the result of the request.
                RestoreFromSnapshotExternalDeviceJob job = new RestoreFromSnapshotExternalDeviceJob(
                        storageSystem.getId(), snapshot, task.getTaskId(), taskCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(job));
            } else if (task.getStatus() == DriverTask.TaskStatus.READY) {
                String msg = String.format("doRestoreFromSnapshot -- Restored snapshots: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                String errorMsg = String.format("doRestoreFromSnapshot -- Failed to restore from snapshots: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.restoreFromSnapshotFailed("doRestoreFromSnapshot", errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String message = String.format("IO exception when trying to restore from snapshots on array %s",
                    storageSystem.getSerialNumber());
            _log.error(message, e);
            ServiceError error = ExternalDeviceException.errors.restoreFromSnapshotFailed("doRestoreFromSnapshot", e.getMessage());
            taskCompleter.error(dbClient, error);
        }
        _log.info("Snapshot Restore..... End");
    }


    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot,
                                 TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);
            List<BlockSnapshot> groupSnapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(blockSnapshot, dbClient);

            if (!groupSnapshots.isEmpty() &&
                    ControllerUtils.checkSnapshotsInConsistencyGroup(Arrays.asList(blockSnapshot), dbClient, taskCompleter)) {
                // make sure we delete only snapshots from the same consistency group
                List<BlockSnapshot> snapshotsToDelete = new ArrayList<>();
                for (BlockSnapshot snap : groupSnapshots ) {
                    if (snap.getConsistencyGroup().equals(blockSnapshot.getConsistencyGroup())) {
                        snapshotsToDelete.add(snap);
                    }
                }
                deleteGroupSnapshots(storage, snapshotsToDelete, taskCompleter);
            } else {
                deleteVolumeSnapshot(storage, snapshot, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to delete snapshot(s) on array %s", storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = ExternalDeviceException.errors.deleteSnapshotFailed("doDeleteSnapshot", e.getMessage());
            taskCompleter.error(dbClient, error);
        } catch (Exception e) {
            String message = String.format(
                    "Exception when trying to delete snapshot(s) on array %s", storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = ExternalDeviceException.errors.deleteSnapshotFailed("doDeleteSnapshot", e.getMessage());
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void doCreateClone(StorageSystem storageSystem, URI volume, URI clone, Boolean createInactive,
                              TaskCompleter taskCompleter) {
        Volume cloneObject = null;
        DriverTask task = null;
        try {
        	cloneObject = dbClient.queryObject(Volume.class, clone);
            BlockObject sourceVolume = BlockObject.fetch(dbClient, volume);
            VolumeClone driverClone = new VolumeClone();
            
            if (sourceVolume instanceof Volume) {
            	driverClone.setSourceType(VolumeClone.SourceType.VOLUME);
            } else if (sourceVolume instanceof BlockSnapshot) {
            	driverClone.setSourceType(VolumeClone.SourceType.SNAPSHOT);
            } else {
                cloneObject.setInactive(true);
                dbClient.updateObject(cloneObject);
                String errorMsg = String.format("doCreateClone -- Failed to create volume clone: unexpected source type %s .",
                        sourceVolume.getClass().getSimpleName());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createVolumeCloneFailed("doCreateClone", errorMsg);
                taskCompleter.error(dbClient, serviceError);
                return;
            }
            // Prepare driver clone
            driverClone.setParentId(sourceVolume.getNativeId());
            driverClone.setStorageSystemId(storageSystem.getNativeId());
            driverClone.setDisplayName(cloneObject.getLabel());
            driverClone.setRequestedCapacity(cloneObject.getCapacity());
            driverClone.setThinlyProvisioned(cloneObject.getThinlyProvisioned());

            // Call driver
            BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        	List<VolumeClone> driverClones = new ArrayList<>();
        	driverClones.add(driverClone);
        	task = driver.createVolumeClone(Collections.unmodifiableList(driverClones), null);
        	
            if (!isTaskInTerminalState(task.getStatus())) {
                // If the task is not in a terminal state and will be completed asynchronously
                // create a job to monitor the progress of the request and update the clone 
                // volume and call the completer as appropriate based on the result of the request.
                CreateVolumeCloneExternalDeviceJob job = new CreateVolumeCloneExternalDeviceJob(
                        storageSystem.getId(), clone, task.getTaskId(), taskCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(job));
            } else if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // Update clone
                String msg = String.format("doCreateClone -- Created volume clone: %s .", task.getMessage());
                _log.info(msg);
            	VolumeClone driverCloneResult = driverClones.get(0);
                ExternalDeviceUtils.updateNewlyCreatedClone(cloneObject, driverCloneResult, dbClient);
                taskCompleter.ready(dbClient);
            } else {
                cloneObject.setInactive(true);
                dbClient.updateObject(cloneObject);
                String errorMsg = String.format("doCreateClone -- Failed to create volume clone: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createVolumeCloneFailed("doCreateClone", errorMsg);
                taskCompleter.error(dbClient, serviceError);                
            }
        } catch (Exception e) {
            if (cloneObject != null) {
                cloneObject.setInactive(true);
                dbClient.updateObject(cloneObject);
            }
            _log.error("Failed to create volume clone. ", e);
            ServiceError serviceError = ExternalDeviceException.errors.createVolumeCloneFailed("doCreateClone", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        } finally {
            try {
                if (task == null || isTaskInTerminalState(task.getStatus())) {
                    StoragePool dbPool = dbClient.queryObject(StoragePool.class, cloneObject.getPool());
                    updateStoragePoolCapacity(dbPool, storageSystem,
                            Collections.singletonList(clone), dbClient);
                }
            } catch (Exception ex) {
                _log.error("Failed to update storage pool {} after create clone operation completion.", cloneObject.getPool(), ex);
            }
        }
    }
    
    @Override
    public void doDisconnect(StorageSystem storageSystem){
    	try{
    		_log.info("doDisconnect {} - start", storageSystem.getId());    	
        	com.emc.storageos.storagedriver.model.StorageSystem driverStorageSystem = ExternalDeviceCommunicationInterface.initStorageSystem(storageSystem);
        	BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        	DriverTask task = driver.stopManagement(driverStorageSystem);
        	if (task.getStatus() == DriverTask.TaskStatus.READY) {
        		_log.info("doDisconnect -- Disconnected Storage System: {}", task.getMessage());
        	} else {
        		_log.error("doDisconnect failed. ", task.getMessage());
        		throw ExternalDeviceException.exceptions.doDisconnectFailed("doDisconnect", task.getMessage());
        	}
    		_log.info("doDisconnect %1$s - Complete", storageSystem.getId());
    	} catch(Exception e){
    		_log.error("doDisconnect failed. ", e.getMessage());
    		throw ExternalDeviceException.exceptions.doDisconnectFailed("doDisconnect", e.getMessage());

    	}
    }
    
    @Override
    public void doAddToConsistencyGroup(StorageSystem storageSystem, URI consistencyGroupId, String replicationGroupName,
            List<URI> blockObjects, TaskCompleter taskCompleter) throws DeviceControllerException {
        	
    	BlockConsistencyGroup consistencyGroup =null;
        try {
        	_log.info("{} doAddToConsistencyGroup START ...", storageSystem.getSerialNumber());
        	BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
            List<Volume> volumes = dbClient.queryObject(Volume.class, blockObjects);           
            List<StorageVolume> driverVolumes = new ArrayList<>();
            consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);

                for (Volume volume : volumes) {
                    StorageVolume driverVolume = new StorageVolume();
                    driverVolume.setStorageSystemId(storageSystem.getNativeId());
                    driverVolume.setNativeId(volume.getNativeId());
                    driverVolume.setRequestedCapacity(volume.getCapacity());
                    driverVolume.setThinlyProvisioned(volume.getThinlyProvisioned());
                    driverVolume.setConsistencyGroup(consistencyGroup.getNativeId()); 
                    driverVolume.setDisplayName(volume.getLabel());
                    //add them to StorageVolumes list	
                    driverVolumes.add(driverVolume);  
                }
                DriverTask task = driver.addVolumesToConsistencyGroup(driverVolumes, null); 
                _log.info("doAddToConsistencyGroup -- added volumes {} to consistency Group: {}", volumes.toString(), consistencyGroupId);     
            if(task.getStatus() == DriverTask.TaskStatus.READY){
                for (Volume volume : volumes) {
                     volume.setConsistencyGroup(consistencyGroupId);   
                }
                dbClient.updateObject(volumes);
                taskCompleter.ready(dbClient);
            } else {
                _log.error(String.format("Add volumes to Consistency Group operation failed %s", task.getMessage()));
                taskCompleter.error(dbClient, DeviceControllerException.exceptions
                        .failedToAddMembersToConsistencyGroup(consistencyGroup.getLabel(),
                                consistencyGroup.getLabel(), task.getMessage()));
            }   
            _log.info("{} doAddVolumesToConsistencyGroup END ...", storageSystem.getSerialNumber());
        } catch (Exception e) {
            _log.error(String.format("Add volumes from Consistency Group operation failed %s", e.getMessage()));
            taskCompleter.error(dbClient, DeviceControllerException.exceptions
                    .failedToAddMembersToConsistencyGroup(consistencyGroup.getLabel(),
                            consistencyGroup.getLabel(), e.getMessage()));
        }
    }
    
    @Override
    public void doRemoveFromConsistencyGroup(StorageSystem storageSystem, URI consistencyGroupId,
            List<URI> blockObjects, TaskCompleter taskCompleter) throws DeviceControllerException {
        	
    	BlockConsistencyGroup consistencyGroup =null;
        try {
        	_log.info("{} doRemoveVolumesFromConsistencyGroup START ...", storageSystem.getSerialNumber());
        	BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
            List<Volume> volumes = dbClient.queryObject(Volume.class, blockObjects);           
            List<StorageVolume> driverVolumes = new ArrayList<>();
            consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);

                for (Volume volume : volumes) {
                    StorageVolume driverVolume = new StorageVolume();
                    driverVolume.setStorageSystemId(storageSystem.getNativeId());
                    driverVolume.setNativeId(volume.getNativeId());
                    driverVolume.setRequestedCapacity(volume.getCapacity());
                    driverVolume.setThinlyProvisioned(volume.getThinlyProvisioned());
                    driverVolume.setConsistencyGroup(consistencyGroup.getNativeId()); 
                    driverVolume.setDisplayName(volume.getLabel());
                    //add them to StorageVolumes list	
                    driverVolumes.add(driverVolume);  
                }
                DriverTask task = driver.removeVolumesFromConsistencyGroup(driverVolumes, null); 
                _log.info("doRemoveVolumesFromConsistencyGroup -- removing volumes {} from consistency Group: {}", volumes.toString(), consistencyGroupId);
            if(task.getStatus() == DriverTask.TaskStatus.READY){
                for (Volume volume : volumes) { 
                        volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
                dbClient.updateObject(volumes);
                taskCompleter.ready(dbClient);
            } else {
                _log.error(String.format("Remove volumes from Consistency Group operation failed %s", task.getMessage()));
                taskCompleter.error(dbClient, DeviceControllerException.exceptions
                        .failedToRemoveMembersToConsistencyGroup(consistencyGroup.getLabel(),
                                consistencyGroup.getLabel(), task.getMessage()));
            }
            _log.info("{} doRemoveVolumesFromConsistencyGroup END ...", storageSystem.getSerialNumber());
        } catch (Exception e) {
            _log.error(String.format("Remove volumes from Consistency Group operation failed %s", e.getMessage()));
            taskCompleter.error(dbClient, DeviceControllerException.exceptions
                    .failedToRemoveMembersToConsistencyGroup(consistencyGroup.getLabel(),
                            consistencyGroup.getLabel(), e.getMessage()));
        }
    }

    @Override
    public void doCreateGroupClone(StorageSystem storageSystem, List<URI> cloneURIs,
                                   Boolean createInactive, TaskCompleter taskCompleter) {
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());

        List<VolumeClone> driverClones = new ArrayList<>();
        Map<VolumeClone, Volume> driverCloneToCloneMap = new HashMap<>();
        Set<URI> consistencyGroups = new HashSet<>();

        List<Volume> clones = null;
        DriverTask task = null;
        try {
            clones = dbClient.queryObject(Volume.class, cloneURIs);
            // We assume here that all volumes belong to the same consistency group
            URI parentUri = clones.get(0).getAssociatedSourceVolume();
            Volume parentVolume = dbClient.queryObject(Volume.class, parentUri);
            BlockConsistencyGroup cg = null;
            if (!NullColumnValueGetter.isNullURI(parentVolume.getConsistencyGroup())) {
                cg = dbClient.queryObject(BlockConsistencyGroup.class, parentVolume.getConsistencyGroup());
            } else {
                String errorMsg = String.format("doCreateGroupClone -- Failed to create group clone, parent volumes do not belong to consistency group." +
                        " Clones: %s .", cloneURIs);
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createGroupCloneFailed("doCreateGroupClone",errorMsg);
                taskCompleter.error(dbClient, serviceError);
                return;
            }
            // Prepare driver consistency group of parent volume
            VolumeConsistencyGroup driverCG = new VolumeConsistencyGroup();
            driverCG.setDisplayName(cg.getLabel());
            driverCG.setNativeId(cg.getNativeId());
            driverCG.setStorageSystemId(storageSystem.getNativeId());

            // Prepare driver clones
            for (Volume clone : clones) {
                URI sourceVolumeUri = clone.getAssociatedSourceVolume();
                Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolumeUri);
                VolumeClone driverClone = new VolumeClone();
                driverClone.setParentId(sourceVolume.getNativeId());
                driverClone.setStorageSystemId(storageSystem.getNativeId());
                driverClone.setDisplayName(clone.getLabel());
                driverClone.setRequestedCapacity(clone.getCapacity());
                driverClone.setThinlyProvisioned(clone.getThinlyProvisioned());
                driverClones.add(driverClone);
                driverCloneToCloneMap.put(driverClone, clone);
            }
            // Call driver to create group snapshot
            task = driver.createConsistencyGroupClone(driverCG, Collections.unmodifiableList(driverClones), null);
            if (!isTaskInTerminalState(task.getStatus())) {
                // If the task is not in a terminal state and will be completed asynchronously
                // create a job to monitor the progress of the request and update the clone 
                // volume and call the completer as appropriate based on the result of the request.
                CreateGroupCloneExternalDeviceJob job = new CreateGroupCloneExternalDeviceJob(
                        storageSystem.getId(), cloneURIs, parentVolume.getConsistencyGroup(),
                        task.getTaskId(), taskCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(job));
            } else if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // Update clone object with driver data
                String msg = String.format("doCreateGroupClone -- Created group clone: %s .", task.getMessage());
                _log.info(msg);
                List<Volume> cloneObjects = new ArrayList<>();
                for (VolumeClone driverCloneResult : driverClones) {
                    Volume cloneObject = driverCloneToCloneMap.get(driverCloneResult);
                    ExternalDeviceUtils.updateNewlyCreatedGroupClone(cloneObject, driverCloneResult, parentVolume.getConsistencyGroup(), dbClient);
                    cloneObjects.add(cloneObject);
                }
                dbClient.updateObject(cloneObjects);
                taskCompleter.ready(dbClient);
            } else {
                // Process failure
                for (Volume cloneObject : clones) {
                    cloneObject.setInactive(true);
                }
                dbClient.updateObject(clones);
                String errorMsg = String.format("doCreateGroupClone -- Failed to create group clone: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createGroupCloneFailed("doCreateGroupClone", errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            if (clones != null) {
                // Process failure
                for (Volume cloneObject : clones) {
                    cloneObject.setInactive(true);
                }
                dbClient.updateObject(clones);
            }
            _log.error("Failed to create group clone. ", e);
            ServiceError serviceError = ExternalDeviceException.errors.createGroupCloneFailed("doCreateGroupClone", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        } finally {
            try {
                if (task == null || isTaskInTerminalState(task.getStatus())) {
                    // post process storage pool capacity for clone's pools
                    // map clones to their storage pool
                    Map<URI, List<URI>> dbPoolToClone = new HashMap<>();
                    for (Volume clone : clones) {
                        URI dbPoolUri = clone.getPool();
                        List<URI> poolClones = dbPoolToClone.get(dbPoolUri);
                        if (poolClones == null) {
                            poolClones = new ArrayList<>();
                            dbPoolToClone.put(dbPoolUri, poolClones);
                        }
                        poolClones.add(clone.getId());
                    }
                    for (URI dbPoolUri : dbPoolToClone.keySet()) {
                        StoragePool dbPool = dbClient.queryObject(StoragePool.class, dbPoolUri);
                        updateStoragePoolCapacity(dbPool, storageSystem,
                                dbPoolToClone.get(dbPoolUri), dbClient);
                    }
                }
            } catch (Exception ex) {
                _log.error("Failed to update storage pool after create group clone operation completion.", ex);
            }
        }
    }


    @Override
    public void doDetachClone(StorageSystem storageSystem, URI cloneVolume,
                              TaskCompleter taskCompleter) {
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = null;
        Volume clone = dbClient.queryObject(Volume.class, cloneVolume);
        _log.info("Detaching volume clone on storage system {}, clone: {} .",
                storageSystem.getNativeId(), clone.toString());

        try {
            BlockObject sourceVolume = BlockObject.fetch(dbClient, clone.getAssociatedSourceVolume());
            VolumeClone driverClone = new VolumeClone();
            driverClone.setStorageSystemId(storageSystem.getNativeId());
            driverClone.setNativeId(clone.getNativeId());
            driverClone.setParentId(sourceVolume.getNativeId());
            driverClone.setConsistencyGroup(clone.getReplicationGroupInstance());

            // Call driver
            task = driver.detachVolumeClone(Collections.unmodifiableList(Collections.singletonList(driverClone)));
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(clone, dbClient);
                clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
                clone.setReplicaState(Volume.ReplicationState.DETACHED.name());
                String msg = String.format("doDetachClone -- Detached volume clone: %s .", task.getMessage());
                _log.info(msg);
                dbClient.updateObject(clone);
                taskCompleter.ready(dbClient);
            } else {
                String msg = String.format("Failed to detach volume clone on storage system %s, clone: %s .",
                        storageSystem.getNativeId(), clone.toString());
                _log.error(msg);
                // todo: add error
                ServiceError serviceError = ExternalDeviceException.errors.detachVolumeCloneFailed("doDetachClone", msg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String msg = String.format("Failed to detach volume clone on storage system %s, clone: %s .",
                    storageSystem.getNativeId(), clone.toString());
            _log.error(msg, e);
            ServiceError serviceError = ExternalDeviceException.errors.detachVolumeCloneFailed("doDetachClone", msg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void doDetachGroupClone(StorageSystem storageSystem, List<URI> cloneVolumes,
                                   TaskCompleter taskCompleter) {

        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = null;
        List<Volume> clones = dbClient.queryObject(Volume.class, cloneVolumes);
        _log.info("Detaching group clones on storage system {}, clone: {} .",
                storageSystem.getNativeId(), clones.toString());

        try {
            Map<VolumeClone, Volume> driverCloneToCloneMap = new HashMap<>();
            List<VolumeClone> driverClones = new ArrayList<>();
            for (Volume clone : clones) {
                BlockObject sourceVolume = BlockObject.fetch(dbClient, clone.getAssociatedSourceVolume());
                VolumeClone driverClone = new VolumeClone();
                driverClone.setStorageSystemId(storageSystem.getNativeId());
                driverClone.setNativeId(clone.getNativeId());
                driverClone.setParentId(sourceVolume.getNativeId());
                driverClone.setConsistencyGroup(clone.getReplicationGroupInstance());
                driverClones.add(driverClone);
                driverCloneToCloneMap.put(driverClone, clone);
            }
            // Call driver
            task = driver.detachVolumeClone(Collections.unmodifiableList(driverClones));
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                for (Map.Entry<VolumeClone, Volume> entry : driverCloneToCloneMap.entrySet() ) {
                    VolumeClone driverClone = entry.getKey();
                    Volume clone = entry.getValue();
                    ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(clone, dbClient);
                    clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
                    clone.setReplicaState(Volume.ReplicationState.DETACHED.name());
                }

                String msg = String.format("doDetachGroupClone -- Detached group clone: %s .", task.getMessage());
                _log.info(msg);
                dbClient.updateObject(clones);
                taskCompleter.ready(dbClient);
            } else {
                String msg = String.format("Failed to detach group clone on storage system %s, clones: %s .",
                        storageSystem.getNativeId(), clones.toString());
                _log.error(msg);
                // todo: add error
                ServiceError serviceError = ExternalDeviceException.errors.detachVolumeCloneFailed("doDetachGroupClone", msg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String msg = String.format("Failed to detach group clone on storage system %s, clones: %s .",
                    storageSystem.getNativeId(), clones.toString());
            _log.error(msg, e);
            // todo: add error
            ServiceError serviceError = ExternalDeviceException.errors.detachVolumeCloneFailed("doDetachGroupClone", msg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void doRestoreFromClone(StorageSystem storageSystem, URI cloneVolume,
                                   TaskCompleter taskCompleter) {
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = null;
        Volume clone = dbClient.queryObject(Volume.class, cloneVolume);
        _log.info("Restore from volume clone on storage system {}, clone: {} .",
                storageSystem.getNativeId(), clone.toString());

        try {
            BlockObject sourceVolume = BlockObject.fetch(dbClient, clone.getAssociatedSourceVolume());
            VolumeClone driverClone = new VolumeClone();

            driverClone.setStorageSystemId(storageSystem.getNativeId());
            driverClone.setNativeId(clone.getNativeId());
            driverClone.setParentId(sourceVolume.getNativeId());
            driverClone.setConsistencyGroup(clone.getReplicationGroupInstance());

            // Call driver
            task = driver.restoreFromClone(Collections.unmodifiableList(Collections.singletonList(driverClone)));
            if (!isTaskInTerminalState(task.getStatus())) {
                // If the task is not in a terminal state and will be completed asynchronously
                // create a job to monitor the progress of the request and update the clone 
                // volume replica state and call the completer as appropriate based on the result
                // of the request.
                RestoreFromCloneExternalDeviceJob job = new RestoreFromCloneExternalDeviceJob(
                        storageSystem.getId(), cloneVolume, task.getTaskId(), taskCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(job));
            } else if (task.getStatus() == DriverTask.TaskStatus.READY) {
                String msg = String.format("doRestoreFromClone -- Restored volume from clone: %s .", task.getMessage());
                _log.info(msg);
                ExternalDeviceUtils.updateRestoredClone(clone, driverClone, dbClient, true);
                taskCompleter.ready(dbClient);
            } else {
                String msg = String.format("Failed to restore volume from clone on storage system %s, clone: %s .",
                        storageSystem.getNativeId(), clone.toString());
                _log.error(msg);
                // todo: add error
                ServiceError serviceError = ExternalDeviceException.errors.restoreVolumesFromClonesFailed("doRestoreFromClone", msg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String msg = String.format("Failed to restore volume from clone on storage system %s, clone: %s .",
                    storageSystem.getNativeId(), clone.toString());
            _log.error(msg, e);
            ServiceError serviceError = ExternalDeviceException.errors.restoreVolumesFromClonesFailed("doRestoreFromClone", msg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void doRestoreFromGroupClone(StorageSystem storageSystem,
                                        List<URI> cloneVolumes, TaskCompleter taskCompleter) {
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = null;
        List<Volume> clones = dbClient.queryObject(Volume.class, cloneVolumes);
        _log.info("Restore from group clone on storage system {}, clones: {} .",
                storageSystem.getNativeId(), clones.toString());

        try {
            Map<VolumeClone, Volume> driverCloneToCloneMap = new HashMap<>();
            List<VolumeClone> driverClones = new ArrayList<>();
            for (Volume clone : clones) {
                BlockObject sourceVolume = BlockObject.fetch(dbClient, clone.getAssociatedSourceVolume());
                VolumeClone driverClone = new VolumeClone();
                driverClone.setStorageSystemId(storageSystem.getNativeId());
                driverClone.setNativeId(clone.getNativeId());
                driverClone.setParentId(sourceVolume.getNativeId());
                driverClone.setConsistencyGroup(clone.getReplicationGroupInstance());
                driverClones.add(driverClone);
                driverCloneToCloneMap.put(driverClone, clone);
            }
            // Call driver
            task = driver.restoreFromClone(Collections.unmodifiableList(driverClones));
            if (!isTaskInTerminalState(task.getStatus())) {
                // If the task is not in a terminal state and will be completed asynchronously
                // create a job to monitor the progress of the request and update the clone 
                // volume replica state and call the completer as appropriate based on the result
                // of the request.
                RestoreFromGroupCloneExternalDeviceJob job = new RestoreFromGroupCloneExternalDeviceJob(
                        storageSystem.getId(), cloneVolumes, task.getTaskId(), taskCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(job));
            } else if (task.getStatus() == DriverTask.TaskStatus.READY) {
                for (Map.Entry<VolumeClone, Volume> entry : driverCloneToCloneMap.entrySet() ) {
                    VolumeClone driverClone = entry.getKey();
                    Volume clone = entry.getValue();
                    ExternalDeviceUtils.updateRestoredClone(clone, driverClone, dbClient, false);
                }

                String msg = String.format("doRestoreFromGroupClone -- Restore from group clone: %s .", task.getMessage());
                _log.info(msg);
                dbClient.updateObject(clones);
                taskCompleter.ready(dbClient);
            } else {
                String msg = String.format("Failed to restore from group clone on storage system %s, clones: %s .",
                        storageSystem.getNativeId(), clones.toString());
                _log.error(msg);
                ServiceError serviceError = ExternalDeviceException.errors.restoreVolumesFromClonesFailed("doRestoreFromGroupClone", msg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String msg = String.format("Failed to restore from group clone on storage system %s, clones: %s .",
                    storageSystem.getNativeId(), clones.toString());
            _log.error(msg, e);
            ServiceError serviceError = ExternalDeviceException.errors.restoreVolumesFromClonesFailed("doRestoreFromGroupClone", msg);
            taskCompleter.error(dbClient, serviceError);
        }
    }


    @Override
    public void doCreateConsistencyGroup(StorageSystem storageSystem, URI consistencyGroup, String replicationGroupName, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("Creating consistency group for volumes START.....");
        BlockConsistencyGroup cg = null;
        try {
            VolumeConsistencyGroup driverCG = new VolumeConsistencyGroup();
            cg = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroup);
            driverCG.setDisplayName(cg.getLabel());
            driverCG.setStorageSystemId(storageSystem.getNativeId());
            // call driver
            BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
            DriverTask task = driver.createConsistencyGroup(driverCG);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                cg.setNativeId(driverCG.getNativeId());
                cg.addSystemConsistencyGroup(storageSystem.getId().toString(), cg.getLabel());
                cg.addConsistencyGroupTypes(BlockConsistencyGroup.Types.LOCAL.name());
                if (NullColumnValueGetter.isNullURI(cg.getStorageController())) {
                    cg.setStorageController(storageSystem.getId());
                }
                dbClient.updateObject(cg);
                String msg = String.format("doCreateConsistencyGroup -- Created consistency group: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                cg.setInactive(true);
                dbClient.updateObject(cg);
                String errorMsg = String.format("doCreateConsistencyGroup -- Failed to create Consistency Group: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createConsistencyGroupFailed("doCreateConsistencyGroup", errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            if (cg != null) {
                cg.setInactive(true);
                dbClient.updateObject(cg);
            }
            String errorMsg = String.format("doCreateConsistencyGroup -- Failed to create Consistency Group: %s .", e.getMessage());
            _log.error(errorMsg, e);
            ServiceError serviceError = ExternalDeviceException.errors.createConsistencyGroupFailed("doCreateConsistencyGroup", errorMsg);
            taskCompleter.error(dbClient, serviceError);
        } finally {
            _log.info("Creating consistency group for volumes END.....");
        }
    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storageSystem,
                                         URI consistencyGroupId, String replicationGroupName,
                                         Boolean keepRGName,  Boolean markInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("Delete consistency group: STARTED...");

        BlockConsistencyGroup consistencyGroup = null;
        String groupNativeId = null;
        String groupDisplayName = null;
        boolean isDeleteForBlockCG = true;

        try {
            if (!NullColumnValueGetter.isNullURI(consistencyGroupId)) {
                consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);
                groupDisplayName = consistencyGroup != null ? consistencyGroup.getLabel() : replicationGroupName;
                groupNativeId = consistencyGroup != null ? consistencyGroup.getNativeId() : replicationGroupName;
                if (consistencyGroup == null) {
                    isDeleteForBlockCG = false;
                }
            } else {
                groupDisplayName = replicationGroupName;
                groupNativeId = replicationGroupName;
                isDeleteForBlockCG = false;
            }

            if (groupNativeId == null || groupNativeId.isEmpty()) {
                String msg = String.format("doDeleteConsistencyGroup -- There is no consistency group or replication group to delete.");
                _log.info(msg);
                taskCompleter.ready(dbClient);
                return;
            }

            if (isDeleteForBlockCG) {
                _log.info("Deleting consistency group: storage system {}, group {}", storageSystem.getNativeId(), groupDisplayName );
            } else {
                _log.info("Deleting system replication group: storage system {}, group {}", storageSystem.getNativeId(), groupDisplayName );
                _log.info("Replication groups are not supported for external devices. Do not call driver." );
                taskCompleter.ready(dbClient);
                return;
            }

            // prepare driver consistency group
            VolumeConsistencyGroup driverCG = new VolumeConsistencyGroup();
            driverCG.setDisplayName(groupDisplayName);
            driverCG.setNativeId(groupNativeId);
            driverCG.setStorageSystemId(storageSystem.getNativeId());

            // call driver
            BlockStorageDriver driver = getDriver(storageSystem.getSystemType());

            DriverTask task = driver.deleteConsistencyGroup(driverCG);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                if (consistencyGroup != null) {
                    // I followed xtremio pattern to implement this logic.
                    consistencyGroup.removeSystemConsistencyGroup(URIUtil.asString(storageSystem.getId()), groupDisplayName);
                    dbClient.updateObject(consistencyGroup);

                    // have to read again to get updated systemConsistencyGroup map
                    consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);

                    /*
                     * Verify if the BlockConsistencyGroup references any LOCAL arrays.
                     * If we no longer have any references we can remove the 'LOCAL' type from the BlockConsistencyGroup.
                     */
                    List<URI> referencedArrays = BlockConsistencyGroupUtils.getLocalSystems(consistencyGroup, dbClient);


                    boolean cgReferenced = referencedArrays != null && !referencedArrays.isEmpty();
                    if (!cgReferenced) {
                        // Remove the LOCAL type
                        StringSet cgTypes = consistencyGroup.getTypes();
                        cgTypes.remove(BlockConsistencyGroup.Types.LOCAL.name());
                        consistencyGroup.setTypes(cgTypes);

                        // Remove the referenced storage system as well, but only if there are no other types
                        // of storage systems associated with the CG.
                        if (!BlockConsistencyGroupUtils.referencesNonLocalCgs(consistencyGroup, dbClient)) {
                            consistencyGroup.setStorageController(NullColumnValueGetter.getNullURI());

                            // Update the consistency group model
                            consistencyGroup.setInactive(markInactive);
                        }
                    } else {
                        _log.info("*** Referenced arrays {}", referencedArrays.toString());
                    }
                    dbClient.updateObject(consistencyGroup);
                }
                String msg = String.format("doDeleteConsistencyGroup -- Delete consistency group: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                String errorMsg = String.format("doDeleteConsistencyGroup -- Failed to delete Consistency Group: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.deleteConsistencyGroupFailed("doDeleteConsistencyGroup", errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String errorMsg = String.format("doDeleteConsistencyGroup -- Failed to delete Consistency Group: %s .", e.getMessage());
            _log.error(errorMsg, e);
            ServiceError serviceError = ExternalDeviceException.errors.deleteConsistencyGroupFailed("doDeleteConsistencyGroup", errorMsg);
            taskCompleter.error(dbClient, serviceError);
        } finally {
            _log.info("Delete consistency group: END...");
        }
    }

    @Override
    public void doExportCreate(StorageSystem storage,
                                    ExportMask exportMask, Map<URI, Integer> volumeMap,
                                    List<Initiator> initiators, List<URI> targets,
                                    TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportCreate START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(storage.getSystemType(), volumeMap, dbClient);
        exportMaskOperationsHelper.createExportMask(storage, exportMask.getId(), volumeLunArray, targets, initiators, taskCompleter);
        _log.info("{} doExportCreate END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddVolume(StorageSystem storage, ExportMask exportMask,
                                  URI volume, Integer lun, List<Initiator> initiators, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        map.put(volume, lun);
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(storage.getSystemType(), map, dbClient);
        exportMaskOperationsHelper.addVolumes(storage, exportMask.getId(), volumeLunArray, initiators, taskCompleter);
        _log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }


    @Override
    public void doExportAddVolumes(StorageSystem storage,
                                   ExportMask exportMask, List<Initiator> initiators,
                                   Map<URI, Integer> volumes, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(storage.getSystemType(), volumes, dbClient);
        exportMaskOperationsHelper.addVolumes(storage, exportMask.getId(),
                volumeLunArray, initiators, taskCompleter);
        _log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveVolume(StorageSystem storage,
                                     ExportMask exportMask, URI volume, List<Initiator> initiators, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeVolumes(storage, exportMask.getId(), Arrays.asList(volume), initiators, taskCompleter);
        _log.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveVolumes(StorageSystem storage,
                                      ExportMask exportMask, List<URI> volumes,
                                      List<Initiator> initiators, 
                                      TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveVolumes START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeVolumes(storage, exportMask.getId(), volumes,
                initiators, taskCompleter);
        _log.info("{} doExportRemoveVolumes END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddInitiator(StorageSystem storage,
                                     ExportMask exportMask, List<URI> volumeURIs, Initiator initiator,
                                     List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.addInitiators(storage, exportMask.getId(), volumeURIs, Arrays.asList(initiator), targets, taskCompleter);
        _log.info("{} doExportAddInitiator END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddInitiators(StorageSystem storage,
                                      ExportMask exportMask, List<URI> volumeURIs,
                                      List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddInitiators START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.addInitiators(storage, exportMask.getId(), volumeURIs, initiators, targets, taskCompleter);
        _log.info("{} doExportAddInitiators END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveInitiator(StorageSystem storage,
                                        ExportMask exportMask, List<URI> volumes, Initiator initiator,
                                        List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeInitiators(storage, exportMask.getId(), volumes, Arrays.asList(initiator), targets, taskCompleter);
        _log.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveInitiators(StorageSystem storage,
                                         ExportMask exportMask, List<URI> volumes,
                                         List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiators START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeInitiators(storage, exportMask.getId(), volumes, initiators, targets, taskCompleter);
        _log.info("{} doExportRemoveInitiators END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportDelete(StorageSystem storage,
                                    ExportMask exportMask, List<URI> volumeURIs, List<URI> initiatorURIs, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportDelete START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.deleteExportMask(storage, exportMask.getId(), new ArrayList<URI>(),
                new ArrayList<URI>(), new ArrayList<Initiator>(), taskCompleter);
        _log.info("{} doExportDelete END ...", storage.getSerialNumber());
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) throws DeviceControllerException {
        return exportMaskOperationsHelper.refreshExportMask(storage, mask);
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


    private void updateVolumesWithDriverVolumeInfo(DbClient dbClient, Map<StorageVolume, Volume> driverVolumesMap, Set<URI> consistencyGroups)
                  throws IOException {
        for (Map.Entry driverVolumeToVolume : driverVolumesMap.entrySet()) {
            StorageVolume driverVolume = (StorageVolume)driverVolumeToVolume.getKey();
            Volume volume = (Volume)driverVolumeToVolume.getValue();
            if (driverVolume.getNativeId() != null && driverVolume.getNativeId().length() > 0) {
                volume.setNativeId(driverVolume.getNativeId());
                volume.setDeviceLabel(driverVolume.getDeviceLabel());
                volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));

                if (driverVolume.getWwn() == null) {
                    volume.setWWN(String.format("%s%s", driverVolume.getStorageSystemId(), driverVolume.getNativeId()));
                } else {
                    volume.setWWN(driverVolume.getWwn());
                }
                volume.setProvisionedCapacity(driverVolume.getProvisionedCapacity());
                volume.setAllocatedCapacity(driverVolume.getAllocatedCapacity());
                if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                    consistencyGroups.add(volume.getConsistencyGroup());
                }

                String compressionRatio = StorageCapabilitiesUtils.getVolumeCompressionRatio(driverVolume);
                if (compressionRatio != null) {
                    volume.setCompressionRatio(compressionRatio);
                }
            } else {
                volume.setInactive(true);
            }
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
        DriverTask task = driver.createVolumeSnapshot(Collections.unmodifiableList(driverSnapshots), null);
        // todo: need to implement support for async case.
        if (task.getStatus() == DriverTask.TaskStatus.READY) {
            // update snapshots
            for (VolumeSnapshot driverSnapshot : driverSnapshotToSnapshotMap.keySet()) {
                BlockSnapshot snapshot = driverSnapshotToSnapshotMap.get(driverSnapshot);
                snapshot.setNativeId(driverSnapshot.getNativeId());
                snapshot.setDeviceLabel(driverSnapshot.getDeviceLabel());
                snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storageSystem, snapshot));
                snapshot.setIsSyncActive(true);
                snapshot.setReplicationGroupInstance(driverSnapshot.getConsistencyGroup());
                if (driverSnapshot.getProvisionedCapacity() > 0) {
                    snapshot.setProvisionedCapacity(driverSnapshot.getProvisionedCapacity());
                }
                if (driverSnapshot.getAllocatedCapacity() > 0) {
                    snapshot.setAllocatedCapacity(driverSnapshot.getAllocatedCapacity());
                }
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

        // Prepare driver consistency group of the parent volume
        VolumeConsistencyGroup driverCG = new VolumeConsistencyGroup();
        driverCG.setNativeId(consistencyGroup.getNativeId());
        driverCG.setDisplayName(consistencyGroup.getLabel());
        driverCG.setStorageSystemId(storageSystem.getNativeId());
        // call driver
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = driver.createConsistencyGroupSnapshot(driverCG, Collections.unmodifiableList(driverSnapshots), null);
        // todo: need to implement support for async case.
        if (task.getStatus() == DriverTask.TaskStatus.READY) {
            // update snapshots
            for (VolumeSnapshot driverSnapshot : driverSnapshotToSnapshotMap.keySet()) {
                BlockSnapshot snapshot = driverSnapshotToSnapshotMap.get(driverSnapshot);
                snapshot.setNativeId(driverSnapshot.getNativeId());
                snapshot.setDeviceLabel(driverSnapshot.getDeviceLabel());
                snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storageSystem, snapshot));
                snapshot.setIsSyncActive(true);
                // we use driver snapshot consistency group id as replication group label for group snapshots
                snapshot.setReplicationGroupInstance(driverSnapshot.getConsistencyGroup());
                if (driverSnapshot.getProvisionedCapacity() > 0) {
                    snapshot.setProvisionedCapacity(driverSnapshot.getProvisionedCapacity());
                }
                if (driverSnapshot.getAllocatedCapacity() > 0) {
                    snapshot.setAllocatedCapacity(driverSnapshot.getAllocatedCapacity());
                }
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

    private void deleteVolumeSnapshot(StorageSystem storageSystem, URI snapshot,
                                      TaskCompleter taskCompleter) {
        BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);
        if (blockSnapshot != null && !blockSnapshot.getInactive() &&
                // If the blockSnapshot.nativeId is not filled in than the
                // snapshot create may have failed somehow, so we'll allow
                // this case to be marked as success, so that the inactive
                // state against the BlockSnapshot object can be set.
                !StringUtils.isEmpty(blockSnapshot.getNativeId())) {
            _log.info("Deleting snapshot of a volume. Snapshot: {}", snapshot);
            Volume parent = dbClient.queryObject(Volume.class, blockSnapshot.getParent().getURI());
            VolumeSnapshot driverSnapshot = new VolumeSnapshot();
            driverSnapshot.setStorageSystemId(storageSystem.getNativeId());
            driverSnapshot.setNativeId(blockSnapshot.getNativeId());
            driverSnapshot.setParentId(parent.getNativeId());
            driverSnapshot.setConsistencyGroup(blockSnapshot.getReplicationGroupInstance());
            // call driver
            BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
            DriverTask task = driver.deleteVolumeSnapshot(driverSnapshot);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // update snapshots
                blockSnapshot.setInactive(true);
                dbClient.updateObject(blockSnapshot);
                String msg = String.format("deleteVolumeSnapshot -- Deleted snapshot: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                String errorMsg = String.format("doDeleteSnapshot -- Failed to delete snapshot: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.deleteSnapshotFailed("doDeleteSnapshot", errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } else if (blockSnapshot != null) {
            blockSnapshot.setInactive(true);
            dbClient.updateObject(blockSnapshot);
            String msg = String.format("deleteVolumeSnapshot -- Deleted snapshot: %s .", blockSnapshot.getId());
            _log.info(msg);
            taskCompleter.ready(dbClient);
        }
    }

    private void deleteGroupSnapshots(StorageSystem storageSystem, List<BlockSnapshot> groupSnapshots,
                                      TaskCompleter taskCompleter) {
        _log.info("Deleting snapshot of consistency group. Snapshots: "+Joiner.on("\t").join(groupSnapshots));
        URI cgUri = groupSnapshots.get(0).getConsistencyGroup();
        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
        List<VolumeSnapshot> driverSnapshots = new ArrayList<>();
        for (BlockSnapshot blockSnapshot : groupSnapshots) {
            VolumeSnapshot driverSnapshot = new VolumeSnapshot();
            driverSnapshot.setStorageSystemId(storageSystem.getNativeId());
            driverSnapshot.setNativeId(blockSnapshot.getNativeId());
            driverSnapshot.setConsistencyGroup(blockSnapshot.getReplicationGroupInstance());
            Volume parent = dbClient.queryObject(Volume.class, blockSnapshot.getParent().getURI());
            driverSnapshot.setParentId(parent.getNativeId());
            driverSnapshots.add(driverSnapshot);
        }
        // call driver
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        DriverTask task = driver.deleteConsistencyGroupSnapshot(Collections.unmodifiableList(driverSnapshots));
        // todo: need to implement support for async case.
        if (task.getStatus() == DriverTask.TaskStatus.READY) {
            // update snapshots
            for (BlockSnapshot blockSnapshot : groupSnapshots) {
                blockSnapshot.setInactive(true);
            }
            dbClient.updateObject(groupSnapshots);
            String msg = String.format("deleteGroupSnapshots -- Deleted group snapshot: %s .", task.getMessage());
            _log.info(msg);
            taskCompleter.ready(dbClient);
        } else {
            String errorMsg = String.format("doDeleteSnapshot -- Failed to delete group snapshot: %s .", task.getMessage());
            _log.error(errorMsg);
            ServiceError serviceError = ExternalDeviceException.errors.deleteGroupSnapshotFailed("doDeleteSnapshot", errorMsg);
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
        dbClient.updateObject(updateCGs);
    }

    private Volume getSnapshotParentVolume(BlockSnapshot snapshot) {
        Volume sourceVolume = null;
        URI sourceVolURI = snapshot.getParent().getURI();
        if (!NullColumnValueGetter.isNullURI(sourceVolURI)) {
            sourceVolume = dbClient.queryObject(Volume.class, sourceVolURI);
        }
        return sourceVolume;
    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
                                                 List<String> initiatorNames, boolean mustHaveAllPorts) throws DeviceControllerException {
        return exportMaskOperationsHelper.findExportMasks(storage, initiatorNames, mustHaveAllPorts);
    }

    @Override
    public void doWaitForSynchronized(Class<? extends BlockObject> clazz, StorageSystem storageObj, URI target, TaskCompleter completer) {
        _log.info("No support for wait for synchronization for external devices.");
        completer.ready(dbClient);
    }

    @Override
    public void doWaitForGroupSynchronized(StorageSystem storageObj, List<URI> target, TaskCompleter completer)
    {
        _log.info("No support for wait for synchronization for external devices.");
        completer.ready(dbClient);

    }
    
    /**
     * Method determines if the passed task status indicates that the task is completed
     * and is in a terminal state.
     * 
     * Terminal states are:
     *   READY
     *   FAILED
     *   PARTIALLY_FAILED
     *   WARNING
     *   ABORTED
     *   
     * Non-Terminal states are:
     *    QUEUED
     *    PROVISIONING  
     * 
     * @param taskStatus A reference to the task status
     * 
     * @return true if the state is terminal, false otherwise.
     */
    public boolean isTaskInTerminalState(DriverTask.TaskStatus taskStatus) {
        if (DriverTask.TaskStatus.PROVISIONING == taskStatus || DriverTask.TaskStatus.QUEUED == taskStatus) {
            return false;
        } else {
            return true;
        }
    }
    
    /**
     * Static method for getting an initialized block storage driver.
     * 
     * @param driverType The driver system type.
     * 
     * @return A reference to the initialized block storage driver.
     */
    public static synchronized BlockStorageDriver getBlockStorageDriver(String driverType) {
        return blockDrivers.get(driverType);
    }

    @Override
    public void createRemoteReplicationGroup(URI groupURI, List<URI> sourcePortIds, List<URI> targetPortIds, TaskCompleter taskCompleter) {

        RemoteReplicationGroup systemGroup = dbClient.queryObject(RemoteReplicationGroup.class, groupURI);
        _log.info("Create remote replication group: {}, id: {} .", systemGroup.getLabel(), groupURI);

        try {
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, systemGroup.getSourceSystem());
            List<StoragePort> sourcePorts = dbClient.queryObject(StoragePort.class, sourcePortIds);
            List<StoragePort> targetPorts = dbClient.queryObject(StoragePort.class, targetPortIds);
            RemoteReplicationDriver driver = (RemoteReplicationDriver)getDriver(sourceSystem.getSystemType());

            // prepare driver group
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup driverGroup =
                    prepareDriverRemoteReplicationGroup(systemGroup, sourcePorts, targetPorts);

            StorageCapabilities storageCapabilities = new StorageCapabilities();
            addRemoteReplicationCapabilities(storageCapabilities, systemGroup.getProperties());
            // call driver
            DriverTask task = driver.createRemoteReplicationGroup(driverGroup, storageCapabilities);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // update group and update parent replication set
                systemGroup.setNativeId(driverGroup.getNativeId());
                systemGroup.setReplicationState(driverGroup.getReplicationState());
                systemGroup.setDeviceLabel(driverGroup.getDeviceLabel());

                dbClient.updateObject(systemGroup);
                String msg = String.format("createRemoteReplicationGroup -- Created remote replication group: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                String errorMsg = String.format("createRemoteReplicationGroup -- Failed to create remote replication group: %s .", task.getMessage());
                _log.error(errorMsg);
                systemGroup.setInactive(true);
                ServiceError serviceError = ExternalDeviceException.errors.createRemoteReplicationGroupFailed(systemGroup.getLabel(), errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String errorMsg = String.format("createRemoteReplicationGroup -- Failed to create remote replication group: %s . %s",
                    systemGroup.getLabel(), e.getMessage());
            _log.error(errorMsg, e);
            ServiceError serviceError = ExternalDeviceException.errors.createRemoteReplicationGroupFailed(systemGroup.getLabel(), errorMsg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    /**
     * Convert db model type ports to sb sdk model type ports, and build a map in which the key is storage system native id
     * and the value is ports of this storage system that are used for remote replication
     */
    private Map<String, Set<com.emc.storageos.storagedriver.model.StoragePort>> preparePortsMap(String system, List<StoragePort> ports) {
        Set<com.emc.storageos.storagedriver.model.StoragePort> storagePorts = new HashSet<>();
        for (StoragePort port : ports) {
            com.emc.storageos.storagedriver.model.StoragePort storagePort = new com.emc.storageos.storagedriver.model.StoragePort();
            storagePort.setNativeId(port.getNativeId());
            storagePort.setPortNetworkId(port.getPortNetworkId());
            storagePort.setStorageSystemId(port.getStorageDevice().toString());
            storagePorts.add(storagePort);
        }
        Map<String, Set<com.emc.storageos.storagedriver.model.StoragePort>> portsMap = new HashMap<>();
        portsMap.put(system, storagePorts);
        return portsMap;
        
    }

    @Override
    public void createGroupReplicationPairs(List<RemoteReplicationPair> systemReplicationPairs, TaskCompleter taskCompleter) {

        _log.info("Create group replication pairs in group {}\n" +
                "for remote replication pairs: {}", systemReplicationPairs.get(0).getReplicationGroup(), systemReplicationPairs);

        try {
            // prepare driver replication pairs and call driver
            List<com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair> driverRRPairs = new ArrayList<>();
            prepareDriverRemoteReplicationPairs(systemReplicationPairs, driverRRPairs);
            StorageCapabilities storageCapabilities = new StorageCapabilities();
            addRemoteReplicationCapabilities(storageCapabilities, systemReplicationPairs.get(0).getProperties());

            // call driver
            RemoteReplicationDriver driver = getDriver(systemReplicationPairs.get(0));
            DriverTask task = driver.createGroupReplicationPairs(Collections.unmodifiableList(driverRRPairs), storageCapabilities);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // store system pairs in database
                for (int i=0; i<driverRRPairs.size(); i++) {
                    systemReplicationPairs.get(i).setNativeId(driverRRPairs.get(i).getNativeId());
                    systemReplicationPairs.get(i).setReplicationState(driverRRPairs.get(i).getReplicationState());
                    systemReplicationPairs.get(i).setReplicationDirection(driverRRPairs.get(i).getReplicationDirection());
                }
                dbClient.createObject(systemReplicationPairs);

                String msg = String.format("createGroupReplicationPairs -- Created group replication pairs: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                String errorMsg = String.format("createGroupReplicationPairs -- Failed to create group replication pairs: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createGroupRemoteReplicationPairsFailed(
                        driverRRPairs.get(0).getReplicationGroupNativeId(), errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String errorMsg = String.format("createGroupReplicationPairs -- Failed to create group replication pairs. " +
                    e.getMessage());
            _log.error(errorMsg, e);
            ServiceError serviceError = ExternalDeviceException.errors.createGroupRemoteReplicationPairsFailed(
                    systemReplicationPairs.get(0).getReplicationGroup().toString(), errorMsg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void createSetReplicationPairs(List<RemoteReplicationPair> systemReplicationPairs, TaskCompleter taskCompleter) {
        _log.info("Create Set replication pairs in set {}\n" +
                "for remote replication pairs: {}", systemReplicationPairs.get(0).getReplicationSet(), systemReplicationPairs);

        try {
            // prepare driver replication pairs and call driver
            List<com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair> driverRRPairs = new ArrayList<>();
            prepareDriverRemoteReplicationPairs(systemReplicationPairs, driverRRPairs);
            StorageCapabilities storageCapabilities = new StorageCapabilities();
            addRemoteReplicationCapabilities(storageCapabilities, systemReplicationPairs.get(0).getProperties());

            // call driver
            RemoteReplicationDriver driver = getDriver(systemReplicationPairs.get(0));
            DriverTask task = driver.createSetReplicationPairs(Collections.unmodifiableList(driverRRPairs), storageCapabilities);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // store system pairs in database
                for (int i=0; i<driverRRPairs.size(); i++) {
                    systemReplicationPairs.get(i).setNativeId(driverRRPairs.get(i).getNativeId());
                    systemReplicationPairs.get(i).setReplicationState(driverRRPairs.get(i).getReplicationState());
                    systemReplicationPairs.get(i).setReplicationDirection(driverRRPairs.get(i).getReplicationDirection());
                }
                dbClient.createObject(systemReplicationPairs);

                String msg = String.format("createSetReplicationPairs -- Created set replication pairs: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                String errorMsg = String.format("createSetReplicationPairs -- Failed to create set replication pairs: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createSetRemoteReplicationPairsFailed(
                        driverRRPairs.get(0).getReplicationSetNativeId(), errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String errorMsg = String.format("createSetReplicationPairs -- Failed to create set replication pairs");
            _log.error(errorMsg, e);
            ServiceError serviceError = ExternalDeviceException.errors.createSetRemoteReplicationPairsFailed(
                    systemReplicationPairs.get(0).getReplicationSet().toString(), errorMsg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void deleteReplicationPairs(List<URI> replicationPairs, TaskCompleter taskCompleter) {
        _log.info("Delete replication pairs: \n" +
                           "\t\t {}", replicationPairs);

        try {
            // prepare driver replication pairs and call driver
            List<RemoteReplicationPair> systemReplicationPairs = dbClient.queryObject(RemoteReplicationPair.class, replicationPairs);
            List<com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair> driverRRPairs = new ArrayList<>();
            prepareDriverRemoteReplicationPairs(systemReplicationPairs, driverRRPairs);

            // call driver
            RemoteReplicationDriver driver = getDriver(systemReplicationPairs.get(0));
            DriverTask task = driver.deleteReplicationPairs(Collections.unmodifiableList(driverRRPairs), null);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // delete system replication pairs in database
                dbClient.markForDeletion(systemReplicationPairs);
                String msg = String.format("deleteReplicationPairs -- Deleted replication pairs: %s .", task.getMessage());
                _log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                String errorMsg = String.format("deleteReplicationPairs -- Failed to delete replication pairs: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.deleteRemoteReplicationPairsFailed(errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String errorMsg = String.format("deleteReplicationPairs -- Failed to delete replication pairs: %s", replicationPairs);
            _log.error(errorMsg, e);
            ServiceError serviceError = ExternalDeviceException.errors.deleteRemoteReplicationPairsFailed(errorMsg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void establish(RemoteReplicationElement replicationElement, TaskCompleter taskCompleter) {
        _log.info("Establish remote replication element {} with system id {}", replicationElement.getType(), replicationElement.getElementUri());

        RemoteReplicationOperationHandler establishHandler = new RemoteReplicationOperationHandler() {
            @Override
            protected DriverTask doOperation() {
                return getDriver().establish(Collections.unmodifiableList(getDriverRRPairs()), getContext(), null);
            }
        };
        establishHandler.processRemoteReplicationTask(replicationElement, taskCompleter, RemoteReplicationOperations.ESTABLISH);
    }

    @Override
    public void split(RemoteReplicationElement replicationElement, TaskCompleter taskCompleter) {
        _log.info("Split remote replication element {} with system id {}", replicationElement.getType(), replicationElement.getElementUri());

        RemoteReplicationOperationHandler splitHandler = new RemoteReplicationOperationHandler() {
            @Override
            protected DriverTask doOperation() {
                return getDriver().split(Collections.unmodifiableList(getDriverRRPairs()), getContext(), null);
            }
        };
        splitHandler.processRemoteReplicationTask(replicationElement, taskCompleter, RemoteReplicationOperations.SPLIT);
    }

    @Override
    public void suspend(RemoteReplicationElement replicationElement, TaskCompleter taskCompleter) {
        _log.info("Suspend remote replication element {} with system id {}", replicationElement.getType(), replicationElement.getElementUri());

        RemoteReplicationOperationHandler suspendHandler = new RemoteReplicationOperationHandler() {
            @Override
            protected DriverTask doOperation() {
                return getDriver().suspend(Collections.unmodifiableList(getDriverRRPairs()), getContext(), null);
            }
        };
        suspendHandler.processRemoteReplicationTask(replicationElement, taskCompleter, RemoteReplicationOperations.SUSPEND);
    }

    @Override
    public void resume(RemoteReplicationElement replicationElement, TaskCompleter taskCompleter) {
        _log.info("Resume remote replication element {} with system id {}", replicationElement.getType(), replicationElement.getElementUri());

        RemoteReplicationOperationHandler resumeHandler = new RemoteReplicationOperationHandler() {
            @Override
            protected DriverTask doOperation() {
                return getDriver().resume(Collections.unmodifiableList(getDriverRRPairs()), getContext(), null);
            }
        };
        resumeHandler.processRemoteReplicationTask(replicationElement, taskCompleter, RemoteReplicationOperations.RESUME);
    }

    @Override
    public void failover(RemoteReplicationElement replicationElement, RemoteReplicationFailoverCompleter taskCompleter) {
        _log.info("Failover remote replication element {} with system id {}", replicationElement.getType(), replicationElement.getElementUri());

        RemoteReplicationOperationHandler failoverHandler = new RemoteReplicationOperationHandler() {
            @Override
            protected DriverTask doOperation() {
                return getDriver().failover(Collections.unmodifiableList(getDriverRRPairs()), getContext(), null);
            }
        };
        failoverHandler.processRemoteReplicationTask(replicationElement, taskCompleter, RemoteReplicationOperations.FAIL_OVER);
    }

    @Override
    public void failback(RemoteReplicationElement replicationElement, TaskCompleter taskCompleter) {
        _log.info("Failback remote replication element {} with system id {}", replicationElement.getType(), replicationElement.getElementUri());

        RemoteReplicationOperationHandler failbackHandler = new RemoteReplicationOperationHandler() {
            @Override
            protected DriverTask doOperation() {
                return getDriver().failback(Collections.unmodifiableList(getDriverRRPairs()), getContext(), null);
            }
        };
        failbackHandler.processRemoteReplicationTask(replicationElement, taskCompleter, RemoteReplicationOperations.FAIL_BACK);
    }

    @Override
    public void swap(RemoteReplicationElement replicationElement, TaskCompleter taskCompleter) {
        _log.info("Swap remote replication element {} with system id {}", replicationElement.getType(), replicationElement.getElementUri());

        RemoteReplicationOperationHandler swapHandler = new RemoteReplicationOperationHandler() {
            @Override
            protected DriverTask doOperation() {
                return getDriver().swap(Collections.unmodifiableList(getDriverRRPairs()), getContext(), null);
            }
        };
        swapHandler.processRemoteReplicationTask(replicationElement, taskCompleter, RemoteReplicationOperations.SWAP);
    }

    @Override
    public void stop(final RemoteReplicationElement replicationElement, TaskCompleter taskCompleter) {
        _log.info("Stop remote replication element {} with system id {}", replicationElement.getType(), replicationElement.getElementUri());

        RemoteReplicationOperationHandler stopHandler = new RemoteReplicationOperationHandler() {
            @Override
            protected DriverTask doOperation() {
                DriverTask task = getDriver().stop(Collections.unmodifiableList(getDriverRRPairs()), getContext(), null);
                return task;
            }

            @Override
            protected void processOperationResult() {
                super.processOperationResult();

                List<RemoteReplicationPair> systemPairs = getSystemRRPairs();

                if (replicationElement.getType() == ElementType.CONSISTENCY_GROUP) {
                    BlockConsistencyGroup sourceCg = dbClient.queryObject(BlockConsistencyGroup.class,replicationElement.getElementUri());
                    sourceCg.getRequestedTypes().remove(Types.RR.toString());
                    dbClient.updateObject(sourceCg);
                    Set<URI> targetCgUris = new HashSet<>();

                    for (RemoteReplicationPair pair : systemPairs) {
                        targetCgUris.add(dbClient.queryObject(Volume.class, pair.getTargetElement().getURI()).getConsistencyGroup());
                    }
                    for (URI targetCgUri : targetCgUris) {
                        BlockConsistencyGroup targetCg = dbClient.queryObject(BlockConsistencyGroup.class, targetCgUri);
                        targetCg.setAlternateLabel(NullColumnValueGetter.getNullStr());
                        dbClient.updateObject(targetCg);
                    }
                }

                dbClient.removeObject(systemPairs.toArray(new RemoteReplicationPair[systemPairs.size()]));
            }
        };
        stopHandler.processRemoteReplicationTask(replicationElement, taskCompleter, RemoteReplicationOperations.STOP);
    }

    @Override
    public void changeReplicationMode(final RemoteReplicationElement replicationElement, final String newMode, TaskCompleter taskCompleter) {
        _log.info("Change mode of remote replication element {} with system id {} to {}", replicationElement.getType(),
                replicationElement.getElementUri(), newMode);

        RemoteReplicationOperationHandler changeModeHandler = new RemoteReplicationOperationHandler() {
            @Override
            protected DriverTask doOperation() {
                return getDriver().changeReplicationMode(Collections.unmodifiableList(getDriverRRPairs()),newMode, getContext(), null);
            }

            @Override
            protected void processOperationResult() {
                super.processOperationResult();

                List<RemoteReplicationPair> systemPairs = getSystemRRPairs();
                for (RemoteReplicationPair pair : systemPairs) {
                    pair.setReplicationMode(newMode);
                }
                dbClient.updateObject(systemPairs);

                if (replicationElement.getType() == ElementType.REPLICATION_GROUP) {
                    RemoteReplicationGroup group = getReplicationGroup();
                    group.setReplicationMode(newMode);
                    dbClient.updateObject(group);
                }
                if (replicationElement.getType() == ElementType.REPLICATION_SET) {
                    RemoteReplicationSet set = getReplicationSet();
                    List<RemoteReplicationGroup> groups = RemoteReplicationUtils.getRemoteReplicationGroupsForRrSet(dbClient,set);
                    for (RemoteReplicationGroup group : groups) {
                        group.setReplicationMode(newMode);
                    }
                    dbClient.updateObject(groups);
                }
            }
        };
        changeModeHandler.processRemoteReplicationTask(replicationElement, taskCompleter, RemoteReplicationOperations.CHANGE_REPLICATION_MODE);
    }

    @Override
    public void movePair(URI replicationPair, URI targetGroup, TaskCompleter taskCompleter) {
        _log.info("Move remote replication pair {} to {}", replicationPair, targetGroup);

        try {
            // 1. Get driver instance
            RemoteReplicationPair systemPair = dbClient.queryObject(RemoteReplicationPair.class, replicationPair);
            RemoteReplicationSet pairSet = dbClient.queryObject(RemoteReplicationSet.class, systemPair.getReplicationSet());
            RemoteReplicationDriver driver = (RemoteReplicationDriver) getDriver(pairSet.getStorageSystemType());

            // 2. Prepare parameters
            // Note: these fields of driverGroup are not initialized: capabilities, sourcePorts and targetPorts
            RemoteReplicationGroup systemGroup = dbClient.queryObject(RemoteReplicationGroup.class, targetGroup);
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup driverGroup = prepareDriverRemoteReplicationGroup(
                    systemGroup, null, null);
            driverGroup.setNativeId(systemGroup.getNativeId());
            driverGroup.setDeviceLabel(systemGroup.getDeviceLabel());
            driverGroup.setReplicationState(systemGroup.getReplicationState());
            driverGroup.setIsGroupConsistencyEnforced(systemGroup.getIsGroupConsistencyEnforced());
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair driverPair = prepareDriverRemoteReplicationPair(systemPair);

            // 3. Invoke driver.movePair method
            DriverTask task = driver.movePair(driverPair, driverGroup, null);

            // 4. Update db status accordingly
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                systemPair.setReplicationGroup(targetGroup);
                if (!StringUtils.equals(driverPair.getReplicationMode(), systemPair.getReplicationMode())) {
                    systemPair.setReplicationMode(driverPair.getReplicationMode());
                }
                dbClient.updateObject(systemPair);
                _log.info(String.format("moveRemoteReplicationPair -- moved remote replication pair %s to %s: %s.",
                        replicationPair, targetGroup, task.getMessage()));
                taskCompleter.ready(dbClient);
            } else {
                String errorMsg = String.format(
                        "moveRemoteReplicationPair -- Failed to move remote replication pair %s to %s: %s.",
                        replicationPair, targetGroup, task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors
                        .moveRemoteReplicationPairFailed(replicationPair, targetGroup, errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (Exception e) {
            String errorMsg = String.format("moveRemoteReplicationPair -- Failed to move remote replication pair. %s",
                    e.getMessage());
            _log.error(errorMsg, e);
            ServiceError serviceError = ExternalDeviceException.errors.moveRemoteReplicationPairFailed(replicationPair,
                    targetGroup, errorMsg);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        // call driver to validate provider connection
        boolean isConnectionValid = false;
        try {
            StringBuffer providerID = new StringBuffer(ipAddress).append(
                    HDSConstants.HYPHEN_OPERATOR).append(portNumber);
            _log.info("Request to validate connection to provider, ID: {}", providerID);

            URIQueryResultList providerUriList = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getStorageProviderByProviderIDConstraint(providerID.toString()),
                    providerUriList);
            if (providerUriList.iterator().hasNext()) {
                StorageProvider storageProvider = dbClient.queryObject(StorageProvider.class,
                        providerUriList.iterator().next());
                isConnectionValid = validateStorageProviderConnection(storageProvider);
            } else {
               String msg = String.format("Cannot find provider with ID: %s ", providerID);
            }
        } catch (Exception ex) {
            _log.error(
                    "Problem in checking provider live connection with IP address and port: {}:{} due to: ",
                    ipAddress, portNumber, ex);
        }
        return isConnectionValid;
    }

    public boolean validateStorageProviderConnection(StorageProvider storageProvider) {
        boolean isConnectionValid = false;
        try {
            // call driver to validate provider connection
            // get driver for the provider
            BlockStorageDriver driver = getDriver(storageProvider.getInterfaceType());
            String username = storageProvider.getUserName();
            String password = storageProvider.getPassword();
            String hostName = storageProvider.getIPAddress();
            Integer providerPortNumber = storageProvider.getPortNumber();
            String providerType = storageProvider.getInterfaceType();
            Boolean useSsl = storageProvider.getUseSSL();
            String msg = String.format("Storage provider info: type: %s, host: %s, port: %s, user: %s, useSsl: %s",
                    providerType, hostName, providerPortNumber, username, useSsl);
            _log.info(msg);

            com.emc.storageos.storagedriver.model.StorageProvider driverProvider =
                    new com.emc.storageos.storagedriver.model.StorageProvider();
            // initialize driver provider
            driverProvider.setProviderHost(hostName);
            driverProvider.setPortNumber(providerPortNumber);
            driverProvider.setUsername(username);
            driverProvider.setPassword(password);
            driverProvider.setUseSSL(useSsl);
            driverProvider.setProviderType(providerType);

            isConnectionValid = driver.validateStorageProviderConnection(driverProvider);
        } catch (Exception ex) {
            _log.error("Problem in checking connection of provider {} due to: ", storageProvider.getLabel(), ex);
        }
        return isConnectionValid;
    }

    /**
     * Update storage pool capacity to the most recent values from  driver.
     * Release reserved capacity in the pool for set of reservedObjects.
     *
     * @param dbPool storage pool to update capacity
     * @param dbSystem storage system where the pool is located
     * @param reservedObjects list of reserved object (volumes/clones/mirrors)
     * @param dbClient db client
     */
    public static void updateStoragePoolCapacity(StoragePool dbPool, StorageSystem dbSystem,
                                                 List<URI> reservedObjects, DbClient dbClient) {
        _log.info(String.format("Update storage pool capacity for pool %s, system %s ", dbPool.getId(),
                dbSystem.getId()));
        BlockStorageDriver driver = getBlockStorageDriver(dbSystem.getSystemType());
        // refresh the pool
        dbPool = dbClient.queryObject(StoragePool.class, dbPool.getId());
        // rediscover driver storage pool
        com.emc.storageos.storagedriver.model.StoragePool driverPool = driver.getStorageObject(dbSystem.getNativeId(),
                dbPool.getNativeId(), com.emc.storageos.storagedriver.model.StoragePool.class);
        // update pool capacity in db
        if (driverPool != null) {
            _log.info(String.format("Driver pool %s info: free capacity %s, subscribed capacity %s ", driverPool.getNativeId(),
                    driverPool.getFreeCapacity(), driverPool.getSubscribedCapacity()));
            dbPool.setFreeCapacity(driverPool.getFreeCapacity());
            dbPool.setSubscribedCapacity(driverPool.getSubscribedCapacity());
        } else {
            _log.error("Driver pool for storage pool {} and storage system {} is null.", dbPool.getNativeId(), dbSystem.getNativeId());
        }
        // release reserved capacity
        dbPool.removeReservedCapacityForVolumes(URIUtil.asStrings(reservedObjects));
        dbClient.updateObject(dbPool);
    }

    /**
     * Builds driver replication pairs from system replication pairs.
     * @param systemReplicationPairs system replication pairs (input)
     * @param driverRRPairs driver replication pairs (output)
     */
    private void prepareDriverRemoteReplicationPairs(List<RemoteReplicationPair> systemReplicationPairs,
                                                     List<com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair> driverRRPairs) {

         for (RemoteReplicationPair systemPair : systemReplicationPairs) {
             driverRRPairs.add(prepareDriverRemoteReplicationPair(systemPair));
         }
    }

    private com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair prepareDriverRemoteReplicationPair(RemoteReplicationPair systemPair) {
        com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair driverPair =
                new com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair();
        // set source and target volume in the pair
        StorageVolume driverSourceVolume = new StorageVolume();
        StorageVolume driverTargetVolume = new StorageVolume();
        URI systemSourceVolumeUri = systemPair.getSourceElement().getURI();
        URI systemTargetVolumeUri = systemPair.getTargetElement().getURI();
        Volume systemSourceVolume = dbClient.queryObject(Volume.class, systemSourceVolumeUri);
        Volume systemTargetVolume = dbClient.queryObject(Volume.class, systemTargetVolumeUri);
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, systemSourceVolume.getStorageController());
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, systemTargetVolume.getStorageController());

        driverSourceVolume.setNativeId(systemSourceVolume.getNativeId());
        driverSourceVolume.setStorageSystemId(sourceSystem.getNativeId());
        driverTargetVolume.setNativeId(systemTargetVolume.getNativeId());
        driverTargetVolume.setStorageSystemId(targetSystem.getNativeId());

        if (systemPair.getNativeId() != null) {
            driverPair.setNativeId(systemPair.getNativeId());
        }

        if (systemPair.getReplicationDirection() != null) {
            driverPair.setReplicationDirection(systemPair.getReplicationDirection());
        }

        // set replication mode
        driverPair.setReplicationMode(systemPair.getReplicationMode());
        // set replication group and replication set native ids
        RemoteReplicationSet systemReplicationSet = dbClient.queryObject(RemoteReplicationSet.class, systemPair.getReplicationSet());
        driverPair.setReplicationSetNativeId(systemReplicationSet.getNativeId());
        if (systemPair.getReplicationGroup() != null) {
            RemoteReplicationGroup systemReplicationGroup = dbClient.queryObject(RemoteReplicationGroup.class, systemPair.getReplicationGroup());
            driverPair.setReplicationGroupNativeId(systemReplicationGroup.getNativeId());
        }
        // set replication state
        driverPair.setReplicationState(systemPair.getReplicationState());
        return driverPair;
    }

    private com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup prepareDriverRemoteReplicationGroup(
            RemoteReplicationGroup systemGroup, List<StoragePort> sourcePorts, List<StoragePort> targetPorts) {

        com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup driverGroup =
                new com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup();

        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, systemGroup.getSourceSystem());
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, systemGroup.getTargetSystem());
        driverGroup.setDisplayName(systemGroup.getDisplayName());
        driverGroup.setSourceSystemNativeId(sourceSystem.getNativeId());
        driverGroup.setTargetSystemNativeId(targetSystem.getNativeId());
        driverGroup.setReplicationMode(systemGroup.getReplicationMode());
        if (sourcePorts != null) {
            driverGroup.setSourcePorts(preparePortsMap(sourceSystem.getNativeId(), sourcePorts));
        }
        if (targetPorts != null) {
            driverGroup.setTargetPorts(preparePortsMap(targetSystem.getNativeId(), targetPorts));
        }

        // todo: complete
        return driverGroup;

    }

    private RemoteReplicationOperationContext initializeContext(RemoteReplicationPair rrPair,
                                                                com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType contextType) {
        URI rrSetURI = rrPair.getReplicationSet();
        URI rrGroupURI = rrPair.getReplicationGroup();
        String rrGroupNativeId = null;
        String rrGroupState = null;
        RemoteReplicationSet rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrSetURI);

        if (contextType != ElementType.REPLICATION_SET && !URIUtil.isNull(rrGroupURI)) {
            RemoteReplicationGroup remoteReplicationGroup = dbClient.queryObject(RemoteReplicationGroup.class, rrGroupURI);
            rrGroupNativeId = remoteReplicationGroup.getNativeId();
            rrGroupState = remoteReplicationGroup.getReplicationState();
        }
        // create context
        RemoteReplicationOperationContext context = new RemoteReplicationOperationContext(contextType);
        context.setRemoteReplicationSetNativeId(rrSet.getNativeId());
        context.setRemoteReplicationGroupNativeId(rrGroupNativeId);
        context.setRemoteReplicationSetState(rrSet.getReplicationState());
        context.setRemoteReplicationGroupState(rrGroupState);

        return context;
    }

    private void addRemoteReplicationCapabilities(StorageCapabilities storageCapabilities, StringMap properties) {
        // Create create_active capability.
        RemoteReplicationAttributes capabilityDefinition = new RemoteReplicationAttributes();
        Map<String, List<String>> capabilityProperties = new HashMap<>();
        String createState = null;
        if(properties != null) {
            // build capability property map
            for (Map.Entry<String, String> property : properties.entrySet()) {
                if (property.getKey().equals(RemoteReplicationAttributes.PROPERTY_NAME.CREATE_STATE.toString())) {
                    createState = property.getValue();
                    capabilityProperties.put(RemoteReplicationAttributes.PROPERTY_NAME.CREATE_STATE.toString(),
                            Collections.singletonList(createState));
                    String msg = String.format("Capability name: %s, value: %s .", RemoteReplicationAttributes.PROPERTY_NAME.CREATE_STATE.toString(),
                            createState);
                    _log.info(msg);
                } else {
                    String msg = String.format("Not supported property: %s, value: %s .", property.getKey(), property.getValue());
                    _log.warn(msg);
                }
            }

            if (capabilityProperties.isEmpty()) {
                String msg = String.format("There is no capabilities for RemoteReplicaionAttributes");
                _log.info(msg);
                return;
            }

            CapabilityInstance pairAttributes = new CapabilityInstance(capabilityDefinition.getId(),
                    capabilityDefinition.getId(), capabilityProperties);

            // Get the common capabilities for the passed storage capabilities.
            // If null, create and set it.
            CommonStorageCapabilities commonCapabilities = storageCapabilities.getCommonCapabilities();
            if (commonCapabilities == null) {
                commonCapabilities = new CommonStorageCapabilities();
                storageCapabilities.setCommonCapabilities(commonCapabilities);
            }

            // Get the data protection service options for the common capabilities.
            // If null, create it and set it.
            List<DataProtectionServiceOption> dataProtectionSvcOptions = commonCapabilities.getDataProtection();
            if (dataProtectionSvcOptions == null) {
                dataProtectionSvcOptions = new ArrayList<>();
                commonCapabilities.setDataProtection(dataProtectionSvcOptions);
            }

            // Create a new data protection service option for the pair capability
            // and add it to the list.
            DataProtectionServiceOption dataProtectionSvcOption = new DataProtectionServiceOption(Collections.singletonList(pairAttributes));
            dataProtectionSvcOptions.add(dataProtectionSvcOption);
        }
    }

    /**
     * Check if block object has exports on device
     *
     * @param driver storage driver
     * @param driverBlockObject driver block object
     * @return true/false
     */
    private boolean hasExports(BlockStorageDriver driver, StorageBlockObject driverBlockObject) {
        Map<String, HostExportInfo> blocObjectToHostExportInfo = null;

        // get HostExportInfo data for this block object from the driver
        if (driverBlockObject instanceof VolumeClone) {
            VolumeClone driverClone = (VolumeClone)driverBlockObject;
            blocObjectToHostExportInfo = driver.getCloneExportInfoForHosts(driverClone);
            _log.info("Export info for clone {} is {}:", driverClone, blocObjectToHostExportInfo);
        } else if (driverBlockObject instanceof VolumeSnapshot) {
            VolumeSnapshot driverSnapshot = (VolumeSnapshot) driverBlockObject;
            blocObjectToHostExportInfo = driver.getSnapshotExportInfoForHosts(driverSnapshot);
            _log.info("Export info for snapshot {} is {}:", driverSnapshot, blocObjectToHostExportInfo);
        } else if (driverBlockObject instanceof StorageVolume) {
            StorageVolume driverVolume = (StorageVolume)driverBlockObject;
            blocObjectToHostExportInfo = driver.getVolumeExportInfoForHosts(driverVolume);
            _log.info("Export info for volume {} is {}:", driverVolume, blocObjectToHostExportInfo);
        } else {
            // not supported type in this method
            String errorMsg = String.format("Method is not supported for %s objects.", driverBlockObject.getClass().getSimpleName());
            throw new RuntimeException(errorMsg);
        }

        return !(blocObjectToHostExportInfo == null || blocObjectToHostExportInfo.isEmpty());
    }

    /**
     * Every remote replication operation (such as suspend resume etc.) should extend
     * this class, override processRemoteReplicationTask method, and then run it.
     */
    private abstract class RemoteReplicationOperationHandler {
        private static final String OPERATION_SUCCESS_MSG_FMT = "Operation %s succeeded for remote replication element %s (system id: %s), message: %s";
        private static final String OPEARTION_FAILURE_MSG_FMT = "Operation %s failed for remote replication element %s (system id %s), message: %s";

        private URI elementURI;
        private ElementType elementType;
        private TaskCompleter taskCompleter;
        private RemoteReplicationOperations operation;

        private RemoteReplicationDriver driver;
        private RemoteReplicationOperationContext context;
        private RemoteReplicationSet replicationSet;
        private RemoteReplicationGroup replicationGroup;
        private List<RemoteReplicationPair> systemRRPairs;
        private List<com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair> driverRRPairs = new ArrayList<>();

        protected List<com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair> getDriverRRPairs() {
            return driverRRPairs;
        }

        protected RemoteReplicationGroup getReplicationGroup() {
            return replicationGroup;
        }

        protected RemoteReplicationSet getReplicationSet() {
            return replicationSet;
        }

        protected RemoteReplicationDriver getDriver() {
            return driver;
        }

        protected RemoteReplicationOperationContext getContext() {
            return context;
        }


        protected List<RemoteReplicationPair> getSystemRRPairs() {
            return systemRRPairs;
        }

        /**
         * Get all necessary parameters prepared to do the operation.
         */
        private void init(RemoteReplicationElement element, TaskCompleter taskCompleter, RemoteReplicationOperations operation) {
            this.taskCompleter = taskCompleter;
            this.operation = operation;
            this.elementURI = element.getElementUri();
            this.elementType = element.getType();

            if (elementURI == null || elementType == null) {
                throw new RuntimeException("Invalid parameter: remote replication element's type and URI can not be null");
            }

            switch (elementType) {
                case REPLICATION_GROUP:
                    replicationGroup = dbClient.queryObject(RemoteReplicationGroup.class, elementURI);
                    StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, replicationGroup.getSourceSystem());
                    driver = (RemoteReplicationDriver) ExternalBlockStorageDevice.this.getDriver(sourceSystem.getSystemType());
                    systemRRPairs = CustomQueryUtility.queryActiveResourcesByRelation(dbClient, elementURI,
                            RemoteReplicationPair.class, "replicationGroup");
                    validateSystemPairs();
                    context = initializeContext(systemRRPairs.get(0),
                            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_GROUP);
                    break;

                case REPLICATION_PAIR:
                    RemoteReplicationPair replicationPair = dbClient.queryObject(RemoteReplicationPair.class, elementURI);
                    systemRRPairs = new ArrayList<>();
                    systemRRPairs.add(replicationPair);
                    URI sourceVolumeURI = replicationPair.getSourceElement().getURI();
                    Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolumeURI);
                    sourceSystem = dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());
                    driver = (RemoteReplicationDriver) ExternalBlockStorageDevice.this.getDriver(sourceSystem.getSystemType());
                    context = initializeContext(systemRRPairs.get(0),
                            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR);
                    break;

                case CONSISTENCY_GROUP:
                    BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, elementURI);
                    sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());
                    driver = (RemoteReplicationDriver) ExternalBlockStorageDevice.this.getDriver(sourceSystem.getSystemType());
                    systemRRPairs = RemoteReplicationUtils.getRemoteReplicationPairsForSourceCG(cg, dbClient);
                    validateSystemPairs();
                    context = initializeContext(systemRRPairs.get(0),
                            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR);
                    break;

                case REPLICATION_SET:
                    replicationSet = dbClient.queryObject(RemoteReplicationSet.class, elementURI);
                    driver = (RemoteReplicationDriver) ExternalBlockStorageDevice.this.getDriver(replicationSet.getStorageSystemType());
                    systemRRPairs = RemoteReplicationUtils.findAllRemoteReplicationPairsByRrSet(elementURI, dbClient);
                    validateSystemPairs();
                    context = initializeContext(systemRRPairs.get(0),
                            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET);
                    break;

                default:
                    throw new RuntimeException(String.format("Undefined element type: %s", Strings.repr(elementType)));
            }

            if (systemRRPairs != null && !systemRRPairs.isEmpty()) {
                // prepare driver replication pairs
                prepareDriverRemoteReplicationPairs(systemRRPairs, driverRRPairs);
            }
        }

        protected abstract DriverTask doOperation();

        /**
         * Do operation and complete returned the driver task.
         */
        public void processRemoteReplicationTask(RemoteReplicationElement element, TaskCompleter taskCompleter,
                RemoteReplicationOperations operation) {
            try {
                init(element, taskCompleter, operation);
                DriverTask task = doOperation();

                if (task.getStatus() == DriverTask.TaskStatus.READY) {
                    succeed(task.getMessage());
                } else {
                    fail(task.getMessage());
                }
            } catch (Exception e) {
                _log.error("Operation failed", e);
                fail(e.getMessage());
            }
        }

        private void succeed(String message) {
            _log.info(String.format(OPERATION_SUCCESS_MSG_FMT, operation, elementType,elementURI, message));
            processOperationResult();
            taskCompleter.ready(dbClient);
        }

        private void fail(String message) {
            String errorMsg = String.format(OPEARTION_FAILURE_MSG_FMT, operation, elementType, elementURI, message);
            _log.error(errorMsg);
            ServiceError serviceError = ExternalDeviceException.errors.remoteReplicationLinkOperationFailed(
                    operation.toString().toLowerCase(), Strings.repr(elementType), Strings.repr(elementURI), errorMsg);
            taskCompleter.error(dbClient, serviceError);
        }

        private void validateSystemPairs() {
            if (systemRRPairs == null || systemRRPairs.isEmpty()) {
                throw new RuntimeException("No qualified remote replication pairs found");
            }
        }

        /**
         * This method is invoked to update DB status only when driver task
         * successfully returns.
         */
        protected void processOperationResult() {
            // set state in system pairs as set by driver
            // set replication direction in system pairs as set by driver
            if (systemRRPairs != null && !systemRRPairs.isEmpty()) {
                for (int i = 0; i < driverRRPairs.size(); i++) {
                    systemRRPairs.get(i).setReplicationState(driverRRPairs.get(i).getReplicationState());
                    systemRRPairs.get(i).setReplicationDirection(driverRRPairs.get(i).getReplicationDirection());
                }
                dbClient.updateObject(systemRRPairs);
            }

            // set state of container objects according to context
            if (replicationSet != null) {
                replicationSet.setReplicationState(context.getRemoteReplicationSetState());
                dbClient.updateObject(replicationSet);
                // update all rr groups' state within this rr set
                for (RemoteReplicationGroup rrGroup : RemoteReplicationUtils
                        .getRemoteReplicationGroupsForRrSet(dbClient, replicationSet)) {
                    rrGroup.setReplicationState(context.getRemoteReplicationSetState());
                    dbClient.updateObject(rrGroup);
                }
            }

            if (replicationGroup != null) {
                replicationGroup.setReplicationState(context.getRemoteReplicationGroupState());
                dbClient.updateObject(replicationGroup);
            }
        }
    }

}
