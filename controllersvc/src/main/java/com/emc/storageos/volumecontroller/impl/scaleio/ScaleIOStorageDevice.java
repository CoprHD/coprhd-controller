/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.scaleio;

import static com.emc.storageos.db.client.model.BlockSnapshot.inReplicationGroup;
import static java.util.Arrays.asList;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.scaleio.api.*;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.collect.Lists;

public class ScaleIOStorageDevice extends DefaultBlockStorageDevice {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOStorageDevice.class);
    private static final String VOLUME_NOT_MAPPED_TO_SDC = "Volume not mapped to SDC";
    private static final String VOLUME_NOT_MAPPED_TO_SCSI = "Volume not mapped to SCSI Initiator";
    private static final String VOLUME_NOT_FOUND = "Volume not found";
    private static final String ALREADY_MAPPED_TO = "already mapped to";

    private static volatile ScaleIOStorageDevice instance;

    private DbClient dbClient;
    private ScaleIOCLIFactory scaleIOCLIFactory;
    private SnapshotOperations snapshotOperations;
    private CloneOperations cloneOperations;

    public ScaleIOStorageDevice() {
        instance = this;
    }

    public static ScaleIOStorageDevice getInstance() {
        return instance;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setScaleIOCLIFactory(ScaleIOCLIFactory scaleIOCLIFactory) {
        this.scaleIOCLIFactory = scaleIOCLIFactory;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSnapshotOperations(SnapshotOperations snapshotOperations) {
        this.snapshotOperations = snapshotOperations;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setCloneOperations(CloneOperations cloneOperations) {
        this.cloneOperations = cloneOperations;
    }

    /**
     * Refresh connection status for all ScaleIO providers.
     * 
     * @return A list of providers whose connections were successful.
     */
    public List<URI> refreshConnectionStatusForAllSIOProviders() {
        log.info("Refreshing connection statuses for ScaleIO providers");

        List<URI> activeProviders = Lists.newArrayList();
        List<StorageProvider> providers = CustomQueryUtility.getActiveStorageProvidersByInterfaceType(dbClient,
                StorageProvider.InterfaceType.scaleio.name());

        for (StorageProvider provider : providers) {
            try {
                // Flag for success/failure
                boolean success = false;
                // Prepare to try secondary IPs if necessary
                StringSet secondaryIps = provider.getSecondaryIps();
                Iterator<String> iterator = secondaryIps.iterator();
                // Cache the current IP address
                String currentIPAddress = provider.getIPAddress();

                String nextIp = null;
                do {
                    try {
                        ScaleIOCLI cli = scaleIOCLIFactory.using(dbClient).getCLI(provider);
                        cli.queryClusterCommand();  // Ignore the result on success, otherwise catch the exception
                        log.info("Successfully connected to ScaleIO MDM {}: {}", provider.getIPAddress(), provider.getId());
                        success = true;
                        break;
                    } catch (Exception e) {
                        log.error(String.format("Failed to connect to ScaleIO MDM %s: %s",
                                provider.getIPAddress(), provider.getId()), e);

                        if (iterator.hasNext()) {
                            nextIp = iterator.next();
                            log.info("Attempting connection to potential new Primary MDM {}: {}", nextIp,
                                    provider.getId());
                            provider.setIPAddress(nextIp);
                        } else {
                            log.warn("Exhausted list of secondary IPs for ScaleIO provider: {}", provider.getId());
                            nextIp = null;
                        }
                    }
                } while (nextIp != null);  // while we have more IPs to try

                if (success) {
                    // Update secondary IP addresses if we switched over
                    if (!provider.getIPAddress().equalsIgnoreCase(currentIPAddress)) {
                        StringSet newSecondaryIps = new StringSet();
                        // Copy old secondary list
                        newSecondaryIps.addAll(secondaryIps);
                        // Remove the new primary IP
                        newSecondaryIps.remove(provider.getIPAddress());
                        // Add the old primary IP
                        newSecondaryIps.add(currentIPAddress);
                        // TODO Improve how we update the StringSet based on infra team suggestions
                        provider.setSecondaryIps(newSecondaryIps);
                    }

                    activeProviders.add(provider.getId());
                    provider.setConnectionStatus(StorageProvider.ConnectionStatus.CONNECTED.toString());
                } else {
                    provider.setIPAddress(currentIPAddress);
                    provider.setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED.toString());
                }
            } finally {
                dbClient.persistObject(provider);
            }
        }

        return activeProviders;
    }

    @Override
    public void doCreateVolumes(StorageSystem storage, StoragePool storagePool, String opId,
            List<Volume> volumes, VirtualPoolCapabilityValuesWrapper capabilities,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(dbClient).getCLI(storage);
            String errorMessage = null;
            boolean anyFailures = false;
            String protectionDomainName = storage.getSerialNumber();
            String storagePoolName = storagePool.getPoolName();
            Long volumeSize = capabilities.getSize() / ScaleIOCLIHelper.BYTES_IN_GB;
            int count = volumes.size();
            int index = 1;
            String failedVolumeName = null;
            Set<URI> poolsToUpdate = new HashSet<>();
            boolean thinlyProvisioned = capabilities.getThinProvisioning();
            Set<URI> consistencyGroups = new HashSet<>();
            Multimap<URI, String> poolToVolumesMap = ArrayListMultimap.create();
            String systemId = getScaleIOCustomerId(scaleIOCLI);
            for (; index <= count; index++) {
                Volume volume = volumes.get(index - 1);
                ScaleIOAddVolumeResult result =
                        scaleIOCLI.addVolume(protectionDomainName, storagePoolName, volume.getLabel(), volumeSize.toString(),
                                thinlyProvisioned);
                if (result.isSuccess()) {
                    ScaleIOCLIHelper.updateVolumeWithAddVolumeInfo(dbClient, volume, systemId, volumeSize, result);
                    poolsToUpdate.add(volume.getPool());
                    if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                        consistencyGroups.add(volume.getConsistencyGroup());
                    }
                    poolToVolumesMap.put(volume.getPool(), volume.getId().toString());
                } else {
                    anyFailures = true;
                    failedVolumeName = volume.getLabel();
                    errorMessage = result.errorString();
                    break;
                }
            }

            updateConsistencyGroupsWithStorageSystem(consistencyGroups, storage);

            List<StoragePool> pools = dbClient.queryObject(StoragePool.class, Lists.newArrayList(poolsToUpdate));
            for (StoragePool pool : pools) {
                pool.removeReservedCapacityForVolumes(poolToVolumesMap.get(pool.getId()));
                ScaleIOCLIHelper.updateStoragePoolCapacity(dbClient, scaleIOCLI, pool, storage);
            }

            if (anyFailures) {
                // There are failures, so for any remaining volumes for which
                // addVolume was not attempted, set their inActive status to true.
                for (int cleanup = index; cleanup <= count; cleanup++) {
                    volumes.get(cleanup - 1).setInactive(true);
                }
            }
            dbClient.persistObject(volumes);
            if (!anyFailures) {
                taskCompleter.ready(dbClient);
            } else {
                ServiceCoded code = DeviceControllerErrors.scaleio.createVolumeError(failedVolumeName, errorMessage);
                taskCompleter.error(dbClient, code);
            }
        } catch (IOException e) {
            log.error("Encountered an exception", e);
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.
                            encounteredAnExceptionFromScaleIOOperation("addVolume", e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void doCreateMetaVolume(StorageSystem storage, StoragePool storagePool, Volume volume,
            VirtualPoolCapabilityValuesWrapper capabilities, MetaVolumeRecommendation recommendation, VolumeCreateCompleter completer)
            throws DeviceControllerException {
        completeTaskAsUnsupported(completer);
    }

    @Override
    public void doCreateMetaVolumes(StorageSystem storage, StoragePool storagePool, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities, MetaVolumeRecommendation recommendation,
            TaskCompleter completer) throws DeviceControllerException
    {
        completeTaskAsUnsupported(completer);
    }

    @Override
    public void doExpandVolume(StorageSystem storage, StoragePool pool, Volume volume, Long size, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        Long volumeSize = size / ScaleIOCLIHelper.BYTES_IN_GB;
        ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(dbClient).getCLI(storage);
        ScaleIOModifyVolumeCapacityResult result = scaleIOCLI.modifyVolumeCapacity(volume.getNativeId(), volumeSize.toString());
        if (result.isSuccess()) {
            long newSize = Long.parseLong(result.getNewCapacity());
            volume.setProvisionedCapacity(size);
            volume.setAllocatedCapacity(newSize);
            volume.setCapacity(size);

            dbClient.persistObject(volume);
            ScaleIOCLIHelper.updateStoragePoolCapacity(dbClient, scaleIOCLI, pool, storage);
            pool.removeReservedCapacityForVolumes(Arrays.asList(volume.getId().toString()));
            taskCompleter.ready(dbClient);
        } else {
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.modifyCapacityFailed(volume.getNativeId(), result.getErrorString());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void doExpandAsMetaVolume(StorageSystem storageSystem, StoragePool storagePool, Volume volume, long size,
            MetaVolumeRecommendation recommendation,
            VolumeExpandCompleter volumeCompleter) throws DeviceControllerException {
        completeTaskAsUnsupported(volumeCompleter);
    }

    @Override
    public void doDeleteVolumes(StorageSystem storageSystem, String opId, List<Volume> volumes, TaskCompleter completer)
            throws DeviceControllerException {
        ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(dbClient).getCLI(storageSystem);
        boolean anyFailures = false;
        String failedVolumeName = null;
        String errorMessage = null;
        Set<URI> poolsToUpdate = new HashSet<>();

        for (Volume volume : volumes) {
            ScaleIORemoveVolumeResult result = scaleIOCLI.removeVolume(volume.getNativeId());
            if (result.isSuccess() || result.errorString().contains(VOLUME_NOT_FOUND)) {
                volume.setInactive(true);
                poolsToUpdate.add(volume.getPool());
                if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                    volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
            } else {
                anyFailures = true;
                failedVolumeName = volume.getLabel();
                errorMessage = result.errorString();
            }
        }
        dbClient.persistObject(volumes);

        List<StoragePool> pools = dbClient.queryObject(StoragePool.class, Lists.newArrayList(poolsToUpdate));
        for (StoragePool pool : pools) {
            ScaleIOCLIHelper.updateStoragePoolCapacity(dbClient, scaleIOCLI, pool, storageSystem);
        }

        if (!anyFailures) {
            completer.ready(dbClient);
        } else {
            ServiceCoded code = DeviceControllerErrors.scaleio.deleteVolumeError(failedVolumeName, errorMessage);
            completer.error(dbClient, code);
        }
    }

    @Override
    public void doExportGroupCreate(StorageSystem storage, ExportMask exportMask, Map<URI, Integer> volumeMap, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {
        filterInitiators(initiators);
        mapVolumes(storage, volumeMap, initiators, taskCompleter);
    }

    @Override
    public void doExportGroupDelete(StorageSystem storage, ExportMask exportMask, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        List<URI> volumeURIs = ExportMaskUtils.getVolumeURIs(exportMask);
        Set<Initiator> initiators =
                ExportMaskUtils.getInitiatorsForExportMask(dbClient, exportMask, null);
        filterInitiators(initiators);
        unmapVolumes(storage, volumeURIs, initiators, taskCompleter);
    }

    @Override
    public void doExportAddVolume(StorageSystem storage, ExportMask exportMask, URI volume, Integer lun, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        Map<URI, Integer> volumes = new HashMap<>();
        volumes.put(volume, lun);
        Set<Initiator> initiators =
                ExportMaskUtils.getInitiatorsForExportMask(dbClient, exportMask, null);
        filterInitiators(initiators);
        mapVolumes(storage, volumes, initiators, taskCompleter);
    }

    @Override
    public void doExportAddVolumes(StorageSystem storage, ExportMask exportMask, Map<URI, Integer> volumes, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        Set<Initiator> initiators =
                ExportMaskUtils.getInitiatorsForExportMask(dbClient, exportMask, null);
        filterInitiators(initiators);
        mapVolumes(storage, volumes, initiators, taskCompleter);
    }

    @Override
    public void doExportRemoveVolume(StorageSystem storage, ExportMask exportMask, URI volume, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        Set<Initiator> initiators =
                ExportMaskUtils.getInitiatorsForExportMask(dbClient, exportMask, null);
        filterInitiators(initiators);
        unmapVolumes(storage, asList(volume), initiators, taskCompleter);
    }

    @Override
    public void doExportRemoveVolumes(StorageSystem storage, ExportMask exportMask, List<URI> volumes, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        Set<Initiator> initiators =
                ExportMaskUtils.getInitiatorsForExportMask(dbClient, exportMask, null);
        filterInitiators(initiators);
        unmapVolumes(storage, volumes, initiators, taskCompleter);
    }

    @Override
    public void doExportAddInitiator(StorageSystem storage, ExportMask exportMask, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        Map<URI, Integer> volumes = createVolumeMapForExportMask(exportMask);
        mapVolumes(storage, volumes, asList(initiator), taskCompleter);
    }

    @Override
    public void doExportAddInitiators(StorageSystem storage, ExportMask exportMask, List<Initiator> initiators, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        Map<URI, Integer> volumes = createVolumeMapForExportMask(exportMask);
        mapVolumes(storage, volumes, initiators, taskCompleter);
    }

    @Override
    public void doExportRemoveInitiator(StorageSystem storage, ExportMask exportMask, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        List<URI> volumeURIs = ExportMaskUtils.getVolumeURIs(exportMask);
        unmapVolumes(storage, volumeURIs, asList(initiator), taskCompleter);
    }

    @Override
    public void doExportRemoveInitiators(StorageSystem storage, ExportMask exportMask, List<Initiator> initiators, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        List<URI> volumeURIs = ExportMaskUtils.getVolumeURIs(exportMask);
        unmapVolumes(storage, volumeURIs, initiators, taskCompleter);
    }

    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList, Boolean createInactive,
            Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, snapshotList);
            if (inReplicationGroup(dbClient, snapshots)) {
                snapshotOperations.createGroupSnapshots(storage, snapshotList, createInactive,
                		readOnly, taskCompleter);
            } else {
                URI snapshot = snapshots.get(0).getId();
                snapshotOperations.createSingleVolumeSnapshot(storage, snapshot, createInactive,
                		readOnly, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String.format("IO exception when trying to create snapshot(s) on array %s",
                    storage.getSerialNumber());
            log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doCreateSnapshot", e.getMessage());
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void doActivateSnapshot(StorageSystem storage, List<URI> snapshotList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);
            List<BlockSnapshot> groupSnapshots = ControllerUtils.getBlockSnapshotsBySnapsetLabelForProject(blockSnapshot, dbClient);

            // We check the snapset size here because SIO consistency groups require more than 1 device
            if (inReplicationGroup(dbClient, asList(blockSnapshot)) && groupSnapshots.size() > 1) {
                snapshotOperations.deleteGroupSnapshots(storage, snapshot, taskCompleter);
            } else {
                snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to delete snapshot(s) on array %s",
                    storage.getSerialNumber());
            log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doDeleteSnapshot",
                    e.getMessage());
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void doRestoreFromSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doCreateMirror(StorageSystem storage, URI mirror, Boolean createInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doFractureMirror(StorageSystem storage, URI mirror, Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doDetachMirror(StorageSystem storage, URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doResumeNativeContinuousCopy(StorageSystem storage, URI mirror, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doDeleteMirror(StorageSystem storage, URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doCreateClone(StorageSystem storageSystem, URI sourceVolume, URI cloneVolume, Boolean createInactive,
            TaskCompleter taskCompleter) {
        cloneOperations.createSingleClone(storageSystem, sourceVolume, cloneVolume, createInactive, taskCompleter);
    }

    @Override
    public void doDetachClone(StorageSystem storage, URI cloneVolume, TaskCompleter taskCompleter) {
        log.info("Nothing to do here.  ScaleIO full copies do not require detaching.");
        // no operation, set to ready
        Volume clone = dbClient.queryObject(Volume.class, cloneVolume);
        clone.setReplicaState(ReplicationState.DETACHED.name());
        dbClient.persistObject(clone);
        taskCompleter.ready(dbClient);
    }

    @Override
    public void doCreateConsistencyGroup(StorageSystem storage, URI consistencyGroup, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("Nothing to do here.  ScaleIO consistency groups are formed automatically on-demand.");
        taskCompleter.ready(dbClient);
    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage, URI consistencyGroup, Boolean markInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("Going to delete BlockConsistency Group {}", consistencyGroup);
        BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroup);
        if (cg != null) {
            dbClient.markForDeletion(cg);
        }
        taskCompleter.ready(dbClient);
    }

    @Override
    public void doConnect(StorageSystem storage) {

    }

    @Override
    public void doDisconnect(StorageSystem storage) {

    }

    @Override
    public String doAddStorageSystem(StorageSystem storage) throws DeviceControllerException {
        return null;
    }

    @Override
    public void doRemoveStorageSystem(StorageSystem storage) throws DeviceControllerException {

    }

    @Override
    public void doCopySnapshotsToTarget(StorageSystem storage, List<URI> snapshotList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts) {
        return null;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        return null;
    }

    @Override
    public void doActivateFullCopy(StorageSystem storageSystem, URI fullCopy, TaskCompleter completer) {
        completeTaskAsUnsupported(completer);
    }

    @Override
    public void doCleanupMetaMembers(StorageSystem storageSystem, Volume volume, CleanupMetaVolumeMembersCompleter cleanupCompleter)
            throws DeviceControllerException {
    }

    @Override
    public Integer checkSyncProgress(URI storage, URI source, URI target) {
        return null;
    }

    @Override
    public void doWaitForSynchronized(Class<? extends BlockObject> clazz, StorageSystem storageObj, URI target, TaskCompleter completer) {
        log.info("Nothing to do here.  ScaleIO does not require a wait for synchronization");
        completer.ready(dbClient);
    }

    @Override
    public void doWaitForGroupSynchronized(StorageSystem storageObj, List<URI> target, TaskCompleter completer)
    {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doAddToConsistencyGroup(StorageSystem storage, URI consistencyGroupId, List<URI> blockObjects, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doRemoveFromConsistencyGroup(StorageSystem storage, URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        return false;
    }

    /**
     * Method calls the completer with error message indicating that the caller's method is unsupported
     * 
     * @param completer [in] - TaskCompleter
     */
    private void completeTaskAsUnsupported(TaskCompleter completer) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String methodName = stackTrace[2].getMethodName();
        ServiceCoded code = DeviceControllerErrors.scaleio.operationIsUnsupported(methodName);
        completer.error(dbClient, code);
    }

    /**
     * Given a mapping of volumes and initiators, make the ScaleIO API calls to map the volume
     * to the specified ScaleIO initiators
     * 
     * @param storage [in] - StorageSystem object (ScaleIO array abstraction)
     * @param volumeMap [in] - Volume URI to Integer LUN map
     * @param initiators [in] - Collection of Initiator objects
     * @param completer [in] - TaskCompleter
     */
    private void mapVolumes(StorageSystem storage, Map<URI, Integer> volumeMap, Collection<Initiator> initiators,
            TaskCompleter completer) {
        ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(dbClient).getCLI(storage);
        for (Map.Entry<URI, Integer> volMapEntry : volumeMap.entrySet()) {
            BlockObject blockObject = BlockObject.fetch(dbClient, volMapEntry.getKey());
            String nativeId = blockObject.getNativeId();
            for (Initiator initiator : initiators) {
                String port = initiator.getInitiatorPort();
                boolean wasMapped = false;
                if (initiator.getProtocol().equals(HostInterface.Protocol.ScaleIO.name())) {
                    wasMapped = mapToSDC(scaleIOCLI, nativeId, port, completer);
                } else if (initiator.getProtocol().equals(HostInterface.Protocol.iSCSI.name())) {
                    wasMapped = mapToSCSI(scaleIOCLI, nativeId, port, completer);
                } else {
                    ServiceCoded code =
                            DeviceControllerErrors.scaleio.mapVolumeToClientFailed(nativeId, port,
                                    String.format("Unexpected initiator type %s", initiator.getProtocol()));
                    completer.error(dbClient, code);
                }
                if (!wasMapped) {
                    // Failed to map the volume
                    return;
                }
            }
        }
        completer.ready(dbClient);
    }

    private boolean mapToSDC(ScaleIOCLI scaleIOCLI, String volumeId, String sdcId, TaskCompleter completer) {
        ScaleIOMapVolumeToSDCResult mapVolumeToSDCResult = scaleIOCLI.mapVolumeToSDC(volumeId, sdcId);
        if (!mapVolumeToSDCResult.isSuccess() &&
                !mapVolumeToSDCResult.getErrorString().contains(ALREADY_MAPPED_TO)) {
            String errorString = mapVolumeToSDCResult.getErrorString();
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.mapVolumeToClientFailed(volumeId, sdcId, errorString);
            completer.error(dbClient, code);
            return false;
        }
        return true;
    }

    private boolean mapToSCSI(ScaleIOCLI scaleIOCLI, String volumeId, String iqn, TaskCompleter completer) {
        ScaleIOMapVolumeToSCSIInitiatorResult mapVolumeToSDCResult = scaleIOCLI.mapVolumeToSCSIInitiator(volumeId, iqn);
        if (!mapVolumeToSDCResult.isSuccess() &&
                !mapVolumeToSDCResult.getErrorString().contains(ALREADY_MAPPED_TO)) {
            String errorString = mapVolumeToSDCResult.getErrorString();
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.mapVolumeToClientFailed(volumeId, iqn, errorString);
            completer.error(dbClient, code);
            return false;
        }
        return true;
    }

    /**
     * Given a mapping of volumes and initiators, make the ScaleIO API calls to un-map the volume
     * to the specified ScaleIO initiators
     * 
     * @param storage [in] - StorageSystem object (ScaleIO array abstraction)
     * @param volumeURIs [in] - Collection of Volume URIs
     * @param initiators [in] - Collection of Initiator objects
     * @param completer [in] - TaskCompleter
     */
    private void unmapVolumes(StorageSystem storage, Collection<URI> volumeURIs, Collection<Initiator> initiators,
            TaskCompleter completer) {
        ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(dbClient).getCLI(storage);
        for (URI volumeURI : volumeURIs) {
            BlockObject blockObject = BlockObject.fetch(dbClient, volumeURI);
            if (blockObject == null || blockObject.getInactive()) {
                log.warn(String.format("Attempted to unmap BlockObject %s, which was either not found in the DB or was inactive",
                        volumeURI.toString()));
                continue;
            }
            String nativeId = blockObject.getNativeId();
            for (Initiator initiator : initiators) {
                String port = initiator.getInitiatorPort();
                boolean wasUnMapped = false;
                if (initiator.getProtocol().equals(HostInterface.Protocol.ScaleIO.name())) {
                    wasUnMapped = unmapFromSDC(scaleIOCLI, nativeId, port, completer);
                } else if (initiator.getProtocol().equals(HostInterface.Protocol.iSCSI.name())) {
                    wasUnMapped = unmapFromSCSI(scaleIOCLI, nativeId, port, completer);
                } else {
                    ServiceCoded code =
                            DeviceControllerErrors.scaleio.unmapVolumeToClientFailed(nativeId, port,
                                    String.format("Unexpected initiator type %s", initiator.getProtocol()));
                    completer.error(dbClient, code);
                }
                if (!wasUnMapped) {
                    // Failed to map the volume
                    return;
                }
            }
        }
        completer.ready(dbClient);
    }

    private boolean unmapFromSDC(ScaleIOCLI scaleIOCLI, String volumeId, String sdcId, TaskCompleter completer) {
        ScaleIOUnMapVolumeToSDCResult unMapVolumeToSDCResult = scaleIOCLI.unMapVolumeToSDC(volumeId, sdcId);
        if (!unMapVolumeToSDCResult.isSuccess() &&
                !unMapVolumeToSDCResult.getErrorString().contains(VOLUME_NOT_MAPPED_TO_SDC)) {
            String errorString = unMapVolumeToSDCResult.getErrorString();
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.unmapVolumeToClientFailed(volumeId, sdcId, errorString);
            completer.error(dbClient, code);
            return false;
        }
        return true;
    }

    private boolean unmapFromSCSI(ScaleIOCLI scaleIOCLI, String volumeId, String sdcId, TaskCompleter completer) {
        ScaleIOUnMapVolumeFromSCSIInitiatorResult unMapVolumeToSDCResult =
                scaleIOCLI.unMapVolumeFromSCSIInitiator(volumeId, sdcId);
        if (!unMapVolumeToSDCResult.isSuccess() &&
                !unMapVolumeToSDCResult.getErrorString().contains(VOLUME_NOT_MAPPED_TO_SCSI)) {
            String errorString = unMapVolumeToSDCResult.getErrorString();
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.unmapVolumeToClientFailed(volumeId, sdcId, errorString);
            completer.error(dbClient, code);
            return false;
        }
        return true;
    }

    /**
     * Given a collection of Initiators, go through and filter out any initiators
     * that are not ScaleIO or IP types. The passed in Collection will be modified.
     * 
     * @param initiators [in/out] - Collection of Initiator objects
     */
    private void filterInitiators(Collection<Initiator> initiators) {
        Iterator<Initiator> initiatorIterator = initiators.iterator();
        while (initiatorIterator.hasNext()) {
            Initiator initiator = initiatorIterator.next();
            if (!initiator.getProtocol().equals(Initiator.Protocol.ScaleIO.name()) &&
                    !initiator.getProtocol().equals(Initiator.Protocol.iSCSI.name())) {
                initiatorIterator.remove();
            }
        }
    }

    /**
     * Using the ExportMask object, create a volume URI to HLU map. For ScaleIO,
     * there isn't any HLU required
     * 
     * @param exportMask [in] - ExportMask object
     * @return Volume URI to HLU integer value (allows ExportGroup.LUN_UNASSIGNED)
     */
    private Map<URI, Integer> createVolumeMapForExportMask(ExportMask exportMask) {
        Map<URI, Integer> map = new HashMap<>();
        for (URI uri : ExportMaskUtils.getVolumeURIs(exportMask)) {
            map.put(uri, ExportGroup.LUN_UNASSIGNED);
        }
        return map;
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

    private String getScaleIOCustomerId(ScaleIOCLI scaleIOCLI) {
        ScaleIOQueryAllResult scaleIOQueryAllResult = scaleIOCLI.queryAll();
        return scaleIOQueryAllResult.getProperty(ScaleIOQueryAllCommand.SCALEIO_CUSTOMER_ID);
    }

    @Override
    public void doFractureClone(StorageSystem storageDevice, URI source, URI clone,
            TaskCompleter completer) {
        completeTaskAsUnsupported(completer);
    }

    @Override
    public void doRestoreFromClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doResyncClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doCreateGroupClone(StorageSystem storageDevice, List<URI> clones,
            Boolean createInactive, TaskCompleter completer) {
        completeTaskAsUnsupported(completer);
    }

    @Override
    public void doDetachGroupClone(StorageSystem storage, List<URI> cloneVolume,
            TaskCompleter taskCompleter) {
        completeTaskAsUnsupported(taskCompleter);

    }

    @Override
    public void doRestoreFromGroupClone(StorageSystem storageSystem, List<URI> cloneVolume,
            TaskCompleter taskCompleter) {
        completeTaskAsUnsupported(taskCompleter);

    }

    @Override
    public void doActivateGroupFullCopy(StorageSystem storageSystem,
            List<URI> fullCopy, TaskCompleter completer) {
        completeTaskAsUnsupported(completer);

    }

    @Override
    public void doResyncGroupClone(StorageSystem storageDevice,
            List<URI> clone, TaskCompleter completer) throws Exception {
        completeTaskAsUnsupported(completer);

    }
}
