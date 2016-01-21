/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy.HitachiTieringPolicy;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.api.HDSApiProtectionManager;
import com.emc.storageos.hds.model.LDEV;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.hds.model.ReplicationInfo;
import com.emc.storageos.hds.model.StorageArray;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.MetaVolumeOperations;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MultiVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSCleanupMetaVolumeMembersJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSCreateMultiVolumeJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSCreateVolumeJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSDeleteVolumeJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSModifyVolumeJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSReplicationSyncJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSReplicationSyncJob.ReplicationStatus;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSVolumeExpandJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.smis.MirrorOperations;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.workflow.WorkflowService;

/**
 * Hitachi Data System specific provisioning implementation class.
 * This class is responsible to do all provisioning operations by interacting with XML API Server which is running on
 * HiCommand Device Manager.
 * 
 */
public class HDSStorageDevice extends DefaultBlockStorageDevice {

    private static final Logger log = LoggerFactory.getLogger(HDSStorageDevice.class);

    private DbClient dbClient;

    private NameGenerator nameGenerator;

    private HDSApiFactory hdsApiFactory;

    private ExportMaskOperations exportMaskOperationsHelper;

    private MetaVolumeOperations metaVolumeOperations;

    private CloneOperations cloneOperations;

    private MirrorOperations mirrorOperations;

    private SnapshotOperations snapshotOperations;

    private static String QUICK_FORMAT_TYPE = "quick";

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    /**
     * @param hdsApiFactory the hdsApiFactory to set
     */
    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    public void setExportMaskOperationsHelper(ExportMaskOperations exportMaskOperationsHelper) {
        this.exportMaskOperationsHelper = exportMaskOperationsHelper;
    }

