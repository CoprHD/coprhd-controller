/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.smis;

import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.MetaVolumeOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisAbstractCreateVolumeJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateMetaVolumeHeadJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateMetaVolumeJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateMetaVolumeMembersJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateMultiVolumeJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisVolumeExpandJob;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class SmisMetaVolumeOperations implements MetaVolumeOperations {

    // Max retries for RP expand operation
    private static final int MAX_RP_EXPAND_RETRIES = 20;
    // Wait 15 seconds before attempting another call to delete the RP associated XIO volume
    private static final int RP_EXPAND_WAIT_FOR_RETRY = 5000;

    private static final String EMC_IS_BOUND = SmisConstants.CP_EMC_IS_BOUND;
    private static final Logger _log = LoggerFactory.getLogger(MetaVolumeOperations.class);
    protected DbClient _dbClient;
    protected SmisCommandHelper _helper;
    protected CIMObjectPathFactory _cimPath;
    protected NameGenerator _nameGenerator;
    private SmisStorageDevicePreProcessor _smisStorageDevicePreProcessor;
    private ControllerLockingService _locker;

    public void setLocker(ControllerLockingService locker) {
        this._locker = locker;
    }

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setSmisCommandHelper(SmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    /**
     * Create meta volume head device. Meta volume is represented by its head.
     * We create it as a regular bound volume.
     *
     * @param storageSystem
     * @param storagePool
     * @param metaHead
     * @param capacity
     * @param capabilities
     * @param metaVolumeTaskCompleter
     * @throws Exception
     */
    @Override
    public void createMetaVolumeHead(StorageSystem storageSystem, StoragePool storagePool, Volume metaHead, long capacity,
            VirtualPoolCapabilityValuesWrapper capabilities, MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws Exception {
        String label;
        _log.info(String.format(
                "Create Meta Volume Head Start - Array: %s, Pool: %s, %n   Head: %s, IsThinlyProvisioned: %s, Capacity: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeId(), metaHead.getLabel(), metaHead.getThinlyProvisioned(), capacity));

        String tenantName = "";
        try {
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, metaHead.getTenant().getURI());
            tenantName = tenant.getLabel();
        } catch (DatabaseException e) {
            _log.error("Error lookup TenantOrb object", e);
        }
        label = _nameGenerator.generate(tenantName, metaHead.getLabel(), metaHead.getId().toString(),
                '-', SmisConstants.MAX_VOLUME_NAME_LENGTH);

        boolean isThinlyProvisioned = metaHead.getThinlyProvisioned();
        // Thin stripe meta heads should be created unbound from pool on VMAX
        // Thin concatenated meta heads are created unbound from pool on vmax as well.
        // This is done to preallocate capacity later when meta volume is bound to pool.
        boolean isBoundToPool = !(isThinlyProvisioned &&
                DiscoveredDataObject.Type.vmax.toString().equalsIgnoreCase(storageSystem.getSystemType()));
        try {
            CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storageSystem);

            CIMArgument[] inArgs;
            // Prepare parameters and call method to create meta head
            // only for vnxBlock, we need to associate StoragePool Setting as Goal
            if (DiscoveredDataObject.Type.vnxblock.toString().equalsIgnoreCase(
                    storageSystem.getSystemType())) {
                inArgs = _helper.getCreateVolumesInputArgumentsOnFastEnabledPool(storageSystem, storagePool,
                        label, capacity, 1, isThinlyProvisioned, capabilities.getAutoTierPolicyName());
            } else {
                inArgs = _helper.getCreateVolumesInputArguments(storageSystem, storagePool, label, capacity, 1, isThinlyProvisioned, null,
                        isBoundToPool);
            }

            CIMArgument[] outArgs = new CIMArgument[5];
            StorageSystem forProvider = _helper.getStorageSystemForProvider(storageSystem, metaHead);
            _log.info("Selected Provider : {}", forProvider.getNativeGuid());
            SmisCreateMetaVolumeHeadJob smisJobCompleter =
                    new SmisCreateMetaVolumeHeadJob(null, forProvider.getId(), metaVolumeTaskCompleter, metaHead.getId());

            _helper.invokeMethodSynchronously(forProvider, configSvcPath,
                    _helper.createVolumesMethodName(forProvider), inArgs,
                    outArgs, smisJobCompleter);
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        } catch (Exception e) {
            _log.error("Problem in createMetaVolumeHead: " + metaHead.getLabel(), e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createMetaVolumeHead", e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        } finally {
            _log.info(String.format(
                    "Create Meta Volume Head End - Array:%s, Pool: %s, %n   Head: %s",
                    storageSystem.getSerialNumber(), storagePool.getNativeId(), metaHead.getLabel()));
        }
    }

    /**
     * Create meta volume member devices. These devices provide capacity to meta volume.
     * SMI-S requires that these devices be created unbound form a pool.
     *
     * @param storageSystem
     * @param storagePool
     * @param metaHead
     * @param memberCount
     * @param memberCapacity
     * @param metaVolumeTaskCompleter
     * @return list of native ids of meta member devices
     * @throws Exception
     */
    @Override
    public List<String> createMetaVolumeMembers(StorageSystem storageSystem, StoragePool storagePool, Volume metaHead, int memberCount,
            long memberCapacity, MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws Exception {
        _log.info(String.format(
                "Create Meta Volume Members Start - Array: %s, Pool: %s, %n   Volume: %s, Count:%s, Member capacity: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeId(), metaHead.getLabel(), memberCount, memberCapacity));

        try {
            boolean isThinlyProvisioned = metaHead.getThinlyProvisioned();

            CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storageSystem);

            CIMArgument[] inArgs;

            // Prepare parameters and call method to create meta members
            inArgs = _helper.getCreateMetaVolumeMembersInputArguments(storageSystem, storagePool,
                    memberCount, memberCapacity, isThinlyProvisioned);
            CIMArgument[] outArgs = new CIMArgument[5];
            StorageSystem forProvider = _helper.getStorageSystemForProvider(storageSystem, metaHead);
            _log.info("Selected Provider : {}", forProvider.getNativeGuid());
            SmisCreateMetaVolumeMembersJob smisJobCompleter =
                    new SmisCreateMetaVolumeMembersJob(null, forProvider.getId(), metaHead, memberCount, metaVolumeTaskCompleter);

            _helper.invokeMethodSynchronously(forProvider, configSvcPath,
                    SmisConstants.CREATE_OR_MODIFY_ELEMENT_FROM_STORAGE_POOL, inArgs,
                    outArgs, smisJobCompleter);

            return smisJobCompleter.getMetaMembers();
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        } catch (Exception e) {
            _log.error("Problem in createMetaVolumeMembers: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createMetaVolumeMemebers", e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        } finally {
            _log.info(String.format(
                    "Create Meta Volume Members End - Array: %s, Pool: %s, %n   Volume: %s",
                    storageSystem.getSerialNumber(), storagePool.getNativeId(), metaHead.getLabel()));
        }
    }

    /**
     * Create meta volume from provided meta head and meta members
     *
     * @param storageSystem storageSystem
     * @param metaHead meta head
     * @param metaMembers list of native ids of meta volume members (not including meta head)
     * @param metaType meta volume type to create, concatenate or stripe
     * @param capabilities capabilities
     * @param metaVolumeTaskCompleter task completer
     */
    @Override
    public void createMetaVolume(StorageSystem storageSystem, StoragePool storagePool, Volume metaHead, List<String> metaMembers,
            String metaType,
            VirtualPoolCapabilityValuesWrapper capabilities, MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws Exception {

        String label = null;

        label = metaHead.getLabel();
        try {
            CIMObjectPath elementCompositionServicePath = _cimPath.getElementCompositionSvcPath(storageSystem);

            // Check if meta head is bound to pool. The binding state is not changed by create meta volume call below, so we can know in
            // advance if we need
            // to bind element after this call completes.
            CIMInstance cimVolume = null;
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageSystem, metaHead);
            cimVolume = _helper.getInstance(storageSystem, volumePath, false,
                    false, new String[] { EMC_IS_BOUND });
            String isBoundStr = cimVolume.getPropertyValue(EMC_IS_BOUND).toString();
            Boolean isBound = Boolean.parseBoolean(isBoundStr);

            // When isBound is true, create meta volume job is the last job in meta volume create sequence and we can complete this task.
            // Otherwise, we can complete this task only after binding is executed.
            Boolean isLastJob = isBound;

            _log.info(String.format(
                    "Create Meta Volume Start - Array: %s, Head: %s, Type: %s %n   Members:%s, isLastJob: %s",
                    storageSystem.getSerialNumber(), metaHead.getLabel(), metaType, metaMembers, isLastJob));

            CIMArgument[] inArgs;
            // Should not change meta head binding state.
            inArgs = _helper.getCreateMetaVolumeInputArguments(storageSystem, label, metaHead, metaMembers, metaType, false);

            CIMArgument[] outArgs = new CIMArgument[5];

            StorageSystem forProvider = _helper.getStorageSystemForProvider(storageSystem, metaHead);
            _log.info("Selected Provider : {}", forProvider.getNativeGuid());
            SmisJob smisJobCompleter = new SmisCreateMetaVolumeJob(null, forProvider.getId(), storagePool.getId(), metaHead,
                    metaVolumeTaskCompleter, isLastJob);
            _helper.invokeMethodSynchronously(forProvider, elementCompositionServicePath,
                    SmisConstants.CREATE_OR_MODIFY_COMPOSITE_ELEMENT, inArgs,
                    outArgs, smisJobCompleter);

            // check if volume has to be bound to pool
            // thin meta heads are created unbound from pool on VMAX
            if (metaVolumeTaskCompleter.getLastStepStatus() == Job.JobStatus.SUCCESS) {
                if (!isBound) {
                    // Set thin meta volume preallocate size when thin meta is bound to pool
                    bindMetaVolumeToPool(storageSystem, storagePool, metaHead, metaVolumeTaskCompleter, true);
                }
            }
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        } catch (Exception e) {
            _log.error("Problem in createMetaVolume: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createMetaVolume", e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        }

        _log.info(String.format(
                "Create Meta Volume End - Array:%s, Head:%s, %n  Head device ID: %s, Members:%s",
                storageSystem.getSerialNumber(), metaHead.getLabel(), metaHead.getNativeId(), metaMembers));
    }

    /**
     * Create meta volumes.
     *
     * @param storageSystem storageSystem
     * @param metaHead meta head
     * @param metaMembers list of native ids of meta volume members (not including meta head)
     * @param metaType meta volume type to create, concatenate or stripe
     * @param capabilities capabilities
     * @param metaVolumeTaskCompleter task completer
     */

    /**
     * Create meta volumes
     *
     * @param storageSystem
     * @param storagePool
     * @param volumes
     * @param capabilities
     * @param taskCompleter
     * @throws Exception
     */
    @Override
    public void createMetaVolumes(StorageSystem storageSystem, StoragePool storagePool, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities, TaskCompleter taskCompleter) throws Exception {

        String label = null;
        Volume volume = volumes.get(0);
        // We don't need a label when we are to create more than
        // one volume. In fact we can't set the label in this
        // case for VMAX, else the request will fail.
        // TODO there is a bug in smis --- the request to element composition service fails when name is set even for a single volume.
        // todo: the opt 450103 was opened on 05/30

        Long capacity = volume.getTotalMetaMemberCapacity();
        Integer metaMemberCount = volume.getMetaMemberCount();
        String metaVolumeType = volume.getCompositionType();

        boolean opCreationFailed = false;
        try {
            CIMObjectPath elementCompositionServicePath = _cimPath.getElementCompositionSvcPath(storageSystem);

            boolean isThinlyProvisioned = volume.getThinlyProvisioned();

            _log.info(String.format(
                    "Create Meta Volumes Start - Array: %s, Count: %s, MetaType: %s",
                    storageSystem.getSerialNumber(), volumes.size(), metaVolumeType));

            CIMArgument[] inArgs;
            CIMInstance poolSetting = null;

            // set preallocate size if needed
            if (isThinlyProvisioned && volume.getThinVolumePreAllocationSize() > 0) {
                poolSetting = _smisStorageDevicePreProcessor.createStoragePoolSetting(
                        storageSystem, storagePool, volume.getThinVolumePreAllocationSize());
            }
            inArgs = _helper.getCreateMetaVolumesInputArguments(storageSystem, storagePool, label,
                    capacity, volumes.size(), isThinlyProvisioned, metaVolumeType, metaMemberCount, poolSetting);

            CIMArgument[] outArgs = new CIMArgument[5];

            StorageSystem forProvider = _helper.getStorageSystemForProvider(storageSystem, volume);
            _log.info("Selected Provider : {}", forProvider.getNativeGuid());

            // can not invoke async --- cimPath is not serializable
            // todo: before opt 450103 is fixed always use multi-volume job
            SmisAbstractCreateVolumeJob smisJobCompleter = new SmisCreateMultiVolumeJob(null,
                    forProvider.getId(), storagePool.getId(), volumes.size(), taskCompleter);
            // SmisAbstractCreateVolumeJob smisJobCompleter = volumes.size() > 1 ? new SmisCreateMultiVolumeJob(null,
            // forProvider.getId(), storagePool.getId(), volumes.size(), taskCompleter)
            // : new SmisCreateVolumeJob(null, forProvider.getId(), storagePool.getId(),
            // taskCompleter);
            smisJobCompleter.setCimPath(_cimPath);
            _helper.invokeMethodSynchronously(forProvider, elementCompositionServicePath,
                    SmisConstants.CREATE_OR_MODIFY_COMPOSITE_ELEMENT, inArgs,
                    outArgs, smisJobCompleter);
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            opCreationFailed = true;
            ServiceError serviceError = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        } catch (Exception e) {
            _log.error("Problem in createMetaVolumes: ", e);
            opCreationFailed = true;
            ServiceError serviceError = DeviceControllerErrors.smis.methodFailed("createMetaVolumes", e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        }
        if (opCreationFailed) {
            for (Volume vol : volumes) {
                vol.setInactive(true);
                _dbClient.persistObject(vol);
            }
        }

        StringBuilder logMsgBuilder = new StringBuilder(String.format("Create meta volumes End - Array:%s, Pool:%s",
                storageSystem.getSerialNumber(), storagePool.getNativeGuid()));
        for (Volume vol : volumes) {
            logMsgBuilder.append(String.format("%nVolume:%s", vol.getLabel()));
        }
        _log.info(logMsgBuilder.toString());

    }

    /**
     * Expand regular volume as a meta volume.
     *
     * @param storageSystem
     * @param metaHead
     * @param metaMembers
     * @param metaType
     * @param metaVolumeTaskCompleter
     * @throws DeviceControllerException
     */
    @Override
    public void expandVolumeAsMetaVolume(StorageSystem storageSystem, StoragePool storagePool, Volume metaHead, List<String> metaMembers,
            String metaType,
            MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws DeviceControllerException {

        String label = null;

        _log.info(String.format(
                "Expand Volume as  Meta Volume Start - Array: %s, Head: %s, Recommended meta type: %s %n   Members:%s",
                storageSystem.getSerialNumber(), metaHead.getLabel(), metaType, metaMembers));

        label = metaHead.getLabel();
        boolean isRPVolume = false;

        if (metaHead != null) {
            // A volume is of type RP if the volume has an RP copy name or it's a VPlex backing volume associated to a
            // VPlex RP source volume.
            isRPVolume = metaHead.checkForRp() || RPHelper.isAssociatedToAnyRpVplexTypes(metaHead, _dbClient);
        }

        // initialize the retry/attempt variables
        int attempt = 0;
        int retries = 1;

        if (isRPVolume) {
            // if we are dealing with an RP volume, we need to set the retry count appropriately
            retries = MAX_RP_EXPAND_RETRIES;
        }

        // Execute one-to-many expand attempts depending on if this is an RP volume or not. If the
        // volume is RP, retry if we get the "The requested device has active sessions" error. This is
        // because RP has issued an asynchronous call to the array to terminate the active session but it
        // has not been received or processed yet.
        while (attempt++ <= retries) {
            try {
                CIMObjectPath elementCompositionServicePath = _cimPath.getElementCompositionSvcPath(storageSystem);

                CIMArgument[] inArgs;
                inArgs = _helper.getCreateMetaVolumeInputArguments(storageSystem, label, metaHead, metaMembers, metaType, true);

                CIMArgument[] outArgs = new CIMArgument[5];
                // TODO evaluate use of asunc call for the last operation in extend sequence
                // _helper.invokeMethod(storageSystem, elementCompositionServicePath, SmisConstants.CREATE_OR_MODIFY_COMPOSITE_ELEMENT,
                // inArgs,
                // outArgs);
                // CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
                // if (job != null) {
                // ControllerServiceImpl.enqueueJob(new QueueJob(new SmisVolumeExpandJob(job, storageSystem.getId(),
                // taskCompleter, "ExpandAsMetaVolume")));
                // }
                //
                StorageSystem forProvider = _helper.getStorageSystemForProvider(storageSystem, metaHead);
                _log.info("Selected Provider : {}", forProvider.getNativeGuid());
                SmisJob smisJobCompleter = new SmisVolumeExpandJob(null, forProvider.getId(), storagePool.getId(),
                        metaVolumeTaskCompleter, "ExpandAsMetaVolume");

                if (metaHead.checkForRp()) {
                    _log.info(String.format("Attempt %s/%s to expand volume %s, which is associated with RecoverPoint", attempt,
                            MAX_RP_EXPAND_RETRIES, metaHead.getLabel()));
                }

                _helper.invokeMethodSynchronously(forProvider, elementCompositionServicePath,
                        SmisConstants.CREATE_OR_MODIFY_COMPOSITE_ELEMENT, inArgs,
                        outArgs, smisJobCompleter);

                // No exceptions so break out of the retry loop
                break;
            } catch (WBEMException e) {
                _log.error("Problem making SMI-S call: ", e);
                ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
                metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, _locker, error);
            } catch (Exception e) {
                if (attempt != retries && isRPVolume && e.getMessage().contains("The requested device has active sessions")) {
                    // RP has issued an async request to terminate the active session so we just need to wait
                    // and retry the expand.
                    _log.warn(String
                            .format("Encountered exception attempting to expand RP volume %s.  Waiting %s milliseconds before trying again.  Error: %s",
                                    metaHead.getLabel(), RP_EXPAND_WAIT_FOR_RETRY, e.getMessage()));
                    try {
                        Thread.sleep(RP_EXPAND_WAIT_FOR_RETRY);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    _log.error("Problem in expandVolumeAsMetaVolume: ", e);
                    ServiceError error = DeviceControllerErrors.smis.methodFailed("expandVolumeAsMetaVolume", e.getMessage());
                    metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, _locker, error);
                    // Break out of the retry loop
                    break;
                }
            }
        }

        _log.info(String.format(
                "Expand Volume as  Meta Volume End - Array:%s, Head:%s, %n  Head device ID: %s, Members:%s",
                storageSystem.getSerialNumber(), metaHead.getLabel(), metaHead.getNativeId(), metaMembers));
    }

    /**
     * Expand meta volume.
     *
     * @param storageSystem
     * @param metaHead
     * @param newMetaMembers
     * @param metaVolumeTaskCompleter
     * @throws DeviceControllerException
     */
    @Override
    public void expandMetaVolume(StorageSystem storageSystem, StoragePool storagePool,
            Volume metaHead, List<String> newMetaMembers, MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws DeviceControllerException {

        _log.info(String.format(
                "Expand Meta Volume Start - Array: %s, Head: %s, %n   New members:%s",
                storageSystem.getSerialNumber(), metaHead.getLabel(), newMetaMembers));

        boolean isRPVolume = false;

        if (metaHead != null) {
            // A volume is of type RP if the volume has an RP copy name or it's a VPlex backing volume associated to a
            // VPlex RP source volume.
            isRPVolume = metaHead.checkForRp() || RPHelper.isAssociatedToAnyRpVplexTypes(metaHead, _dbClient);
        }

        // initialize the retry/attempt variables
        int attempt = 0;
        int retries = 1;

        if (isRPVolume) {
            // if we are dealing with an RP volume, we need to set the retry count appropriately
            retries = MAX_RP_EXPAND_RETRIES;
        }

        // Execute one-to-many expand attempts depending on if this is an RP volume or not. If the
        // volume is RP, retry if we get the "The requested device has active sessions" error. This is
        // because RP has issued an asynchronous call to the array to terminate the active session but it
        // has not been received or processed yet.
        while (attempt++ <= retries) {
            try {
                CIMObjectPath elementCompositionServicePath = _cimPath.getElementCompositionSvcPath(storageSystem);

                CIMArgument[] inArgs;
                inArgs = _helper.getExpandMetaVolumeInputArguments(storageSystem, metaHead, newMetaMembers);

                CIMArgument[] outArgs = new CIMArgument[5];
                // TODO evaluate use of asunc call for the last operation in extend sequence
                // _helper.invokeMethod(storageSystem, elementCompositionServicePath, SmisConstants.CREATE_OR_MODIFY_COMPOSITE_ELEMENT,
                // inArgs,
                // outArgs);
                // CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
                // if (job != null) {
                // ControllerServiceImpl.enqueueJob(new QueueJob(new SmisVolumeExpandJob(job, storageSystem.getId(),
                // taskCompleter, "ExpandMetaVolume")));
                // }
                StorageSystem forProvider = _helper.getStorageSystemForProvider(storageSystem, metaHead);
                _log.info("Selected Provider : {}", forProvider.getNativeGuid());
                SmisJob smisJobCompleter = new SmisVolumeExpandJob(null, forProvider.getId(), storagePool.getId(),
                        metaVolumeTaskCompleter, "ExpandMetaVolume");

                if (metaHead.checkForRp()) {
                    _log.info(String.format("Attempt %s/%s to expand volume %s, which is associated with RecoverPoint", attempt,
                            MAX_RP_EXPAND_RETRIES, metaHead.getLabel()));
                }

                _helper.invokeMethodSynchronously(forProvider, elementCompositionServicePath,
                        SmisConstants.CREATE_OR_MODIFY_COMPOSITE_ELEMENT, inArgs,
                        outArgs, smisJobCompleter);

                // No exceptions so break out of the retry loop
                break;
            } catch (WBEMException e) {
                _log.error("Problem making SMI-S call: ", e);
                ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
                metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, _locker, error);
            } catch (Exception e) {
                if (attempt != retries && isRPVolume && e.getMessage().contains("The requested device has active sessions")) {
                    // RP has issued an async request to terminate the active session so we just need to wait
                    // and retry the expand.
                    _log.warn(String
                            .format("Encountered exception attempting to expand RP volume %s.  Waiting %s milliseconds before trying again.  Error: %s",
                                    metaHead.getLabel(), RP_EXPAND_WAIT_FOR_RETRY, e.getMessage()));
                    try {
                        Thread.sleep(RP_EXPAND_WAIT_FOR_RETRY);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    _log.error("Problem in expandMetaVolume: ", e);
                    ServiceError error = DeviceControllerErrors.smis.methodFailed("expandVolume", e.getMessage());
                    metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, _locker, error);
                    // Break out of the retry loop
                    break;
                }
            }
        }

        _log.info(String.format(
                "Expand Meta Volume End - Array:%s, Head:%s, %n  Head device ID: %s, New members:%s",
                storageSystem.getSerialNumber(), metaHead.getLabel(), metaHead.getNativeId(), newMetaMembers));
    }

    private void bindMetaVolumeToPool(StorageSystem storageSystem, StoragePool storagePool, Volume volume,
            MetaVolumeTaskCompleter metaVolumeTaskCompleter, Boolean isLastJob) throws Exception {

        long thinMetaVolumePreAllocateSize = 0;
        if (volume.getThinVolumePreAllocationSize() != null && volume.getThinVolumePreAllocationSize() > 0) {
            thinMetaVolumePreAllocateSize = volume.getThinVolumePreAllocationSize();
        }

        _log.info(String.format(
                "Bind Meta Volume to Pool Start - Array: %s, Pool: %s, %n   Volume: %s, ThinMetaVolumePreAllocateSize: %s, isLastJob: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeId(), volume.getNativeId(), thinMetaVolumePreAllocateSize, isLastJob));

        try {
            CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storageSystem);

            CIMArgument[] inArgs;
            // Prepare parameters and call method to bind meta head
            inArgs = _helper.getBindVolumeInputArguments(storageSystem, storagePool, volume, thinMetaVolumePreAllocateSize);

            CIMArgument[] outArgs = new CIMArgument[5];
            // SmisJob smisJobCompleter =
            // new SmisJob(null, storageSystem.getId(), metaVolumeTaskCompleter.getVolumeTaskCompleter(), "Bind volume to pool job");
            StorageSystem forProvider = _helper.getStorageSystemForProvider(storageSystem, volume);
            _log.info("Selected Provider : {}", forProvider.getNativeGuid());
            SmisJob smisJobCompleter = new SmisCreateMetaVolumeJob(null, forProvider.getId(), storagePool.getId(), volume,
                    metaVolumeTaskCompleter, isLastJob);

            _helper.invokeMethodSynchronously(forProvider, configSvcPath,
                    SmisConstants.EMC_BIND_ELEMENT, inArgs,
                    outArgs, smisJobCompleter);
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        } catch (Exception e) {
            _log.error("Problem in bindVolumeToPool: " + volume.getLabel(), e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        } finally {
            _log.info(String.format(
                    "Bind Meta Volume to Pool End - Array:%s, Pool: %s, %n   Volume: %s",
                    storageSystem.getSerialNumber(), storagePool.getNativeId(), volume.getNativeId()));
        }
    }

    /**
     * Deletes 'SMI_BCV_META_...' helper volume form array.
     *
     * @param storageSystem
     * @param volume
     * @throws Exception
     */
    @Override
    public void deleteBCVHelperVolume(StorageSystem storageSystem, Volume volume) throws Exception {

        _log.info(String.format("Start executing BCV helper volume from array: %s, for volume: %s",
                storageSystem.getId(), volume.getId()));
        try {
            // Find BCV volume instance based on volume device name.
            String deviceName = volume.getNativeId();

            // Temporary before OPT: is fixed ---
            // try to find volume based on device id with removed leading zeros, if failed try complete device id.
            String deviceNameWithoutLeadingZeros = deviceName.replaceAll("^0*", "");
            String query = String
                    .format("SELECT CIM_StorageVolume.%s, CIM_StorageVolume.%s  FROM CIM_StorageVolume where CIM_StorageVolume.%s ='SMI_BCV_META_%s'",
                            SmisConstants.CP_ELEMENT_NAME, SmisConstants.CP_DEVICE_ID, SmisConstants.CP_ELEMENT_NAME,
                            deviceNameWithoutLeadingZeros);
            String queryLanguage = "CQL";
            List<CIMInstance> bcvVolumeInstanceList = _helper.executeQuery(storageSystem, query, queryLanguage);
            if (bcvVolumeInstanceList == null || bcvVolumeInstanceList.isEmpty()) {
                // Execute query for unmodified device name
                query = String
                        .format("SELECT CIM_StorageVolume.%s, CIM_StorageVolume.%s FROM CIM_StorageVolume where CIM_StorageVolume.%s ='SMI_BCV_META_%s'",
                                SmisConstants.CP_ELEMENT_NAME, SmisConstants.CP_DEVICE_ID, SmisConstants.CP_ELEMENT_NAME, deviceName);
                bcvVolumeInstanceList = _helper.executeQuery(storageSystem, query, queryLanguage);
            }

            String elementName = null;
            String nativeId = null;
            CIMInstance bcvVolumeInstance = null;
            if (bcvVolumeInstanceList != null && !bcvVolumeInstanceList.isEmpty()) {
                bcvVolumeInstance = bcvVolumeInstanceList.get(0);
                elementName = CIMPropertyFactory.getPropertyValue(bcvVolumeInstance, SmisConstants.CP_ELEMENT_NAME);
                nativeId = CIMPropertyFactory.getPropertyValue(bcvVolumeInstance, SmisConstants.CP_DEVICE_ID);
                _log.info(String.format("Found BCV helper volume: %s, nativeId: %s", elementName, nativeId));
            } else {
                _log.warn(String.format("Could not find BCV helper volume for volume: %s, nativeId: %s", volume.getId(),
                        volume.getNativeId()));
                return;
            }

            // Delete BCV volume from array
            _log.info(String.format("Executing delete of BCV helper volume: " + nativeId));
            String[] nativeIds = new String[] { nativeId };
            // Prepare parameters and call method to delete meta members from array
            CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storageSystem);
            CIMArgument[] inArgs = _helper.getDeleteVolumesInputArguments(storageSystem, nativeIds);
            CIMArgument[] outArgs = new CIMArgument[5];
            String returnElementsMethod;
            if (storageSystem.getUsingSmis80()) {
                returnElementsMethod = SmisConstants.RETURN_ELEMENTS_TO_STORAGE_POOL;
            } else {
                returnElementsMethod = SmisConstants.EMC_RETURN_TO_STORAGE_POOL;
            }
            _helper.invokeMethodSynchronously(storageSystem, configSvcPath,
                    returnElementsMethod, inArgs, outArgs,
                    null);

            _log.info(String.format("Deleted BCV helper volume: " + nativeId));

        } catch (Exception ex) {
            _log.error(String.format("Failed to delete BCV helper volume from array: %s, for volume: %s",
                    storageSystem.getId(), volume.getId()));
        }
    }

    @Override
    public String defineExpansionType(StorageSystem storageSystem, Volume volume, String recommendedMetaVolumeType,
            MetaVolumeTaskCompleter metaVolumeTaskCompleter)
            throws Exception {
        String expansionType = null;
        Boolean isBound = null;
        try {
            CIMInstance cimVolume = null;
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageSystem, volume);
            cimVolume = _helper.getInstance(storageSystem, volumePath, false,
                    false, new String[] { EMC_IS_BOUND });
            String isBoundStr = cimVolume.getPropertyValue(EMC_IS_BOUND).toString();
            isBound = Boolean.parseBoolean(isBoundStr);
            String deviceType = storageSystem.getSystemType();

            // If a volume is composite volume, use its meta type,
            // otherwise for vmax bound regular volumes always use concatenated type for expansion.
            // The reason: not all microcode versions allows to form striped meta volumes with bound meta head.
            // See Notes in smis provider guide 4.6.1 p. 424
            expansionType = recommendedMetaVolumeType;
            if (volume.getIsComposite()) {
                expansionType = volume.getCompositionType();
            } else if (deviceType.equals(StorageSystem.Type.vmax.toString()) && isBound) {
                expansionType = Volume.CompositionType.CONCATENATED.toString();
            }

            return expansionType;
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        } catch (Exception e) {
            _log.error("Problem in defineExpansionType: " + volume.getLabel(), e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("defineExpansionType", e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(_dbClient, error);
            throw e;
        } finally {
            _log.info(String.format(
                    "defineExpansionType End -  Volume: %s, IsMeta: %s, isBound: %s, " +
                            "\n Array:%s, Array type: %s,  Meta type for expansion: %s",
                    volume.getNativeId(), volume.getIsComposite(), isBound, storageSystem.getSerialNumber(),
                    storageSystem.getSystemType(), expansionType));
        }
    }

    public void setSmisStorageDevicePreProcessor(
            final SmisStorageDevicePreProcessor smisStorageDevicePreProcessor) {
        _smisStorageDevicePreProcessor = smisStorageDevicePreProcessor;
    }

}
