/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.cinder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.cinder.api.CinderApiFactory;
import com.emc.storageos.cinder.errorhandling.CinderException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MultiVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.cinder.job.CinderDeleteVolumeJob;
import com.emc.storageos.volumecontroller.impl.cinder.job.CinderJob;
import com.emc.storageos.volumecontroller.impl.cinder.job.CinderMultiVolumeCreateJob;
import com.emc.storageos.volumecontroller.impl.cinder.job.CinderSingleVolumeCreateJob;
import com.emc.storageos.volumecontroller.impl.cinder.job.CinderSnapshotCreateJob;
import com.emc.storageos.volumecontroller.impl.cinder.job.CinderSnapshotDeleteJob;
import com.emc.storageos.volumecontroller.impl.cinder.job.CinderVolumeExpandJob;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * OpenStack Cinder specific provisioning implementation class.
 * This class is responsible to do all provisioning operations by interacting with Cinder REST API
 * 
 */
public class CinderStorageDevice extends DefaultBlockStorageDevice {

    private static final Logger log = LoggerFactory.getLogger(CinderStorageDevice.class);
    private DbClient dbClient;
    private CinderApiFactory cinderApiFactory;
    private ExportMaskOperations _exportMaskOperationsHelper;
    private CloneOperations cloneOperations;
    private NameGenerator nameGenerator;
    private final long SNAPSHOT_DELETE_STATUS_CHECK_SLEEP_TIME = 10 * 1000; // 10 seconds

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * @param cinderApiFactory the CinderApiFactory to set
     */
    public void setCinderApiFactory(CinderApiFactory cinderApiFactory)
    {
        this.cinderApiFactory = cinderApiFactory;
    }

    public void setExportMaskOperationsHelper(ExportMaskOperations exportMaskOperationsHelper) {
        _exportMaskOperationsHelper = exportMaskOperationsHelper;
    }

    public void setCloneOperations(final CloneOperations cloneOperations)
    {
        this.cloneOperations = cloneOperations;
    }

    public void setNameGenerator(final NameGenerator nameGenerator)
    {
        this.nameGenerator = nameGenerator;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doCreateVolumes
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.StoragePool,
     * java.lang.String, java.util.List,
     * com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doCreateVolumes(StorageSystem storageSystem, StoragePool storagePool,
            String opId, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities,
            TaskCompleter taskCompleter) throws DeviceControllerException
    {

        String label = null;
        Long capacity = null;
        boolean opCreationFailed = false;
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create Volume Start - Array:%s, Pool:%s",
                storageSystem.getSerialNumber(),
                storagePool.getNativeGuid()));

        for (Volume volume : volumes)
        {
            logMsgBuilder
                    .append(String.format("%nVolume:%s", volume.getLabel()));
            String tenantName = "";
            try
            {
                TenantOrg tenant = dbClient.queryObject(TenantOrg.class, volume.getTenant().getURI());
                tenantName = tenant.getLabel();
            } catch (DatabaseException e)
            {
                log.error("Error lookup TenantOrg object", e);
            }

            label = nameGenerator.generate(tenantName, volume.getLabel(),
                    volume.getId().toString(), CinderConstants.CHAR_HYPHEN,
                    SmisConstants.MAX_VOLUME_NAME_LENGTH);

            if (capacity == null)
            {
                capacity = volume.getCapacity();
            }
        }
        log.info(logMsgBuilder.toString());