    public void setMetaVolumeOperations(final MetaVolumeOperations metaVolumeOperations) {
        this.metaVolumeOperations = metaVolumeOperations;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doCreateVolumes(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.StoragePool, java.lang.String, java.util.List,
     * com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doCreateVolumes(StorageSystem storageSystem, StoragePool storagePool,
            String opId, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        String label = null;
        Long capacity = null;
        boolean isThinVolume = false;
        boolean opCreationFailed = false;
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create Volume Start - Array:%s, Pool:%s", storageSystem.getSerialNumber(),
                storagePool.getNativeGuid()));
        for (Volume volume : volumes) {
            logMsgBuilder.append(String.format("%nVolume:%s , IsThinlyProvisioned: %s",
                    volume.getLabel(), volume.getThinlyProvisioned()));

            if ((label == null) && (volumes.size() == 1)) {
                String tenantName = "";
                try {
                    TenantOrg tenant = dbClient.queryObject(TenantOrg.class, volume.getTenant().getURI());
                    tenantName = tenant.getLabel();
                } catch (DatabaseException e) {
                    log.error("Error lookup TenantOrb object", e);
                }
                label = nameGenerator.generate(tenantName, volume.getLabel(), volume.getId().toString(),
                        '-', HDSConstants.MAX_VOLUME_NAME_LENGTH);
            }

            if (capacity == null) {
                capacity = volume.getCapacity();
            }
            isThinVolume = volume.getThinlyProvisioned();
        }
        log.info(logMsgBuilder.toString());
        try {
            multiVolumeCheckForHitachiModel(volumes, storageSystem);

            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
            String systemObjectID = HDSUtils.getSystemObjectID(storageSystem);
            String poolObjectID = HDSUtils.getPoolObjectID(storagePool);
            String asyncTaskMessageId = null;

            // isThinVolume = true, creates VirtualVolumes
            // isThinVolume = false, creates LogicalUnits
            if (isThinVolume) {
                asyncTaskMessageId = hdsApiClient.createThinVolumes(systemObjectID,
                        storagePool.getNativeId(), capacity, volumes.size(), label,
                        QUICK_FORMAT_TYPE, storageSystem.getModel());
            } else if (!isThinVolume) {
                asyncTaskMessageId = hdsApiClient.createThickVolumes(systemObjectID,
                        poolObjectID, capacity, volumes.size(), label, null, storageSystem.getModel(), null);
            }

            if (asyncTaskMessageId != null) {
                HDSJob createHDSJob = (volumes.size() > 1) ? new HDSCreateMultiVolumeJob(
                        asyncTaskMessageId, volumes.get(0).getStorageController(), storagePool.getId(),
                        volumes.size(), taskCompleter) : new HDSCreateVolumeJob(
                        asyncTaskMessageId, volumes.get(0).getStorageController(), storagePool.getId(),
                        taskCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(createHDSJob));
            }
        } catch (final InternalException e) {
            log.error("Problem in doCreateVolumes: ", e);
            opCreationFailed = true;
            taskCompleter.error(dbClient, e);
        } catch (final Exception e) {
            log.error("Problem in doCreateVolumes: ", e);
            opCreationFailed = true;
            ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("doCreateVolumes", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        if (opCreationFailed) {
            for (Volume vol : volumes) {
                vol.setInactive(true);
                dbClient.persistObject(vol);
            }
        }

        logMsgBuilder = new StringBuilder(String.format(
                "Create Volumes End - Array:%s, Pool:%s", storageSystem.getSerialNumber(),
                storagePool.getNativeGuid()));
        for (Volume volume : volumes) {
            logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
        }
        log.info(logMsgBuilder.toString());
    }

    /**
     * Few Hitachi models doesn't support multivolume creation in single request.
     * We shouldn't allow user to use bulk volume creation.
     * 
     * @param volumes
     * @param storageSystem
     */
    private void multiVolumeCheckForHitachiModel(List<Volume> volumes,
            StorageSystem storageSystem) throws HDSException {
        if (volumes.size() > 1
                && HDSUtils.checkForAMSSeries(storageSystem)) {
            throw HDSException.exceptions.unsupportedOperationOnThisModel();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExpandVolume(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.StoragePool, com.emc.storageos.db.client.model.Volume, java.lang.Long,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExpandVolume(StorageSystem storageSystem, StoragePool storagePool, Volume volume,
            Long size, TaskCompleter taskCompleter) throws DeviceControllerException {

        log.info(String.format(
                "Expand Volume Start - Array: %s, Pool: %s, Volume: %s, New size: %d",
                storageSystem.getSerialNumber(), storagePool.getNativeGuid(), volume.getLabel(), size));
        try {
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
            String systemObjectID = HDSUtils.getSystemObjectID(storageSystem);
            String asyncTaskMessageId = null;

            if (volume.getThinlyProvisioned()) {
                asyncTaskMessageId = hdsApiClient.modifyThinVolume(systemObjectID,
                        HDSUtils.getLogicalUnitObjectId(volume.getNativeId(), storageSystem), size, storageSystem.getModel());
            }

            if (null != asyncTaskMessageId) {
                HDSJob expandVolumeJob = new HDSVolumeExpandJob(asyncTaskMessageId,
                        storageSystem.getId(), storagePool.getId(), taskCompleter,
                        "ExpandVolume");
                ControllerServiceImpl.enqueueJob(new QueueJob(expandVolumeJob));
            }
        } catch (final InternalException e) {
            log.error("Problem in doExpandVolume: ", e);
            taskCompleter.error(dbClient, e);
        } catch (final Exception e) {
            log.error("Problem in doExpandVolume: ", e);
            ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("doExpandVolume", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        log.info(String.format("Expand Volume End - Array: %s, Pool: %s, Volume: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeGuid(), volume.getLabel()));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExpandAsMetaVolume(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.StoragePool, com.emc.storageos.db.client.model.Volume, long,
     * com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExpandAsMetaVolume(StorageSystem storageSystem,
            StoragePool storagePool, Volume metaHead, long size,
            MetaVolumeRecommendation recommendation, VolumeExpandCompleter volumeCompleter)
            throws DeviceControllerException {
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Expand Meta Volume Start - Array:%s, Pool:%s %n Volume: %s, id: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeId(), metaHead.getLabel(),
                metaHead.getId()));
        log.info(logMsgBuilder.toString());
        long metaMemberCapacity = recommendation.getMetaMemberSize();
        int metaMemberCount = (int) recommendation.getMetaMemberCount();
        MetaVolumeTaskCompleter metaVolumeTaskCompleter = new MetaVolumeTaskCompleter(
                volumeCompleter);
        try {
            // Step 1: create meta members.
            List<String> newMetaMembers = metaVolumeOperations
                    .createMetaVolumeMembers(storageSystem, storagePool,
                            metaHead, metaMemberCount, metaMemberCapacity,
                            metaVolumeTaskCompleter);
            log.info("ldevMetaMembers created successfully: {}", newMetaMembers);

            if (metaVolumeTaskCompleter.getLastStepStatus() == Job.JobStatus.SUCCESS) {
                metaVolumeOperations.expandMetaVolume(storageSystem, storagePool, metaHead, newMetaMembers, metaVolumeTaskCompleter);
            } else {
                ServiceError serviceError = DeviceControllerErrors.hds.jobFailed("LDEV Meta Member creation failed");
                volumeCompleter.error(dbClient, serviceError);
            }

        } catch (final InternalException e) {
            log.error("Problem in doExpandAsMetaVolume: ", e);
            volumeCompleter.error(dbClient, e);
        } catch (final Exception e) {
            log.error("Problem in doExpandAsMetaVolume: ", e);
            ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("doExpandAsMetaVolume", e.getMessage());
            volumeCompleter.error(dbClient, serviceError);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doDeleteVolumes(com.emc.storageos.db.client.model.StorageSystem,
     * java.lang.String, java.util.List, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doDeleteVolumes(StorageSystem storageSystem, String opId,
            List<Volume> volumes, TaskCompleter taskCompleter)
            throws DeviceControllerException {

        try {
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Delete Volume Start - Array:%s", storageSystem.getSerialNumber()));
            MultiVolumeTaskCompleter multiVolumeTaskCompleter = (MultiVolumeTaskCompleter) taskCompleter;
            Set<String> thickLogicalUnitIdList = new HashSet<String>();
            Set<String> thinLogicalUnitIdList = new HashSet<String>();
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
            String systemObjectId = HDSUtils.getSystemObjectID(storageSystem);
            log.info("volumes size: {}", volumes.size());
            for (Volume volume : volumes) {
                logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
                String logicalUnitObjectId = HDSUtils.getLogicalUnitObjectId(
                        volume.getNativeId(), storageSystem);
                LogicalUnit logicalUnit = hdsApiClient.getLogicalUnitInfo(systemObjectId,
                        logicalUnitObjectId);
                if (logicalUnit == null) {
                    // related volume state (if any) has been deleted. skip
                    // processing, if already deleted from array.
                    log.info(String.format("Volume %s already deleted: ",
                            volume.getNativeId()));
                    volume.setInactive(true);
                    dbClient.persistObject(volume);
                    VolumeTaskCompleter deleteTaskCompleter = multiVolumeTaskCompleter
                            .skipTaskCompleter(volume.getId());
                    deleteTaskCompleter.ready(dbClient);
                    continue;
                }
                if (volume.getThinlyProvisioned()) {
                    thinLogicalUnitIdList.add(logicalUnitObjectId);
                } else {
                    thickLogicalUnitIdList.add(logicalUnitObjectId);
                }

            }
            log.info(logMsgBuilder.toString());
            if (!multiVolumeTaskCompleter.isVolumeTaskCompletersEmpty()) {
                if (null != thickLogicalUnitIdList && !thickLogicalUnitIdList.isEmpty()) {
                    String asyncThickLUsJobId = hdsApiClient.deleteThickLogicalUnits(systemObjectId,
                            thickLogicalUnitIdList, storageSystem.getModel());
                    if (null != asyncThickLUsJobId) {
                        ControllerServiceImpl.enqueueJob(new QueueJob(new HDSDeleteVolumeJob(
                                asyncThickLUsJobId, volumes.get(0).getStorageController(),
                                taskCompleter)));
                    }
                }

                if (null != thinLogicalUnitIdList && !thinLogicalUnitIdList.isEmpty()) {
                    String asyncThinHDSJobId = hdsApiClient.deleteThinLogicalUnits(
                            systemObjectId, thinLogicalUnitIdList, storageSystem.getModel());

                    // Not sure whether this really works as tracking two jobs
                    // in single operation.
                    if (null != asyncThinHDSJobId) {
                        ControllerServiceImpl.enqueueJob(new QueueJob(
                                new HDSDeleteVolumeJob(asyncThinHDSJobId, volumes.get(0)
                                        .getStorageController(), taskCompleter)));
                    }
                }
            } else {
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
        } catch (Exception e) {
            log.error("Problem in doDeleteVolume: ", e);
            ServiceError error = DeviceControllerErrors.hds.methodFailed(
                    "doDeleteVolume", e.getMessage());
            taskCompleter.error(dbClient, error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Delete Volume End - Array: %s", storageSystem.getSerialNumber()));
        for (Volume volume : volumes) {
            logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
        }
        log.info(logMsgBuilder.toString());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportGroupCreate(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask, java.util.Map, java.util.List, java.util.List,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportGroupCreate(StorageSystem storage, ExportMask exportMask,
            Map<URI, Integer> volumeMap, List<Initiator> initiators, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportGroupCreate START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(storage.getSystemType(), volumeMap, dbClient);
        exportMaskOperationsHelper.createExportMask(storage, exportMask.getId(), volumeLunArray, targets, initiators, taskCompleter);
        log.info("{} doExportGroupCreate END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportGroupDelete(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportGroupDelete(StorageSystem storage, ExportMask exportMask,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportGroupDelete START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.deleteExportMask(storage, exportMask.getId(), new ArrayList<URI>(),
                new ArrayList<URI>(), new ArrayList<Initiator>(), taskCompleter);
        log.info("{} doExportGroupDelete END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddVolume(StorageSystem storage,
            ExportMask exportMask,
            URI volume, Integer lun,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        map.put(volume, lun);
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(storage.getSystemType(), map, dbClient);
        exportMaskOperationsHelper.addVolume(storage, exportMask.getId(), volumeLunArray, taskCompleter);
        log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddVolumes(StorageSystem storage, ExportMask exportMask,
            Map<URI, Integer> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(storage.getSystemType(), volumes, dbClient);

        exportMaskOperationsHelper.addVolume(storage, exportMask.getId(),
                volumeLunArray, taskCompleter);
        log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportRemoveVolume(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask, java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportRemoveVolume(StorageSystem storage, ExportMask exportMask,
            URI volume, TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(), Arrays.asList(volume), taskCompleter);
        log.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportRemoveVolumes(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask, java.util.List, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportRemoveVolumes(StorageSystem storage, ExportMask exportMask,
            List<URI> volumes, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(), volumes,
                taskCompleter);
        log.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportAddInitiator(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask, com.emc.storageos.db.client.model.Initiator, java.util.List,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportAddInitiator(StorageSystem storage, ExportMask exportMask,
            Initiator initiator, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(), Arrays.asList(initiator), targets, taskCompleter);
        log.info("{} doExportAddInitiator END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportAddInitiators(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask, java.util.List, java.util.List, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportAddInitiators(StorageSystem storage, ExportMask exportMask,
            List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(), initiators,
                targets, taskCompleter);
        log.info("{} doExportAddInitiator END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportRemoveInitiator(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask, com.emc.storageos.db.client.model.Initiator, java.util.List,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportRemoveInitiator(StorageSystem storage, ExportMask exportMask,
            Initiator initiator, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(),
                Arrays.asList(initiator), targets, taskCompleter);
        log.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExportRemoveInitiators(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask, java.util.List, java.util.List, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExportRemoveInitiators(StorageSystem storage, ExportMask exportMask,
            List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(),
                initiators, targets, taskCompleter);
        log.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doCreateSnapshot(com.emc.storageos.db.client.model.StorageSystem,
     * java.util.List, java.lang.Boolean, java.lang.Boolean, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // CG support is not yet implemented for HDS
        snapshotOperations.createSingleVolumeSnapshot(storage, snapshotList.get(0), createInactive, readOnly, taskCompleter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doDeleteSnapshot(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doRestoreFromSnapshot(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doRestoreFromSnapshot(StorageSystem storage, URI volume, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        snapshotOperations.restoreSingleVolumeSnapshot(storage, volume, snapshot, taskCompleter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doCreateMirror(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, java.lang.Boolean, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doCreateMirror(StorageSystem storage, URI mirror, Boolean createInactive,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("Started doCreateMirror");
        mirrorOperations.createSingleVolumeMirror(storage, mirror, createInactive, taskCompleter);
        log.info("Completed doCreateMirror");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doFractureMirror(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, java.lang.Boolean, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doFractureMirror(StorageSystem storage, URI mirror, Boolean sync,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("Started doFractureMirror");
        mirrorOperations.fractureSingleVolumeMirror(storage, mirror, sync, taskCompleter);
        log.info("Completed doFractureMirror");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doDetachMirror(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doDetachMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("Started doDetachMirror");
        mirrorOperations.detachSingleVolumeMirror(storage, mirror, taskCompleter);
        log.info("Completed doDetachMirror");
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.volumecontroller.BlockStorageDevice#doResumeNativeContinuousCopy(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doResumeNativeContinuousCopy(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("Started doResumeNativeContinuousCopy");
        mirrorOperations.resumeSingleVolumeMirror(storage, mirror, taskCompleter);
        log.info("Completed doResumeNativeContinuousCopy");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doDeleteMirror(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doDeleteMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("Started doDeleteMirror");
        mirrorOperations.deleteSingleVolumeMirror(storage, mirror, taskCompleter);
        log.info("Completed doDeleteMirror");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doCreateClone(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, java.net.URI, java.lang.Boolean, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doCreateClone(StorageSystem storageSystem, URI sourceVolumeURI,
            URI cloneVolumeURI, Boolean createInactive, TaskCompleter taskCompleter) {
        log.info("Inside doCreateClone");
        cloneOperations.createSingleClone(storageSystem, sourceVolumeURI, cloneVolumeURI, createInactive, taskCompleter);
        log.info("Completed doCreateClone");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doDetachClone(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doDetachClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        log.info("Started detach clone operation");
        cloneOperations.detachSingleClone(storage, cloneVolume, taskCompleter);
        log.info("Completed detach clone operation");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doCreateConsistencyGroup(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doCreateConsistencyGroup(StorageSystem storage, URI consistencyGroup,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        // throw new DeviceControllerException("UnSupported Operation");
        taskCompleter.ready(dbClient);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#findExportMasks(com.emc.storageos.db.client.model.StorageSystem,
     * java.util.List, boolean)
     */
    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) {
        return exportMaskOperationsHelper.findExportMasks(storage, initiatorNames, mustHaveAllPorts);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#refreshExportMask(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportMask)
     */
    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        return exportMaskOperationsHelper.refreshExportMask(storage, mask);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doActivateFullCopy(com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doActivateFullCopy(StorageSystem storageSystem, URI fullCopy,
            TaskCompleter completer) {
        log.info("Activate FullCopy started");
        cloneOperations.activateSingleClone(storageSystem, fullCopy, completer);
        log.info("Activate FullCopy completed");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doCleanupMetaMembers(com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.Volume,
     * com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter)
     */
    @Override
    public void doCleanupMetaMembers(StorageSystem storageSystem, Volume volume,
            CleanupMetaVolumeMembersCompleter cleanupCompleter)
            throws DeviceControllerException {
        // Remove meta member volumes from storage device
        try {
            log.info(String.format("doCleanupMetaMembers  Start - Array: %s, Volume: %s",
                    storageSystem.getSerialNumber(), volume.getLabel()));
            // Load meta volume members from WF data
            String sourceStepId = cleanupCompleter.getSourceStepId();
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getUsername(), storageSystem.getSmisPassword());
            List<String> metaMembers = (ArrayList<String>) WorkflowService.getInstance()
                    .loadStepData(sourceStepId);
            if (metaMembers != null && !metaMembers.isEmpty()) {
                log.info(String.format(
                        "doCleanupMetaMembers: Members stored for meta volume: %n %s",
                        metaMembers));
                // Check if volumes still exist in array and if it is not composite member (already
                // added to the meta volume)
                Set<String> volumeIds = new HashSet<String>();
                for (String logicalUnitObjectId : metaMembers) {
                    LogicalUnit logicalUnit = hdsApiClient.getLogicalUnitInfo(HDSUtils.getSystemObjectID(storageSystem),
                            logicalUnitObjectId);
                    if (logicalUnit != null) {
                        log.debug("doCleanupMetaMembers: Volume: " + logicalUnitObjectId
                                + ", Usage of volume: " + logicalUnit.getComposite());
                        if (logicalUnit.getComposite() != HDSConstants.COMPOSITE_ELEMENT_MEMBER) {
                            volumeIds.add(logicalUnitObjectId);
                        }
                    }
                }
                if (volumeIds.isEmpty()) {
                    cleanupCompleter.setSuccess(true);
                    log.info("doCleanupMetaMembers: No meta members to cleanup in array.");
                } else {
                    log.info(String
                            .format("doCleanupMetaMembers: Members to cleanup in array: %n   %s",
                                    volumeIds));
                    // Prepare parameters and call method to delete meta members from array

                    HDSCleanupMetaVolumeMembersJob hdsJobCompleter = null;
                    // When "cleanup" is separate workflow step, call async (for example rollback
                    // step in volume expand)
                    // Otherwise, call synchronously (for example when cleanup is part of meta
                    // volume create rollback)
                    String asyncMessageId = hdsApiClient.deleteThickLogicalUnits(HDSUtils.getSystemObjectID(storageSystem), volumeIds,
                            storageSystem.getModel());

                    if (cleanupCompleter.isWFStep()) {
                        if (asyncMessageId != null) {
                            ControllerServiceImpl.enqueueJob(new QueueJob(
                                    new HDSCleanupMetaVolumeMembersJob(asyncMessageId, storageSystem.getId(),
                                            volume.getId(), cleanupCompleter)));
                        }
                    } else {
                        // invoke synchronously
                        hdsJobCompleter = new HDSCleanupMetaVolumeMembersJob(
                                asyncMessageId, storageSystem.getId(), volume.getId(),
                                cleanupCompleter);
                        ((HDSMetaVolumeOperations) metaVolumeOperations)
                                .invokeMethodSynchronously(hdsApiFactory, asyncMessageId,
                                        hdsJobCompleter);
                    }
                }
            } else {
                log.info("doCleanupMetaMembers: No meta members stored for meta volume. Nothing to cleanup in array.");
                cleanupCompleter.setSuccess(true);
            }
        } catch (Exception e) {
            log.error("Problem in doCleanupMetaMembers: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doCleanupMetaMembers",
                    e.getMessage());
            cleanupCompleter.setError(error);
            cleanupCompleter.setSuccess(false);
        }
        log.info(String.format("doCleanupMetaMembers End - Array: %s,  Volume: %s",
                storageSystem.getSerialNumber(), volume.getLabel()));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.volumecontroller.BlockStorageDevice#doWaitForSynchronized
     * (java.lang.Class, com.emc.storageos.db.client.model.StorageSystem,
     * java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doWaitForSynchronized(Class<? extends BlockObject> clazz,
            StorageSystem storageObj, URI target, TaskCompleter completer) {
        log.info("START waitForSynchronized for {}", target);

        try {
            Volume targetObj = dbClient.queryObject(Volume.class, target);
            // Source could be either Volume or BlockSnapshot
            BlockObject sourceObj = BlockObject.fetch(dbClient,
                    targetObj.getAssociatedSourceVolume());

            // We split the pair which causes the data to be synchronized.
            // When the split is complete that data is synchronized.
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageObj),
                    storageObj.getSmisUserName(), storageObj.getSmisPassword());
            HDSApiProtectionManager hdsApiProtectionManager = hdsApiClient
                    .getHdsApiProtectionManager();
            String replicationGroupObjectID = hdsApiProtectionManager
                    .getReplicationGroupObjectId();
            ReplicationInfo replicationInfo = hdsApiProtectionManager
                    .getReplicationInfoFromSystem(sourceObj.getNativeId(),
                            targetObj.getNativeId()).first;
            hdsApiProtectionManager.modifyShadowImagePair(replicationGroupObjectID,
                    replicationInfo.getObjectID(),
                    HDSApiProtectionManager.ShadowImageOperationType.split, storageObj.getModel());

            // Update state in case we are waiting for synchronization
            // after creation of a new full copy that was not created
            // inactive.
            String state = targetObj.getReplicaState();
            if (!ReplicationState.SYNCHRONIZED.name().equals(state)) {
                targetObj.setSyncActive(true);
                targetObj.setReplicaState(ReplicationState.SYNCHRONIZED.name());
                dbClient.persistObject(targetObj);
            }

            // Queue job to wait for replication status to move to split.
            ControllerServiceImpl.enqueueJob(new QueueJob(new HDSReplicationSyncJob(
                    storageObj.getId(), sourceObj.getNativeId(), targetObj.getNativeId(),
                    ReplicationStatus.SPLIT, completer)));
        } catch (Exception e) {
            log.error("Exception occurred while waiting for synchronization", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(dbClient, serviceError);

        }
        log.info("completed doWaitForSynchronized");
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        boolean isConnectionValid = false;
        try {
            StringBuffer providerID = new StringBuffer(ipAddress).append(
                    HDSConstants.HYPHEN_OPERATOR).append(portNumber);
            URIQueryResultList providerUriList = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStorageProviderByProviderIDConstraint(providerID.toString()),
                    providerUriList);
            if (providerUriList.iterator().hasNext()) {
                StorageProvider provider = dbClient.queryObject(StorageProvider.class,
                        providerUriList.iterator().next());
                HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                        HDSUtils.getHDSServerManagementServerInfo(provider),
                        provider.getUserName(), provider.getPassword());
                List<StorageArray> storageArrayList = hdsApiClient
                        .getStorageSystemsInfo();
                if (null != storageArrayList && !storageArrayList.isEmpty()) {
                    isConnectionValid = true;
                }

            }
        } catch (Exception ex) {
            log.error(
                    "Problem in checking provider live connection for ipaddress: {} due to",
                    ipAddress, ex);
        }
        return isConnectionValid;
    }

    @Override
    public void updatePolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVpool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {
        exportMaskOperationsHelper.updateStorageGroupPolicyAndLimits(
                storage, exportMask, volumeURIs, newVpool, rollback, taskCompleter);
    }

    public void setCloneOperations(CloneOperations cloneOperations) {
        this.cloneOperations = cloneOperations;
    }

    @Override
    public void doModifyVolumes(StorageSystem storage, StoragePool storagePool, String opId, List<Volume> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        StringBuilder logMsgBuilder = new StringBuilder(String.format("Modify Volume Start - Array:%s, Pool:%s",
                storage.getSerialNumber(), storagePool.getNativeGuid()));

        String systemObjectID = HDSUtils.getSystemObjectID(storage);
        for (Volume volume : volumes) {
            try {
                HDSApiClient hdsApiClient = hdsApiFactory.getClient(HDSUtils.getHDSServerManagementServerInfo(storage),
                        storage.getSmisUserName(), storage.getSmisPassword());
                logMsgBuilder.append(String.format("%nVolume:%s , IsThinlyProvisioned: %s, tieringPolicy: %s",
                        volume.getLabel(), volume.getThinlyProvisioned(), volume.getAutoTieringPolicyUri()));
                LogicalUnit logicalUnit = hdsApiClient.getLogicalUnitInfo(systemObjectID,
                        HDSUtils.getLogicalUnitObjectId(volume.getNativeId(), storage));
                String policyName = ControllerUtils.getAutoTieringPolicyName(volume.getId(), dbClient);
                String autoTierPolicyName = null;
                if (policyName.equals(Constants.NONE)) {
                    autoTierPolicyName = null;
                } else {
                    autoTierPolicyName = HitachiTieringPolicy.getPolicy(
                            policyName.replaceAll(HDSConstants.SLASH_OPERATOR, HDSConstants.UNDERSCORE_OPERATOR))
                            .getKey();
                }
                if (null != logicalUnit && null != logicalUnit.getLdevList() && !logicalUnit.getLdevList().isEmpty()) {
                    Iterator<LDEV> ldevItr = logicalUnit.getLdevList().iterator();
                    if (ldevItr.hasNext()) {
                        LDEV ldev = ldevItr.next();
                        String asyncMessageId = hdsApiClient.modifyThinVolumeTieringPolicy(systemObjectID,
                                logicalUnit.getObjectID(), ldev.getObjectID(), autoTierPolicyName, storage.getModel());
                        if (null != asyncMessageId) {
                            HDSJob modifyHDSJob = new HDSModifyVolumeJob(asyncMessageId, volume.getStorageController(),
                                    taskCompleter, HDSModifyVolumeJob.VOLUME_MODIFY_JOB);
                            ControllerServiceImpl.enqueueJob(new QueueJob(modifyHDSJob));
                        }
                    }
                } else {
                    String errorMsg = String.format("No LDEV's found for volume: %s", volume.getId());
                    log.info(errorMsg);
                    ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("doModifyVolumes", errorMsg);
                    taskCompleter.error(dbClient, serviceError);
                }
            } catch (final InternalException e) {
                log.error("Problem in doModifyVolumes: ", e);
                taskCompleter.error(dbClient, e);
            } catch (final Exception e) {
                log.error("Problem in doModifyVolumes: ", e);
                ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("doModifyVolumes", e.getMessage());
                taskCompleter.error(dbClient, serviceError);
            }
        }
    }

    public void setMirrorOperations(MirrorOperations mirrorOperations) {
        this.mirrorOperations = mirrorOperations;
    }

    public void setSnapshotOperations(SnapshotOperations snapshotOperations) {
        this.snapshotOperations = snapshotOperations;
    }

    @Override
    public void doRestoreFromClone(StorageSystem storageSystem, URI cloneURI,
            TaskCompleter taskCompleter) {
        log.info("Restore from full copy {} started", cloneURI);
        cloneOperations.restoreFromSingleClone(storageSystem, cloneURI, taskCompleter);
        log.info("Restore from full copy completed");
    }

    @Override
    public void doResyncClone(StorageSystem storageSystem, URI cloneURI,
            TaskCompleter taskCompleter) {
        log.info("Resynchronize full copy {} started", cloneURI);
        cloneOperations.resyncSingleClone(storageSystem, cloneURI, taskCompleter);
        log.info("Resynchronize full copy completed");
    }

}
