/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxunity;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.BlockHostAccess;
import com.emc.storageos.vnxe.models.FastVPParam;
import com.emc.storageos.vnxe.models.HostTypeEnum;
import com.emc.storageos.vnxe.models.LunCreateParam;
import com.emc.storageos.vnxe.models.LunParam;
import com.emc.storageos.vnxe.models.StorageResource;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.vnxe.models.BlockHostAccess.HostLUNAccessEnum;
import com.emc.storageos.vnxe.models.StorageResource.TieringPolicyEnum;
import com.emc.storageos.vnxe.requests.StorageResourceRequest;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.vnxe.VNXeUtils;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeCreateVolumesJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeExpandVolumeJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeJob;
import com.emc.storageos.volumecontroller.impl.vnxunity.job.VNXUnityCreateVolumesJob;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.collect.Lists;

public class VNXUnityBlockStorageDevice extends VNXUnityOperations
        implements BlockStorageDevice {

    private static final Logger logger = LoggerFactory.getLogger(VNXUnityBlockStorageDevice.class);

    private SnapshotOperations snapshotOperations;
    private NameGenerator nameGenerator;
    private WorkflowService workflowService;

    public NameGenerator getNameGenerator() {
        return nameGenerator;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    private ExportMaskOperations exportMaskOperationsHelper;

    public ExportMaskOperations getExportMaskOperationsHelper() {
        return exportMaskOperationsHelper;
    }

    public void setExportMaskOperationsHelper(
            ExportMaskOperations exportMaskOperationsHelper) {
        this.exportMaskOperationsHelper = exportMaskOperationsHelper;
    }

    public void setSnapshotOperations(final SnapshotOperations snapshotOperations) {
        this.snapshotOperations = snapshotOperations;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public void doConnect(StorageSystem storage) throws ControllerException {
        try {
            logger.info("doConnect {} - start", storage.getId());
            VNXeApiClient client = getVnxUnityClient(storage);
            client.getStorageSystem();
            String msg = String.format("doConnect %1$s - complete", storage.getId());
            logger.info(msg);
        } catch (VNXeException e) {
            logger.error("doConnect failed.", e);
            throw DeviceControllerException.exceptions.connectStorageFailed(e);
        }
    }

    @Override
    public void doDisconnect(StorageSystem storage) {
        try {
            logger.info("doDisconnect {} - start", storage.getId());
            VNXeApiClient client = getVnxUnityClient(storage);
            client.logout();
            String msg = String.format("doDisconnect %1$s - complete", storage.getId());
            logger.info(msg);

        } catch (VNXeException e) {
            logger.error("doDisconnect failed.", e);
            throw DeviceControllerException.exceptions.disconnectStorageFailed(e);
        }
    }

    @Override
    public void doCreateVolumes(StorageSystem storage, StoragePool storagePool,
            String opId, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        logger.info("creating volumes, array: {}, pool : {}", storage.getSerialNumber(),
                storagePool.getNativeId());
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        List<String> jobs = new ArrayList<String>();
        boolean opFailed = false;
        try {
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_022);
            Map<String, List<Volume>> cgVolumes = new HashMap<String, List<Volume>>();
            Map<String, List<URI>> jobVolumesMap = new HashMap<String, List<URI>>();
            for (Volume volume : volumes) {
                String tenantName = "";
                try {
                    TenantOrg tenant = dbClient.queryObject(TenantOrg.class, volume.getTenant()
                            .getURI());
                    tenantName = tenant.getLabel();
                } catch (DatabaseException e) {
                    logger.error("Error lookup TenantOrb object", e);
                }
                String label = nameGenerator.generate(tenantName, volume.getLabel(), volume.getId()
                        .toString(), '-', VNXeConstants.MAX_NAME_LENGTH);

                String cgName = volume.getReplicationGroupInstance();

                volume.setNativeGuid(label);
                dbClient.updateObject(volume);
                if (NullColumnValueGetter.isNullValue(cgName)) {
                    String autoTierPolicyName = ControllerUtils.getAutoTieringPolicyName(volume.getId(), dbClient);
                    if (autoTierPolicyName.equals(Constants.NONE)) {
                        autoTierPolicyName = null;
                    }
                    VNXeCommandJob job = apiClient.createLun(label, storagePool.getNativeId(), volume.getCapacity(),
                            volume.getThinlyProvisioned(), autoTierPolicyName);
                    jobs.add(job.getId());
                    jobVolumesMap.put(job.getId(), Arrays.asList(volume.getId()));
                } else {
                    logger.info(String.format("Creating the volume %s in CG %s", volume.getLabel(), cgName));
                    List<Volume> vols = cgVolumes.get(cgName);
                    if (vols == null) {
                        vols = new ArrayList<Volume> ();
                    }
                    vols.add(volume);
                    cgVolumes.put(cgName, vols);
                }
            }
            for (Map.Entry<String, List<Volume>> cgVol : cgVolumes.entrySet()) {
                // Creating volumes in a CG
                String cgName = cgVol.getKey();
                String cgId = apiClient.getConsistencyGroupIdByName(cgName);
                if (cgId == null) {
                    String errorMsg = String.format("The CG %s could not be found in the array", cgName);
                    logger.error(errorMsg);
                    ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateVolumes", errorMsg);
                    taskCompleter.error(dbClient, error);
                    dbClient.markForDeletion(cgVol.getValue());
                    return;
                }
                List<Volume> vols = cgVol.getValue();
                VNXeUtils.getCGLock(workflowService, storage, cgName, opId);
                List<LunCreateParam> lunCreates = new ArrayList<LunCreateParam>();
                StorageResource cg = apiClient.getConsistencyGroup(cgId);
                List<URI> volURIs = new ArrayList<URI>();
                for (Volume volToCreate : vols) {
                    volURIs.add(volToCreate.getId());
                    boolean isPolicyOn = false;
                    String tierPolicy = ControllerUtils.getAutoTieringPolicyName(volToCreate.getId(), dbClient);
                    FastVPParam fastVP = new FastVPParam();
                    if (!tierPolicy.equals(Constants.NONE)) {
                        TieringPolicyEnum tierValue = TieringPolicyEnum.valueOf(tierPolicy);
                        if (tierValue != null) {
                            fastVP.setTieringPolicy(tierValue.getValue());
                            isPolicyOn = true;
                        }
                    }

                    // Construct Unity API LunParam for each volume to be created.
                    LunParam lunParam = new LunParam();
                    lunParam.setIsThinEnabled(volToCreate.getThinlyProvisioned());
                    lunParam.setSize(volToCreate.getCapacity());
                    lunParam.setPool(new VNXeBase(storagePool.getNativeId()));
                    List<BlockHostAccess> hostAccesses = cg.getBlockHostAccess();
                    if (hostAccesses != null && !hostAccesses.isEmpty()) {
                        for (BlockHostAccess hostAccess : hostAccesses) {
                            hostAccess.setAccessMask(HostLUNAccessEnum.NOACCESS.getValue());
                        }
                        lunParam.setHostAccess(hostAccesses);
                    }
                    LunCreateParam createParam = new LunCreateParam();
                    createParam.setName(volToCreate.getLabel());
                    createParam.setLunParameters(lunParam);
                    if (isPolicyOn) {
                        lunParam.setFastVPParameters(fastVP);
                    }
                    lunCreates.add(createParam);
                }
                VNXeCommandJob result = apiClient.createLunsInConsistencyGroup(lunCreates, cgId);
                jobs.add(result.getId());
                jobVolumesMap.put(result.getId(), volURIs);
            }

            VNXUnityCreateVolumesJob createVolumesJob = new VNXUnityCreateVolumesJob(jobVolumesMap, jobs, storage.getId(),
                    taskCompleter, storagePool.getId());

            ControllerServiceImpl.enqueueJob(new QueueJob(createVolumesJob));
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_023);
        } catch (VNXeException e) {
            logger.error("Create volumes got the exception", e);
            opFailed = true;
            taskCompleter.error(dbClient, e);

        } catch (Exception ex) {
            logger.error("Create volumes got the exception", ex);
            opFailed = true;
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateVolumes", ex.getMessage());
            taskCompleter.error(dbClient, error);

        }
        if (opFailed) {
            for (Volume vol : volumes) {
                vol.setInactive(true);
                dbClient.updateObject(vol);
            }
        }

    }

    @Override
    public void doCreateMetaVolume(StorageSystem storage,
            StoragePool storagePool, Volume volume,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeRecommendation recommendation,
            VolumeCreateCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doExpandVolume(StorageSystem storage, StoragePool pool,
            Volume volume, Long size, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info(String.format("Expand Volume Start - Array: %s, Pool: %s, Volume: %s, New size: %d",
                storage.getSerialNumber(), pool.getNativeGuid(), volume.getLabel(), size));

        String cgName = volume.getReplicationGroupInstance();
        String consistencyGroupId = null;

        try {
            VNXeApiClient apiClient = getVnxUnityClient(storage);
            if (NullColumnValueGetter.isNotNullValue(cgName)) {
                consistencyGroupId = apiClient.getConsistencyGroupIdByName(cgName);
                VNXeUtils.getCGLock(workflowService, storage, cgName, taskCompleter.getOpId());
            }
            VNXeCommandJob commandJob = apiClient.expandLun(volume.getNativeId(), size, consistencyGroupId);
            VNXeExpandVolumeJob expandVolumeJob = new VNXeExpandVolumeJob(commandJob.getId(), storage.getId(), taskCompleter);
            ControllerServiceImpl.enqueueJob(new QueueJob(expandVolumeJob));

        } catch (VNXeException e) {
            logger.error("Expand volume got the exception", e);
            taskCompleter.error(dbClient, e);

        } catch (Exception ex) {
            logger.error("Expand volume got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("ExpandVolume", ex.getMessage());
            taskCompleter.error(dbClient, error);

        }

    }

    @Override
    public void doDeleteVolumes(StorageSystem storageSystem, String opId,
            List<Volume> volumes, TaskCompleter completer)
                    throws DeviceControllerException {
        logger.info("deleting volumes, array: {}", storageSystem.getSerialNumber());
        VNXeApiClient apiClient = getVnxUnityClient(storageSystem);
        Map<String, List<String>> cgNameMap = new HashMap<String, List<String>>();
        try {
            Set<URI> updateStoragePools = new HashSet<URI>();
            // Invoke a test failure if testing
            for (Volume volume : volumes) {
                String lunId = volume.getNativeId();
                if (NullColumnValueGetter.isNullValue(lunId)) {
                    logger.info(String.format("The volume %s does not have native id, do nothing", volume.getLabel()));
                    continue;
                }
                updateStoragePools.add(volume.getPool());
                if (!apiClient.checkLunExists(lunId)) {
                    logger.info(String.format("The volume %s does not exist in the array, do nothing", volume.getLabel()));
                    continue;
                }

                String cgName = volume.getReplicationGroupInstance();
                if (NullColumnValueGetter.isNotNullValue(cgName)) {
                    List<String> lunIds = cgNameMap.get(cgName);
                    if (lunIds == null) {
                        lunIds = new ArrayList<String>();
                        cgNameMap.put(cgName, lunIds);
                    }
                    lunIds.add(volume.getNativeId());
                } else {
                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_035);
                    apiClient.deleteLunSync(volume.getNativeId(), false);
                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_036);
                }
            }

            for (Map.Entry<String, List<String>> entry : cgNameMap.entrySet()) {
                String cgName = entry.getKey();
                List<String> lunIDs = entry.getValue();
                String cgId = apiClient.getConsistencyGroupIdByName(cgName);
                boolean isRP = false;
                if (cgId != null && !cgId.isEmpty()) {
                    // Check if the CG has blockHostAccess to a RP host. if the CG is exported to a RP, we could not delete the lun
                    // directly, we have to remove the volume from the CG first, then delete it.
                    StorageResource cg = apiClient.getStorageResource(cgId);
                    List<BlockHostAccess> hosts = cg.getBlockHostAccess();
                    if (hosts != null && !hosts.isEmpty()) {
                        for (BlockHostAccess hostAccess : hosts) {
                            VNXeBase hostId = hostAccess.getHost();
                            if (hostId != null) {
                                VNXeHost host = apiClient.getHostById(hostId.getId());
                                if (host != null) {
                                    if (host.getType() == HostTypeEnum.RPA.getValue()) {
                                        // Remove the luns from the CG
                                        isRP = true;
                                        logger.info(String.format("Removing volumes from CG because the CG %sis exported to RP", cgName));
                                        VNXeUtils.getCGLock(workflowService, storageSystem, cgName, opId);
                                        InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_034);
                                        apiClient.removeLunsFromConsistencyGroup(cgId, lunIDs);
                                        for (String lunId : lunIDs) {
                                            logger.info(String.format("Deleting the volume %s", lunId));
                                            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_035);
                                            apiClient.deleteLunSync(lunId, false);
                                            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_036);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (!isRP) {
                    VNXeUtils.getCGLock(workflowService, storageSystem, cgName, opId);
                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_034);
                    apiClient.deleteLunsFromConsistencyGroup(cgId, lunIDs);
                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_037);
                }
            }

            for (URI pool : updateStoragePools) {
                VNXeJob.updateStoragePoolCapacity(dbClient, apiClient, pool, null);
            }
            completer.ready(dbClient);

        } catch (VNXeException e) {
            logger.error("Delete volumes got the exception", e);
            completer.error(dbClient, e);

        } catch (Exception ex) {
            logger.error("Delete volumes got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteVolumes", ex.getMessage());
            completer.error(dbClient, error);

        }

    }

    @Override
    public void doExportCreate(StorageSystem storage,
            ExportMask exportMask, Map<URI, Integer> volumeMap,
            List<Initiator> initiators, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        logger.info("{} doExportGroupCreate START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumeMap, dbClient);
        exportMaskOperationsHelper.createExportMask(storage, exportMask.getId(), volumeLunArray,
                targets, initiators, taskCompleter);
        logger.info("{} doExportGroupCreate END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportDelete(StorageSystem storage,
            ExportMask exportMask, List<URI> volumeURIs, List<URI> initiatorURIs, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info("{} doExportGroupDelete START ...", storage.getSerialNumber());
        List<URI> volumes = new ArrayList<URI>();
        StringMap maskVolumes = exportMask.getVolumes();

        if (maskVolumes != null && !maskVolumes.isEmpty()) {
            for (String volURI : maskVolumes.keySet()) {
                volumes.add(URI.create(volURI));
            }
        }

        List<Initiator> initiators = Lists.newArrayList();
        if (initiatorURIs != null) {
            initiators.addAll(dbClient.queryObject(Initiator.class, initiatorURIs));
        }

        exportMaskOperationsHelper.deleteExportMask(storage, exportMask.getId(),
                volumes, new ArrayList<URI>(), initiators,
                taskCompleter);
        logger.info("{} doExportGroupDelete END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddVolume(StorageSystem storage, ExportMask exportMask,
            URI volume, Integer lun, List<Initiator> initiators, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        map.put(volume, lun);
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), map, dbClient);
        exportMaskOperationsHelper.addVolumes(storage, exportMask.getId(), volumeLunArray,
                initiators, taskCompleter);
        logger.info("{} doExportAddVolume END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddVolumes(StorageSystem storage,
            ExportMask exportMask, List<Initiator> initiators, Map<URI, Integer> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        logger.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumes, dbClient);
        exportMaskOperationsHelper.addVolumes(storage, exportMask.getId(), volumeLunArray, initiators,
                taskCompleter);
        logger.info("{} doExportAddVolume END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveVolume(StorageSystem storage,
            ExportMask exportMask, URI volume, List<Initiator> initiators, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeVolumes(storage, exportMask.getId(),
                Arrays.asList(volume), initiators, taskCompleter);
        logger.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveVolumes(StorageSystem storage,
            ExportMask exportMask, List<URI> volumes, List<Initiator> initiators,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        logger.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeVolumes(storage, exportMask.getId(), volumes,
                initiators, taskCompleter);
        logger.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddInitiator(StorageSystem storage,
            ExportMask exportMask, List<URI> volumeURIs, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        logger.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.addInitiators(storage, exportMask.getId(),
                volumeURIs, Arrays.asList(initiator), targets, taskCompleter);
        logger.info("{} doExportAddInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportAddInitiators(StorageSystem storage,
            ExportMask exportMask, List<URI> volumeURIs, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.addInitiators(storage, exportMask.getId(), volumeURIs, initiators, targets,
                taskCompleter);
        logger.info("{} doExportAddInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveInitiator(StorageSystem storage,
            ExportMask exportMask, List<URI> volumeURIs, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        logger.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeInitiators(storage, exportMask.getId(), volumeURIs,
                Arrays.asList(initiator), targets, taskCompleter);
        logger.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doExportRemoveInitiators(StorageSystem storage,
            ExportMask exportMask, List<URI> volumeURIs, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        exportMaskOperationsHelper.removeInitiators(storage, exportMask.getId(), volumeURIs,
                initiators, targets, taskCompleter);
        logger.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());

    }

    @Override
    public void doCreateSingleSnapshot(StorageSystem storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        logger.info("{} doCreateSingleSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = dbClient
                .queryObject(BlockSnapshot.class, snapshotList);
        URI snapshot = snapshots.get(0).getId();
        snapshotOperations.createSingleVolumeSnapshot(storage, snapshot, createInactive,
                readOnly, taskCompleter);
        logger.info("{} doCreateSingleSnapshot END ...", storage.getSerialNumber());
    }

    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
                    throws DeviceControllerException {

        logger.info("{} doCreateSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = dbClient
                .queryObject(BlockSnapshot.class, snapshotList);
        if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, dbClient, taskCompleter)) {
            snapshotOperations.createGroupSnapshots(storage, snapshotList, createInactive, readOnly, taskCompleter);
        } else {
            URI snapshot = snapshots.get(0).getId();
            snapshotOperations.createSingleVolumeSnapshot(storage, snapshot, createInactive,
                    readOnly, taskCompleter);

        }
        logger.info("{} doCreateSnapshot END ...", storage.getSerialNumber());
    }

    @Override
    public void doActivateSnapshot(StorageSystem storage,
            List<URI> snapshotList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info("{} doActivateSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, snapshotList);
        URI snapshot = snapshots.get(0).getId();
        if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, dbClient, taskCompleter)) {
            snapshotOperations.activateGroupSnapshots(storage, snapshot, taskCompleter);
        } else {
            snapshotOperations.activateSingleVolumeSnapshot(storage, snapshot, taskCompleter);

        }
        logger.info("{} doActivateSnapshot END ...", storage.getSerialNumber());

    }

    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        logger.info("{} doDeleteSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));

        if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, dbClient, taskCompleter)) {
            snapshotOperations.deleteGroupSnapshots(storage, snapshot, taskCompleter);
        } else {
            snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);

        }
        logger.info("{} doDeleteSnapshot END ...", storage.getSerialNumber());

    }

    @Override
    public void doDeleteSelectedSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        logger.info("{} doDeleteSelectedSnapshot START ...", storage.getSerialNumber());
        try {
            snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to delete snapshot(s) on array %s",
                    storage.getSerialNumber());
            logger.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doDeleteSnapshot",
                    e.getMessage());
            taskCompleter.error(dbClient, error);
        }
        logger.info("{} doDeleteSelectedSnapshot END ...", storage.getSerialNumber());
    }

    @Override
    public void doRestoreFromSnapshot(StorageSystem storage, URI volume,
            URI snapshot, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info("{} doRestoreFromSnapshot START ...", storage.getSerialNumber());
        List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));

        if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, dbClient, taskCompleter)) {
            snapshotOperations.restoreGroupSnapshots(storage, volume, snapshot, taskCompleter);
        } else {
            snapshotOperations.restoreSingleVolumeSnapshot(storage, volume, snapshot, taskCompleter);

        }
        logger.info("{} doRestoreFromSnapshot END ...", storage.getSerialNumber());

    }

    @Override
    public void doFractureMirror(StorageSystem storage, URI mirror,
            Boolean sync, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doDetachMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doResumeNativeContinuousCopy(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doEstablishVolumeNativeContinuousCopyGroupRelation(
            StorageSystem storage, URI sourceVolume, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doEstablishVolumeSnapshotGroupRelation(
            StorageSystem storage, URI sourceVolume, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doCreateClone(StorageSystem storageSystem, URI sourceVolume,
            URI cloneVolume, Boolean createInactive, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doDetachClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doCreateConsistencyGroup(StorageSystem storage,
            URI consistencyGroup, String replicationGroupName, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info("creating consistency group, array: {}", storage.getSerialNumber());
        BlockConsistencyGroup consistencyGroupObj = dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroup);
        VNXeApiClient apiClient = getVnxUnityClient(storage);

        String label = null;
        if (NullColumnValueGetter.isNotNullValue(replicationGroupName)) {
            label = replicationGroupName;
        } else {
            label = consistencyGroupObj.getLabel();
        }

        try {
            VNXeCommandResult result = apiClient.createConsistencyGroup(label);
            if (result.getStorageResource() != null) {
                consistencyGroupObj.addSystemConsistencyGroup(storage.getId().toString(), label);
                consistencyGroupObj.addConsistencyGroupTypes(Types.LOCAL.name());
                if (NullColumnValueGetter.isNullURI(consistencyGroupObj.getStorageController())) {
                    consistencyGroupObj.setStorageController(storage.getId());
                }
                dbClient.updateObject(consistencyGroupObj);
                taskCompleter.ready(dbClient);
                logger.info("Consistency group {} created", label);
            } else {
                logger.error("No storage resource Id returned");
                BlockConsistencyGroupUtils.cleanUpCGAndUpdate(consistencyGroupObj, storage.getId(), null, false, dbClient);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateConsistencyGroup failed");
                taskCompleter.error(dbClient, error);
            }
        } catch (Exception e) {
            logger.error("Exception caught when creating consistency group ", e);
            BlockConsistencyGroupUtils.cleanUpCGAndUpdate(consistencyGroupObj, storage.getId(), null, false, dbClient);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateConsistencyGroup", e.getMessage());
            taskCompleter.error(dbClient, error);
        }

    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, String replicationGroupName, Boolean keepRGName, Boolean markInactive, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        logger.info("Deleting consistency group, array: {}", storage.getSerialNumber());
        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);

        StringSet cgNames = new StringSet();
        if (NullColumnValueGetter.isNullValue(replicationGroupName)) {
            StringSetMap ssm = consistencyGroup.getSystemConsistencyGroups();
            if (ssm != null) {
                cgNames = ssm.get(storage.getId().toString());
                if (cgNames == null || cgNames.isEmpty()) {
                    logger.info("There is no array consistency group to be deleted.");
                    // Clean up the system consistency group references
                    BlockConsistencyGroupUtils.cleanUpCGAndUpdate(consistencyGroup, storage.getId(), null, markInactive, dbClient);
                    taskCompleter.ready(dbClient);
                    return;
                }
            }

        } else {
            cgNames.add(replicationGroupName);
        }
        VNXeApiClient apiClient = getVnxUnityClient(storage);
        try {
            for (String cgName : cgNames) {
                logger.info("Deleting the consistency group {}", cgName);
                String id = apiClient.getConsistencyGroupIdByName(cgName);
                if (id != null && !id.isEmpty()) {
                    apiClient.deleteConsistencyGroup(id, false, false);
                }

                if (!keepRGName) {
                    // Clean up the system consistency group references
                    BlockConsistencyGroupUtils.cleanUpCGAndUpdate(consistencyGroup, storage.getId(), cgName, markInactive, dbClient);
                    if (consistencyGroup.getInactive()) {
                        logger.info("CG is deleted");
                    }
                }
            }
            
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            logger.info("Failed to delete consistency group: " + e);
            // Set task to error
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed(
                    "doDeleteConsistencyGroup", e.getMessage());
            taskCompleter.error(dbClient, error);
        }

    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage, final URI consistencyGroupId,
            String replicationGroupName, Boolean keepRGName, Boolean markInactive,
            String sourceReplicationGroup, final TaskCompleter taskCompleter) throws DeviceControllerException {
        doDeleteConsistencyGroup(storage, consistencyGroupId, replicationGroupName, keepRGName, markInactive, taskCompleter);
    }

    @Override
    public String doAddStorageSystem(StorageSystem storage)
            throws DeviceControllerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void doRemoveStorageSystem(StorageSystem storage)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void doCopySnapshotsToTarget(StorageSystem storage,
            List<URI> snapshotList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) throws DeviceControllerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Set<Integer> findHLUsForInitiators(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts) {
        return exportMaskOperationsHelper.findHLUsForInitiators(storage, initiatorNames, mustHaveAllPorts);
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) throws DeviceControllerException {
        return exportMaskOperationsHelper.refreshExportMask(storage, mask);
    }

    @Override
    public void doActivateFullCopy(StorageSystem storageSystem, URI fullCopy,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doCleanupMetaMembers(StorageSystem storageSystem,
            Volume volume, CleanupMetaVolumeMembersCompleter cleanupCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public Integer checkSyncProgress(URI storage, URI source, URI target) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void doWaitForSynchronized(Class<? extends BlockObject> clazz,
            StorageSystem storageObj, URI target, TaskCompleter completer) {
        return;

    }

    @Override
    public void doWaitForGroupSynchronized(StorageSystem storageObj, List<URI> target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doAddToConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, String replicationGroupName, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);

        VNXeApiClient apiClient = getVnxUnityClient(storage);
        try {
            List<String> luns = new ArrayList<String>();
            for (URI volume : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(dbClient, volume);
                if (blockObject instanceof Volume) {
                    Volume lun = (Volume) blockObject;
                    luns.add(lun.getNativeId());
                } else {
                    String errorStr = String.format("The blockObject %s is not a volume. it is not supported.", volume.toString());
                    logger.error(errorStr);
                    handleAddToCGError(blockObjects, taskCompleter, consistencyGroup.getLabel(), replicationGroupName, errorStr);
                    return;

                }
            }
            String cgNativeId = apiClient.getConsistencyGroupIdByName(replicationGroupName);
            if (cgNativeId == null || cgNativeId.isEmpty()) {
                String errorStr = String.format("Could not find the consistency group %s in the error", replicationGroupName);
                logger.error(errorStr);
                handleAddToCGError(blockObjects, taskCompleter, consistencyGroup.getLabel(), replicationGroupName, errorStr);
                return;
            }
            apiClient.addLunsToConsistencyGroup(cgNativeId, luns);
            for (URI blockObjectURI : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectURI);
                if (blockObject != null) {
                    blockObject.setConsistencyGroup(consistencyGroupId);
                }
                dbClient.updateObject(blockObject);
            }

            taskCompleter.ready(dbClient);
            logger.info("Added volumes to the consistency group successfully");
        } catch (Exception e) {
            logger.error("Exception caught when adding volumes to the consistency group ", e);
            handleAddToCGError(blockObjects, taskCompleter, consistencyGroup.getLabel(), replicationGroupName, e.getMessage());
        }

    }

    /**
     * Handle the error case when adding volumes to a consistency group
     * 
     * @param blockObjects
     *            The list of block objects URIs that are adding to the consistency group
     * @param taskCompleter
     *            The task completer instance
     * @param cgName
     *            The consistency group name
     * @param replicationGroupName
     *            The replication group name in the array
     * @param msg
     *            The error message
     */
    private void handleAddToCGError(List<URI> blockObjects, TaskCompleter taskCompleter, String cgName,
            String replicationGroupName, String msg) {
        // Remove any references to the consistency group
        for (URI blockObjectURI : blockObjects) {
            BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectURI);
            if (blockObject != null) {
                blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
            }
            dbClient.updateObject(blockObject);
        }
        taskCompleter.error(dbClient, DeviceControllerException.exceptions
                .failedToAddMembersToConsistencyGroup(cgName,
                        replicationGroupName, msg));

    }

    @Override
    public void doRemoveFromConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);

        VNXeApiClient apiClient = getVnxUnityClient(storage);
        try {
            List<String> luns = new ArrayList<String>();
            String cgName = null;
            // All volumes belongs to the same array consistency group.
            for (URI volume : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(dbClient, volume);
                if (blockObject != null && !blockObject.getInactive()
                        && !NullColumnValueGetter.isNullValue(blockObject.getReplicationGroupInstance())) {
                    luns.add(blockObject.getNativeId());
                    if (cgName == null) {
                        cgName = blockObject.getReplicationGroupInstance();
                    }
                }
            }
            if (cgName != null) {
                String cgId = apiClient.getConsistencyGroupIdByName(cgName);
                apiClient.removeLunsFromConsistencyGroup(cgId, luns);
            } else {
                logger.warn(String.format("The block object %s is not in a CG", blockObjects.get(0).toString()));
            }
            taskCompleter.ready(dbClient);
            logger.info("Remove volumes from the consistency group successfully");
        } catch (Exception e) {
            logger.error("Exception caught when removing volumes from the consistency group ", e);
            taskCompleter.error(dbClient, DeviceControllerException.exceptions
                    .failedToRemoveMembersToConsistencyGroup(consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId()), e.getMessage()));
        }

    }

    @Override
    public void doAddToReplicationGroup(StorageSystem storage,
            URI consistencyGroupId, String replicationGroupName, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveFromReplicationGroup(StorageSystem storage,
            URI consistencyGroupId, String replicationGroupName, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress,
            Integer portNumber) {

        return false;
    }

    @Override
    public void doCreateMetaVolumes(StorageSystem storage,
            StoragePool storagePool, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeRecommendation recommendation, TaskCompleter completer)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doExpandAsMetaVolume(StorageSystem storageSystem,
            StoragePool storagePool, Volume volume, long size,
            MetaVolumeRecommendation recommendation,
            VolumeExpandCompleter volumeCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void updatePolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVpool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doTerminateAnyRestoreSessions(StorageSystem storageDevice, URI source, BlockObject snapshot,
            TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doModifyVolumes(StorageSystem storage, StoragePool storagePool, String opId, List<Volume> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public ExportMaskPolicy getExportMaskPolicy(StorageSystem storage, ExportMask mask) {
        // No special policy for this device type yet.
        return new ExportMaskPolicy();
    }

    @Override
    public void doFractureClone(StorageSystem storageDevice, URI source, URI clone,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRestoreFromClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doResyncClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateGroupClone(StorageSystem storageDevice, List<URI> clones,
            Boolean createInactive, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachGroupClone(StorageSystem storage, List<URI> cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doEstablishVolumeFullCopyGroupRelation(
            StorageSystem storage, URI sourceVolume, URI fullCopy,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRestoreFromGroupClone(StorageSystem storageSystem,
            List<URI> cloneVolume, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doActivateGroupFullCopy(StorageSystem storageSystem,
            List<URI> fullCopy, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doResyncGroupClone(StorageSystem storageDevice,
            List<URI> clone, TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.EMPTY_MAP;
    }

    @Override
    public void doFractureGroupClone(StorageSystem storageDevice,
            List<URI> clone, TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doResyncSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doCreateGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, Boolean createInactive,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doFractureGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, Boolean sync, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, Boolean deleteGroup, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doResumeGroupNativeContinuousCopies(StorageSystem storage,
            List<URI> mirrorList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveMirrorFromDeviceMaskingGroup(StorageSystem system,
            List<URI> mirrors, TaskCompleter completer)
                    throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateListReplica(StorageSystem storage, List<URI> replicaList, /* String repGroupoName, */Boolean createInactive,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachListReplica(StorageSystem storage, List<URI> replicaList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doFractureListReplica(StorageSystem storage, List<URI> replicaList, Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteListReplica(StorageSystem storage, List<URI> replicaList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doUntagVolumes(StorageSystem storageSystem, String opId, List<Volume> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        // If this operation is unsupported by default it's not necessarily an error
        return;
    }

    // file mirror related operations
    @Override
    public void doCreateMirror(StorageSystem storage, URI mirror,
            Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public void doCreateSnapshotSession(StorageSystem system, URI snapSessionURI, String groupName, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doLinkBlockSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapShotURI,
            String copyMode, Boolean targetExists, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doLinkBlockSnapshotSessionTargetGroup(StorageSystem system, URI snapshotSessionURI, List<URI> snapSessionSnapshotURIs,
            String copyMode, Boolean targetsExist, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doRelinkBlockSnapshotSessionTarget(StorageSystem system, URI tgtSnapSessionURI, URI snapshotURI,
            TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doUnlinkBlockSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            Boolean deleteTarget, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doRestoreBlockSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doDeleteBlockSnapshotSession(StorageSystem system, URI snapSessionURI, String groupName, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doAddSnapshotSessionsToConsistencyGroup(StorageSystem storageSystem, URI consistencyGroup, List<URI> addVolumesList,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public Map<URI, List<Integer>> doFindHostHLUs(StorageSystem storage, Collection<URI> initiatorURIs) throws DeviceControllerException {
        logger.error("This method is not implemented");
        return null;
    }

    @Override
    public Map<String, List<URI>> groupVolumesByStorageGroupWithHostIOLimit(StorageSystem storage, Set<URI> volumeURIs)
            throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doInitiatorAliasSet(StorageSystem storage, Initiator initiator, String initiatorAlias) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public String doInitiatorAliasGet(StorageSystem storage, Initiator initiator) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    @Override
    public void doExportAddPaths(StorageSystem storage, URI exportMask, Map<URI, List<URI>>addedPaths, 
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    @Override
    public void doExportRemovePaths(StorageSystem storage, URI exportMask, Map<URI, List<URI>> adjustedPaths, Map<URI, List<URI>>removedPaths, 
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateStoragePortGroup(StorageSystem storage, URI portGroupURI, TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteStoragePortGroup(StorageSystem storage, URI portGroupURI, TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    @Override
    public void doExportChangePortGroupAddPaths(StorageSystem storage, URI newMaskURI, URI oldMaskURI, URI portGroupURI, 
             TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    @Override
    public void doExportChangePortGroupRemovePaths(StorageSystem storage, URI oldMaskURI, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    @Override
    public void rollbackChangePortGroupRemovePaths(StorageSystem storage, URI exportGroupURI, URI oldMaskURI, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void refreshPortGroup(URI portGroupURI) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doExpandSnapshot(StorageSystem storage, StoragePool pool, BlockSnapshot snapshot, Long size, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

}