        try
        {

            CinderEndPointInfo ep = CinderUtils.getCinderEndPoint(storageSystem.getActiveProviderURI(), dbClient);

            log.info("Getting the cinder APi for the provider with id {}", storageSystem.getActiveProviderURI());
            CinderApi cinderApi = cinderApiFactory.getApi(storageSystem.getActiveProviderURI(), ep);

            String volumeId = null;
            Map<String, URI> volumeIds = new HashMap<String, URI>();
            if (volumes.size() == 1)
            {
                volumeId = cinderApi.createVolume(label, (capacity / CinderConstants.BYTES_TO_GB), storagePool.getNativeId());
                volumeIds.put(volumeId, volumes.get(0).getId());
                log.debug("Creating volume with the id {} on Openstack cinder node", volumeId);
            }
            else
            {
                log.debug("Starting to create {} volumes", volumes.size());
                for (int volumeIndex = 0; volumeIndex < volumes.size(); volumeIndex++)
                {
                    volumeId = cinderApi.createVolume(label + CinderConstants.HYPHEN + (volumeIndex + 1),
                            (capacity / CinderConstants.BYTES_TO_GB), storagePool.getNativeId());
                    volumeIds.put(volumeId, volumes.get(volumeIndex).getId());
                    log.debug("Creating volume with the id {} on Openstack cinder node", volumeId);
                }
            }

            if (!volumeIds.isEmpty())
            {
                CinderJob createVolumeJob = (volumes.size() > 1) ?
                        new CinderMultiVolumeCreateJob(volumeId, label, volumes.get(0).getStorageController(),
                                CinderConstants.ComponentType.volume.name(), ep, taskCompleter, storagePool.getId(), volumeIds) :
                        new CinderSingleVolumeCreateJob(volumeId, label, volumes.get(0).getStorageController(),
                                CinderConstants.ComponentType.volume.name(), ep, taskCompleter, storagePool.getId(), volumeIds);
                ControllerServiceImpl.enqueueJob(new QueueJob(createVolumeJob));
            }

        } catch (final InternalException e)
        {
            log.error("Problem in doCreateVolumes: ", e);
            opCreationFailed = true;
            taskCompleter.error(dbClient, e);
        } catch (final Exception e)
        {
            log.error("Problem in doCreateVolumes: ", e);
            opCreationFailed = true;
            ServiceError serviceError = DeviceControllerErrors.cinder.operationFailed("doCreateVolumes", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }

        if (opCreationFailed)
        {
            for (Volume vol : volumes)
            {
                vol.setInactive(true);
                dbClient.persistObject(vol);
            }
        }

        logMsgBuilder = new StringBuilder(String.format(
                "Create Volumes End - Array:%s, Pool:%s",
                storageSystem.getSerialNumber(),
                storagePool.getNativeGuid()));
        for (Volume volume : volumes)
        {
            logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
        }
        log.info(logMsgBuilder.toString());

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExpandVolume
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.StoragePool,
     * com.emc.storageos.db.client.model.Volume,
     * java.lang.Long,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExpandVolume(StorageSystem storageSystem, StoragePool storagePool,
            Volume volume, Long size, TaskCompleter taskCompleter)
            throws DeviceControllerException
    {

        try {
            log.info(String.format("Expand Volume Start - Array:%s, Pool:%s, Volume:%s",
                    storageSystem.getSerialNumber(), storagePool.getNativeGuid(), volume.getId()));

            CinderEndPointInfo ep = CinderUtils.getCinderEndPoint(storageSystem.getActiveProviderURI(), dbClient);

            log.info("Getting the cinder APi for the provider with id " + storageSystem.getActiveProviderURI());
            CinderApi cinderApi = cinderApiFactory.getApi(storageSystem.getActiveProviderURI(), ep);

            String volumeId = volume.getNativeId();
            if (null != volumeId) {
                log.info("Expanding volume with the id " + volumeId + " on Openstack cinder node");
                cinderApi.expandVolume(volumeId, (size / CinderConstants.BYTES_TO_GB));

                CinderJob expandVolumeJob = new CinderVolumeExpandJob(volumeId, volume.getLabel(),
                        volume.getStorageController(), CinderConstants.ComponentType.volume.name(),
                        ep, taskCompleter, storagePool.getId());
                ControllerServiceImpl.enqueueJob(new QueueJob(expandVolumeJob));
            }

            log.info(String.format("Expand Volume End - Array:%s, Pool:%s, Volume:%s",
                    storageSystem.getSerialNumber(), storagePool.getNativeGuid(), volume.getId()));

        } catch (CinderException ce) {
            String message = String.format(
                    "Exception when trying to expand volume on Array %s, Pool:%s, Volume:%s",
                    storageSystem.getSerialNumber(), storagePool.getNativeGuid(), volume.getId());
            log.error(message, ce);
            ServiceError error = DeviceControllerErrors.cinder.operationFailed("doExpandVolume",
                    ce.getMessage());
            taskCompleter.error(dbClient, error);
        } catch (final Exception e) {
            log.error("Problem in doExpandVolume: ", e);
            ServiceError serviceError = DeviceControllerErrors.cinder.operationFailed("doExpandVolume", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doDeleteVolumes
     * (com.emc.storageos.db.client.model.StorageSystem,
     * java.lang.String,
     * java.util.List,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doDeleteVolumes(StorageSystem storageSystem, String opId,
            List<Volume> volumes, TaskCompleter taskCompleter) throws DeviceControllerException
    {
        try
        {
            List<String> volumeNativeIdsToDelete = new ArrayList<String>(volumes.size());
            List<String> volumeLabels = new ArrayList<String>(volumes.size());

            StringBuilder logMsgBuilder = new StringBuilder(
                    String.format("Delete Volume Start - Array:%s", storageSystem.getSerialNumber()));
            log.info(logMsgBuilder.toString());

            MultiVolumeTaskCompleter multiVolumeTaskCompleter = (MultiVolumeTaskCompleter) taskCompleter;

            CinderEndPointInfo ep = CinderUtils.getCinderEndPoint(storageSystem.getActiveProviderURI(), dbClient);

            log.info("Getting the cinder APi for the provider with id " + storageSystem.getActiveProviderURI());
            CinderApi cinderApi = cinderApiFactory.getApi(storageSystem.getActiveProviderURI(), ep);

            for (Volume volume : volumes)
            {
                logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));

                try
                {
                    // Check if the volume is present on the back-end device
                    cinderApi.showVolume(volume.getNativeId());
                } catch (CinderException ce)
                {
                    // This means, the volume is not present on the back-end device
                    log.info(String.format("Volume %s already deleted: ", volume.getNativeId()));
                    volume.setInactive(true);
                    dbClient.persistObject(volume);
                    VolumeTaskCompleter deleteTaskCompleter = multiVolumeTaskCompleter.skipTaskCompleter(volume.getId());
                    deleteTaskCompleter.ready(dbClient);
                    continue;
                }

                volumeNativeIdsToDelete.add(volume.getNativeId());
                volumeLabels.add(volume.getLabel());

                // cleanup if there are any snapshots created for a volume
                cleanupAnyBackupSnapshots(volume, cinderApi);
            }

            // Now - trigger the delete
            if (!multiVolumeTaskCompleter.isVolumeTaskCompletersEmpty())
            {
                cinderApi.deleteVolumes(volumeNativeIdsToDelete.toArray(new String[] {}));
                ControllerServiceImpl.enqueueJob(new QueueJob(
                        new CinderDeleteVolumeJob(volumeNativeIdsToDelete.get(0), volumeLabels.get(0),
                                volumes.get(0).getStorageController(), CinderConstants.ComponentType.volume.name(), ep, taskCompleter)));
            }
            else
            {
                // If we are here, there are no volumes to delete, we have
                // invoked ready() for the VolumeDeleteCompleter, and told
                // the multiVolumeTaskCompleter to skip these completers.
                // In this case, the multiVolumeTaskCompleter complete()
                // method will not be invoked and the result is that the
                // workflow that initiated this delete request will never
                // be updated. So, here we just call complete() on the
                // multiVolumeTaskCompleter to ensure the workflow status is
                // updated.
                multiVolumeTaskCompleter.ready(dbClient);
            }
        } catch (Exception e)
        {
            log.error("Problem in doDeleteVolume: ", e);
            ServiceError error = DeviceControllerErrors.cinder.operationFailed("doDeleteVolume", e.getMessage());
            taskCompleter.error(dbClient, error);
        }

        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Delete Volume End - Array: %s", storageSystem.getSerialNumber()));

        for (Volume volume : volumes)
        {
            logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
        }
        log.info(logMsgBuilder.toString());

    }

    /**
     * Removes all volume snapshots
     * 
     * @param volume
     * @param cinderApi
     * @throws Exception
     */
    private void cleanupAnyBackupSnapshots(Volume volume, CinderApi cinderApi) throws Exception
    {
        // Get all snapshots of a volume
        List<String> snapshotIdsList = cinderApi.listSnapshotsForVolume(volume.getNativeId());
        String snapIdToRemove = "";

        if (!snapshotIdsList.isEmpty())
        {
            // Invoke Delete for all volume snapshots
            for (String snapId : snapshotIdsList)
            {
                cinderApi.deleteSnapshot(snapId);
            }

            // Check the deletion status before returning to the caller.
            // Return only after snapshots are deleted.
            boolean isWait = true;
            while (true)
            {

                if (isWait)
                {
                    // Wait for 10 seconds before checking the status as
                    // deletion will take some time to complete.
                    try
                    {
                        Thread.sleep(SNAPSHOT_DELETE_STATUS_CHECK_SLEEP_TIME);
                    } catch (InterruptedException e)
                    {
                        log.error("Snapshot deletion check waiting thread interrupted", e);
                    }
                }

                for (String snapId : snapshotIdsList)
                {
                    try
                    {
                        cinderApi.getTaskStatus(snapId, CinderConstants.ComponentType.snapshot.name());
                        isWait = true;
                    } catch (CinderException e)
                    {
                        // This means the snapshot was deleted, remove it from
                        // the status check list
                        snapIdToRemove = snapId;
                        isWait = false; // If one of the snapshot in the list is deleted, its possible that others are also deleted
                                        // so dont wait in the next cycle.
                        break; // break the for loop
                    }
                }

                // remove the snapId which is deleted from the backend
                if (!snapIdToRemove.isEmpty())
                {
                    snapshotIdsList.remove(snapIdToRemove);
                    snapIdToRemove = ""; // reset
                }

                // break the while loop on list empty
                if (snapshotIdsList.isEmpty())
                {
                    break;
                }

            }
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportGroupCreate
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * java.util.Map, java.util.List,
     * java.util.List,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportGroupCreate(StorageSystem storage,
            ExportMask exportMask, Map<URI, Integer> volumeMap,
            List<Initiator> initiators, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportGroupCreate START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(storage.getSystemType(), volumeMap, dbClient);
        _exportMaskOperationsHelper.createExportMask(storage, exportMask.getId(), volumeLunArray, targets, initiators, taskCompleter);
        log.info("{} doExportGroupCreate END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportGroupDelete(
     * com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportGroupDelete(StorageSystem storage,
            ExportMask exportMask, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportGroupDelete START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.deleteExportMask(storage, exportMask.getId(), new ArrayList<URI>(),
                new ArrayList<URI>(), new ArrayList<Initiator>(), taskCompleter);
        log.info("{} doExportGroupDelete END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportAddVolume
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * java.net.URI, java.lang.Integer,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportAddVolume(StorageSystem storage, ExportMask exportMask,
            URI volume, Integer lun, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        map.put(volume, lun);
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(storage.getSystemType(), map, dbClient);
        _exportMaskOperationsHelper.addVolume(storage, exportMask.getId(), volumeLunArray, taskCompleter);
        log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportAddVolumes
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * java.util.Map, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportAddVolumes(StorageSystem storage,
            ExportMask exportMask, Map<URI, Integer> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(storage.getSystemType(), volumes, dbClient);
        _exportMaskOperationsHelper.addVolume(storage, exportMask.getId(),
                volumeLunArray, taskCompleter);
        log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportRemoveVolume
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportRemoveVolume(StorageSystem storage,
            ExportMask exportMask, URI volume, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(), Arrays.asList(volume), taskCompleter);
        log.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportRemoveVolumes
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * java.util.List, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportRemoveVolumes(StorageSystem storage,
            ExportMask exportMask, List<URI> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(), volumes,
                taskCompleter);
        log.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportAddInitiator
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * com.emc.storageos.db.client.model.Initiator,
     * java.util.List, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportAddInitiator(StorageSystem storage,
            ExportMask exportMask, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(), Arrays.asList(initiator), targets, taskCompleter);
        log.info("{} doExportAddInitiator END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportAddInitiators
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * java.util.List,
     * java.util.List,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportAddInitiators(StorageSystem storage,
            ExportMask exportMask, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(), initiators,
                targets, taskCompleter);
        log.info("{} doExportAddInitiator END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportRemoveInitiator
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * com.emc.storageos.db.client.model.Initiator,
     * java.util.List, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportRemoveInitiator(StorageSystem storage,
            ExportMask exportMask, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(),
                Arrays.asList(initiator), targets, taskCompleter);
        log.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportRemoveInitiators
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask,
     * java.util.List, java.util.List,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportRemoveInitiators(StorageSystem storage,
            ExportMask exportMask, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(),
                initiators, targets, taskCompleter);
        log.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doCreateSnapshot
     * (com.emc.storageos.db.client.model.StorageSystem,
     * java.util.List,
     * java.lang.Boolean,
     * java.lang.Boolean,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
            throws DeviceControllerException {

        log.debug("In CinderStorageDevice.doCreateSnapshot method.");
        boolean operationFailed = false;
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create Snapshot Start - Array:%s, ",
                storage.getSerialNumber()));

        BlockSnapshot snapshot = null;
        try {
            snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotList.get(0));
            Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());

            logMsgBuilder.append(String.format("%nSnapshot:%s for Volume %s",
                    snapshot.getLabel(), volume.getLabel()));

            log.info(logMsgBuilder.toString());

            CinderEndPointInfo endPoint = CinderUtils.getCinderEndPoint(storage.getActiveProviderURI(), dbClient);
            CinderApi cinderApi = cinderApiFactory.getApi(storage.getActiveProviderURI(), endPoint);

            String snapshotID = cinderApi.createSnapshot(volume.getNativeId(), snapshot.getLabel());

            if (snapshotID != null) {
                CinderJob createSnapshotJob = new CinderSnapshotCreateJob(snapshotID, snapshot.getLabel(),
                        volume.getStorageController(), CinderConstants.ComponentType.snapshot.name(), endPoint, taskCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(createSnapshotJob));
            }

        } catch (Exception e) {
            String message = String.format(
                    "Exception when trying to create snapshot(s) on array %s",
                    storage.getSerialNumber());
            log.error(message, e);
            ServiceError error = DeviceControllerErrors.cinder.operationFailed("doCreateSnapshot",
                    e.getMessage());
            taskCompleter.error(dbClient, error);
            operationFailed = true;
        }

        if (operationFailed && null != snapshot) {
            snapshot.setInactive(true);
            dbClient.persistObject(snapshot);
        }

        logMsgBuilder = new StringBuilder(String.format(
                "Create Snapshot End - Array:%s, ",
                storage.getSerialNumber()));
        log.info(logMsgBuilder.toString());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doDeleteSnapshot
     * (com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doDeleteSnapshot(StorageSystem storageSystem, URI snapshotURI,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Delete Snapshot Start - Array:%s", storageSystem.getSerialNumber()));
            log.info(logMsgBuilder.toString());

            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotURI);

            CinderEndPointInfo ep = CinderUtils.getCinderEndPoint(storageSystem.getActiveProviderURI(), dbClient);
            log.info("Getting the cinder APi for the provider with id " + storageSystem.getActiveProviderURI());
            CinderApi cinderApi = cinderApiFactory.getApi(storageSystem.getActiveProviderURI(), ep);

            try {
                cinderApi.showSnapshot(snapshot.getId().toString());
            } catch (CinderException ce) {
                // This means, the snapshot is not present on the back-end device
                log.info(String.format("Snapshot %s already deleted: ", snapshot.getNativeId()));
                snapshot.setInactive(true);
                dbClient.persistObject(snapshot);
            }

            // Now - trigger the delete
            cinderApi.deleteSnapshot(snapshot.getNativeId().toString());
            ControllerServiceImpl.enqueueJob(new QueueJob(
                    new CinderSnapshotDeleteJob(snapshot.getNativeId(), snapshot.getLabel(),
                            storageSystem.getId(), CinderConstants.ComponentType.snapshot.name(), ep, taskCompleter)));

        } catch (Exception e) {
            log.error("Problem in doDeleteVolume: ", e);
            ServiceError error = DeviceControllerErrors.cinder.operationFailed(
                    "doDeleteSnapshot", e.getMessage());
            taskCompleter.error(dbClient, error);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doCreateClone
     * (com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI,
     * java.net.URI,
     * java.lang.Boolean,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doCreateClone(StorageSystem storageSystem, URI sourceVolume,
            URI cloneVolume, Boolean createInactive,
            TaskCompleter taskCompleter)
    {

        this.cloneOperations.createSingleClone(storageSystem, sourceVolume,
                cloneVolume, createInactive,
                taskCompleter);

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doDetachClone
     * (com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doDetachClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter)
    {
        this.cloneOperations.detachSingleClone(storage, cloneVolume, taskCompleter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#findExportMasks
     * (com.emc.storageos.db.client.model.StorageSystem, java.util.List, boolean)
     */
    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) {
        return _exportMaskOperationsHelper.findExportMasks(storage, initiatorNames, mustHaveAllPorts);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#refreshExportMask
     * (com.emc.storageos.db.client.model.StorageSystem, com.emc.storageos.db.client.model.ExportMask)
     */
    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        return _exportMaskOperationsHelper.refreshExportMask(storage, mask);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doActivateFullCopy
     * (com.emc.storageos.db.client.model.StorageSystem, java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doActivateFullCopy(StorageSystem storageSystem, URI fullCopy,
            TaskCompleter completer)
    {
        this.cloneOperations.activateSingleClone(storageSystem, fullCopy, completer);
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        return true;
    }
    
    public void doWaitForSynchronized(Class<? extends BlockObject> clazz,
            StorageSystem storageObj, URI target, TaskCompleter completer) {
        // Do nothing - cinder API does not have API to synchronize copies
        // This has to do nothing for VPLEX+Cinder case to pass for the
        // volume clone.
        log.info("Nothing to do here.  Cinder does not require a wait for synchronization");
        completer.ready(dbClient);
    }
    
    @Override
    public void doCreateConsistencyGroup(StorageSystem storage,
            URI consistencyGroup, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doCreateConsistencyGroup START ...", storage.getSerialNumber());
        taskCompleter.ready(dbClient);
        log.info("{} doCreateConsistencyGroup END ...", storage.getSerialNumber());
    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage,
            URI consistencyGroup, Boolean markInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doDeleteConsistencyGroup START ...", storage.getSerialNumber());
        taskCompleter.ready(dbClient);
        log.info("{} doDeleteConsistencyGroup START ...", storage.getSerialNumber());
        
    }
    
    @Override
    public void doAddToConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doAddToConsistencyGroup START ...", storage.getSerialNumber());
        taskCompleter.ready(dbClient);
        log.info("{} doAddToConsistencyGroup END ...", storage.getSerialNumber());
    }

    @Override
    public void doRemoveFromConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doRemoveFromConsistencyGroup START ...", storage.getSerialNumber());
        taskCompleter.ready(dbClient);
        log.info("{} doRemoveFromConsistencyGroup END ...", storage.getSerialNumber());
    }

}
