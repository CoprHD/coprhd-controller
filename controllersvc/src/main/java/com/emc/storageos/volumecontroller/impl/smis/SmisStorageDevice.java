/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_INSTANCE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_REPLICATION_GROUP;
import static java.text.MessageFormat.format;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger16;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.MetaVolumeOperations;
import com.emc.storageos.volumecontroller.ReplicaOperations;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MultiVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.providerfinders.FindProviderFactory;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCleanupMetaVolumeMembersJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateMultiVolumeJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateVolumeJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisDeleteVolumeJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisVolumeExpandJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisWaitForGroupSynchronizedJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisWaitForSynchronizedJob;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Joiner;

/**
 * SMI-S specific block controller implementation.
 */
public class SmisStorageDevice extends DefaultBlockStorageDevice {
    private static final Logger _log = LoggerFactory.getLogger(SmisStorageDevice.class);
    private static final BiosCommandResult _ok = new BiosCommandResult(true,
            Operation.Status.ready.name(), "");
    private static final BiosCommandResult _err = new BiosCommandResult(false,
            Operation.Status.error.name(), "");
    private DbClient _dbClient;
    protected SmisCommandHelper _helper;
    private ExportMaskOperations _exportMaskOperationsHelper;
    private CIMObjectPathFactory _cimPath;
    private SnapshotOperations _snapshotOperations;
    private MirrorOperations _mirrorOperations;
    private CloneOperations _cloneOperations;
    private ReplicaOperations _replicaOperations;
    private NameGenerator _nameGenerator;
    private MetaVolumeOperations _metaVolumeOperations;
    private SRDFOperations _srdfOperations;
    private SmisStorageDevicePreProcessor _smisStorageDevicePreProcessor;
    private FindProviderFactory findProviderFactory;
    private ControllerLockingService _locker;

    public void setLocker(final ControllerLockingService locker) {
        this._locker = locker;
    }

    public void setCimObjectPathFactory(final CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(final DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setSmisCommandHelper(final SmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setExportMaskOperationsHelper(final ExportMaskOperations exportMaskOperationsHelper) {
        _exportMaskOperationsHelper = exportMaskOperationsHelper;
    }

    public void setSnapshotOperations(final SnapshotOperations snapshotOperations) {
        _snapshotOperations = snapshotOperations;
    }

    public void setMirrorOperations(final MirrorOperations mirrorOperations) {
        _mirrorOperations = mirrorOperations;
    }

    public void setCloneOperations(final CloneOperations cloneOperations) {
        _cloneOperations = cloneOperations;
    }

    public void setReplicaOperations(final ReplicaOperations replicaOperations) {
        _replicaOperations = replicaOperations;
    }

    public void setNameGenerator(final NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    public void setMetaVolumeOperations(final MetaVolumeOperations metaVolumeOperations) {
        _metaVolumeOperations = metaVolumeOperations;
    }

    public void setSrdfOperations(final SRDFOperations srdfOperations) {
        _srdfOperations = srdfOperations;
    }

    public void setSmisStorageDevicePreProcessor(
            final SmisStorageDevicePreProcessor smisStorageDevicePreProcessor) {
        _smisStorageDevicePreProcessor = smisStorageDevicePreProcessor;
    }

    public void setFindProviderFactory(final FindProviderFactory findProviderFactory) {
        this.findProviderFactory = findProviderFactory;
    }

    @Override
    public void doCreateVolumes(final StorageSystem storageSystem, final StoragePool storagePool,
            final String opId, final List<Volume> volumes,
            final VirtualPoolCapabilityValuesWrapper capabilities, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        String label = null;
        Long capacity = null;
        Long thinVolumePreAllocationSize = null;
        CIMInstance poolSetting = null;
        boolean opCreationFailed = false;
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create Volume Start - Array:%s, Pool:%s", storageSystem.getSerialNumber(),
                storagePool.getNativeGuid()));
        // volumeGroupObjectPath is required for VMAX3
        CIMObjectPath volumeGroupObjectPath = _helper.getVolumeGroupPath(storageSystem, volumes.get(0), storagePool);
        List<String> volumeLabels = new ArrayList<>();

        for (Volume volume : volumes) {
            logMsgBuilder.append(String.format("%nVolume:%s , IsThinlyProvisioned: %s",
                    volume.getLabel(), volume.getThinlyProvisioned()));

            String tenantName = "";
            try {
                TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, volume.getTenant()
                        .getURI());
                tenantName = tenant.getLabel();
            } catch (DatabaseException e) {
                _log.error("Error lookup TenantOrb object", e);
            }
            label = _nameGenerator.generate(tenantName, volume.getLabel(), volume.getId()
                    .toString(), '-', SmisConstants.MAX_VOLUME_NAME_LENGTH);
            volumeLabels.add(label);

            if (capacity == null) {
                capacity = volume.getCapacity();
            }
            if (thinVolumePreAllocationSize == null && volume.getThinVolumePreAllocationSize() > 0) {
                thinVolumePreAllocationSize = volume.getThinVolumePreAllocationSize();
            }
        }
        _log.info(logMsgBuilder.toString());
        boolean isThinlyProvisioned = volumes.get(0).getThinlyProvisioned();
        try {
            CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storageSystem);
            CIMArgument[] inArgs = null;
            // only for vnxBlock, we need to associate StoragePool Setting as Goal
            // I didn't find any ways to add this branching logic based on device Types.
            if (DiscoveredDataObject.Type.vnxblock.toString().equalsIgnoreCase(
                    storageSystem.getSystemType())) {
                String autoTierPolicyName = ControllerUtils.getAutoTieringPolicyName(volumes.get(0)
                        .getId(), _dbClient);
                if (autoTierPolicyName.equals(Constants.NONE)) {
                    autoTierPolicyName = null;
                }
                inArgs = _helper.getCreateVolumesInputArgumentsOnFastEnabledPool(storageSystem,
                        storagePool, volumeLabels, capacity, volumes.size(), isThinlyProvisioned,
                        autoTierPolicyName);
            } else {
                if (!storageSystem.checkIfVmax3() && isThinlyProvisioned && null != thinVolumePreAllocationSize) {
                    poolSetting = _smisStorageDevicePreProcessor.createStoragePoolSetting(
                            storageSystem, storagePool, thinVolumePreAllocationSize);
                }
                if (storageSystem.checkIfVmax3() && volumeGroupObjectPath != null) {
                    inArgs = _helper.getCreateVolumesInputArguments(storageSystem, storagePool, volumeLabels,
                            capacity, volumes.size(), isThinlyProvisioned, true, volumeGroupObjectPath,
                            (null != thinVolumePreAllocationSize));
                } else {
                    inArgs = _helper.getCreateVolumesInputArguments(storageSystem, storagePool, volumeLabels,
                            capacity, volumes.size(), isThinlyProvisioned, poolSetting, true);
                }
            }
            CIMArgument[] outArgs = new CIMArgument[5];
            StorageSystem forProvider = _helper.getStorageSystemForProvider(storageSystem, volumes.get(0));
            _helper.invokeMethod(forProvider, configSvcPath,
                    _helper.createVolumesMethodName(forProvider), inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            if (job != null) {
                SmisJob createSmisJob = volumes.size() > 1 ? new SmisCreateMultiVolumeJob(job,
                        forProvider.getId(), storagePool.getId(), volumes.size(), taskCompleter)
                        : new SmisCreateVolumeJob(job, forProvider.getId(), storagePool.getId(),
                                taskCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(createSmisJob));
            }
        } catch (final InternalException e) {
            _log.error("Problem in doCreateVolumes: ", e);
            opCreationFailed = true;
            taskCompleter.error(_dbClient, e);
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            opCreationFailed = true;
            ServiceError serviceError = DeviceControllerErrors.smis.unableToCallStorageProvider(e
                    .getMessage());
            taskCompleter.error(_dbClient, serviceError);
        } catch (Exception e) {
            _log.error("Problem in doCreateVolumes: ", e);
            opCreationFailed = true;
            ServiceError serviceError = DeviceControllerErrors.smis.methodFailed("doCreateVolumes",
                    e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        }
        if (opCreationFailed) {
            for (Volume vol : volumes) {
                vol.setInactive(true);
                _dbClient.persistObject(vol);
            }
        }
        logMsgBuilder = new StringBuilder(String.format("Create Volumes End - Array:%s, Pool:%s",
                storageSystem.getSerialNumber(), storagePool.getNativeGuid()));
        for (Volume volume : volumes) {
            logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
        }
        _log.info(logMsgBuilder.toString());
    }

    @Override
    public void doCreateMetaVolumes(final StorageSystem storageSystem,
            final StoragePool storagePool, List<Volume> volumes,
            final VirtualPoolCapabilityValuesWrapper capabilities,
            final MetaVolumeRecommendation recommendation, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create Meta Volumes Start - Array:%s, Pool:%s %n",
                storageSystem.getSerialNumber(), storagePool.getNativeId()));
        StringBuilder volumesMsg = new StringBuilder();
        for (Volume volume : volumes) {
            volumesMsg.append(String.format("   Volume: %s, id: %s %n",
                    volume.getLabel(), volume.getId()));
        }
        _log.info(logMsgBuilder.toString() + volumesMsg.toString());

        try {
            // Create meta volumes
            _metaVolumeOperations.createMetaVolumes(storageSystem, storagePool, volumes,
                    capabilities, taskCompleter);
        } catch (Exception e) {
            _log.error(
                    "Problem in doCreateMetaVolumes: failed to create meta volumes: "
                            + volumesMsg.toString(), e);
        }

        // Get updated volumes ( we need to know their nativeId)
        volumesMsg = new StringBuilder();
        for (Volume volume : volumes) {
            volume = _dbClient.queryObject(Volume.class, volume.getId());
            volumesMsg.append(String.format("   Volume: %s, id: %s, nativeID: %s",
                    volume.getLabel(), volume.getNativeId(), volume.getNativeId()));
        }
        logMsgBuilder = new StringBuilder(String.format(
                "Create Meta Volume End - Array:%s, Pool:%s%n",
                storageSystem.getSerialNumber(), storagePool.getNativeId()));
        _log.info(logMsgBuilder.toString() + volumesMsg.toString());
    }

    @Override
    public void doCreateMetaVolume(final StorageSystem storageSystem,
            final StoragePool storagePool, Volume volume,
            final VirtualPoolCapabilityValuesWrapper capabilities,
            final MetaVolumeRecommendation recommendation, final VolumeCreateCompleter taskCompleter)
            throws DeviceControllerException {
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create Meta Volume Start - Array:%s, Pool:%s %n    Volume: %s, id: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeId(), volume.getLabel(),
                volume.getId()));
        _log.info(logMsgBuilder.toString());
        boolean isThinlyProvisioned = volume.getThinlyProvisioned();
        String metaVolumeType = volume.getCompositionType();
        long metaMemberCapacity = volume.getMetaMemberSize();
        int metaMemberCount = volume.getMetaMemberCount();
        MetaVolumeTaskCompleter metaVolumeTaskCompleter = new MetaVolumeTaskCompleter(taskCompleter);
        _log.info(String.format("Start of steps to create meta volume: %s, \n   volume ID: %s"
                + "\n   type: %s, member count: %s, member size: %s. isThinlyProvisioned: %s",
                volume.getLabel(), volume.getId(), metaVolumeType, metaMemberCount,
                metaMemberCapacity, isThinlyProvisioned));

        try {
            // Step 1: Create meta volume head
            // Create meta volume head as bound to pool
            _metaVolumeOperations.createMetaVolumeHead(storageSystem, storagePool, volume,
                    metaMemberCapacity, capabilities, metaVolumeTaskCompleter);
            // Step 2: Create meta members
            // Create members as unbound to pool (SMI-S requirement)
            List<String> metaMembers = null;
            if (metaVolumeTaskCompleter.getLastStepStatus() == Job.JobStatus.SUCCESS) {
                metaMembers = _metaVolumeOperations.createMetaVolumeMembers(storageSystem,
                        storagePool, volume, metaMemberCount - 1, metaMemberCapacity,
                        metaVolumeTaskCompleter);
            }
            // Step 3: Create meta volume from the head and meta members
            if (metaVolumeTaskCompleter.getLastStepStatus() == Job.JobStatus.SUCCESS) {
                // Get updated volume ( we need to know its nativeId) which was set in Step 1.
                Volume metaHead = _dbClient.queryObject(Volume.class, volume.getId());
                _metaVolumeOperations.createMetaVolume(storageSystem, storagePool, metaHead,
                        metaMembers, metaVolumeType, capabilities, metaVolumeTaskCompleter);
            }
        } catch (Exception e) {
            _log.error(
                    "Problem in doCreateMetaVolume: failed to create meta volume "
                            + volume.getLabel() + " .", e);
        } finally {
            _log.info(String.format("End of steps to create meta volume: %s, \n   volume ID: %s"
                    + "\n   type: %s, member count: %s, member size: %s. isThinlyProvisioned: %s",
                    volume.getLabel(), volume.getId(), metaVolumeType, metaMemberCount,
                    metaMemberCapacity, isThinlyProvisioned));
        }
        // Get updated volume ( we need to know its nativeId) which was set in Step 1.
        volume = _dbClient.queryObject(Volume.class, volume.getId());
        logMsgBuilder = new StringBuilder(String.format(
                "Create Meta Volume End - Array:%s, Pool:%s%n    Volume: %s, id: %s, nativeID: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeId(), volume.getLabel(),
                volume.getId(), volume.getNativeId()));
        _log.info(logMsgBuilder.toString());
    }

    @Override
    public void doExpandAsMetaVolume(final StorageSystem storageSystem,
            final StoragePool storagePool, final Volume volume, final long size,
            final MetaVolumeRecommendation recommendation, VolumeExpandCompleter volumeCompleter) {
        // To expand a volume as meta volume we need to execute sequence of two SMI-S requests.
        // First, we need to create required number of meta members to supply capacity.
        // Second step depends if input volume is a meta volume or a regular volume.
        // If input volume is a regular volume, we need to create a new meta volume with input
        // volume as its meta head.
        // If input volume is already a meta volume, we need to add new meta members to this volume.
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Expand Meta Volume Start - Array:%s, Pool:%s %n    Volume: %s, id: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeId(), volume.getLabel(),
                volume.getId()));
        _log.info(logMsgBuilder.toString());
        String recommendedMetaVolumeType = recommendation.getMetaVolumeType().toString();
        String expansionType = null;
        long metaMemberCapacity = recommendation.getMetaMemberSize();
        int metaMemberCount = (int) recommendation.getMetaMemberCount();

        MetaVolumeTaskCompleter metaVolumeTaskCompleter = new MetaVolumeTaskCompleter(
                volumeCompleter);
        boolean canBeExpanded = false;
        try {
            _helper.doApplyRecoverPointTag(storageSystem, volume, false);

            // First of all check if we need to do cleanup of dangling meta volumes left from previous failed
            // expand attempt (may happen when rollback of expand failed due to smis connection issues -- typically cleanup
            // is done by expand rollback)
            boolean cleanupSuccess = cleanupDanglingMetaMembers(storageSystem, volume);
            if (!cleanupSuccess) {
                // Failed to cleanup dangling meta members: probably still smis issues. Do not expand at this time.
                String errorMessage = String.format("Failed to delete meta volume: %s ,  nativeId: %s . \n" +
                        " Could not cleanup dangling meta members.",
                        volume.getId(), volume.getNativeId()
                        );
                ServiceError error = DeviceControllerErrors.smis.methodFailed("doExpandAsMetaVolume", errorMessage);
                TaskCompleter taskCompleter = metaVolumeTaskCompleter.getVolumeTaskCompleter();
                taskCompleter.error(_dbClient, error);
                _log.error(String.format(errorMessage));
                return;
            }

            // Check if this is zero-capacity extension to cleanup dangling meta members.
            if (size == volume.getCapacity()) {
                // This is zero-capacity expansion executed as recovery to cleanup dangling meta members from previous expand failure
                _log.info(String.format(
                        "Zero-capacity expansion completed. Array: %s Pool:%s Volume:%s, Capacity: %s  ",
                        storageSystem.getId(), storagePool.getId(), volume.getId(), volume.getCapacity()));
                TaskCompleter taskCompleter = metaVolumeTaskCompleter.getVolumeTaskCompleter();
                taskCompleter.ready(_dbClient);
                return;
            }

            // Check if this is expansion within current total capacity of meta members
            if (recommendation.getMetaMemberCount() == 0) {
                volume.setCapacity(size);
                _dbClient.persistObject(volume);
                _log.info(String.format(
                        "Expanded volume within its total meta volume capacity (simple case) - Array: %s Pool:%s, \n" +
                                " Volume: %s, IsMetaVolume: %s, Total meta volume capacity: %s, NewSize: %s",
                        storageSystem.getId(), storagePool.getId(), volume.getId(),
                        volume.getIsComposite(), volume.getTotalMetaMemberCapacity(), volume.getCapacity()
                        ));
                TaskCompleter taskCompleter = metaVolumeTaskCompleter.getVolumeTaskCompleter();
                taskCompleter.ready(_dbClient);
                return;
            }

            // Check if we can expand volume using recommended meta volume type:
            // On VMAX striped meta can be formed only when meta head is in unbound from pool.
            // This is our assumption for now --- some ucode versions support case when meta head is bound to pool when striped meta volume
            // is formed.
            expansionType = _metaVolumeOperations.defineExpansionType(storageSystem, volume,
                    recommendedMetaVolumeType, metaVolumeTaskCompleter);
            _log.info(String
                    .format("Meta volume type used for expansion: %s, recommended meta volume type: %s", expansionType,
                            recommendedMetaVolumeType));
            // update expansion type in completer
            volumeCompleter.setMetaVolumeType(expansionType);
            _log.info(String
                    .format("Start of steps to expand volume as meta volume: %s, \n   volume ID: %s"
                            + "\n   expansion type: %s, new member count: %s, member size: %s, is already meta volume: %s .",
                            volume.getLabel(), volume.getId(), expansionType, metaMemberCount,
                            metaMemberCapacity, volume.getIsComposite()
                    ));
            // Step 1: Create new meta members
            // Create members as unbound to pool (SMI-S requirement)
            List<String> metaMembers = null;
            metaMembers = _metaVolumeOperations.createMetaVolumeMembers(storageSystem,
                    storagePool, volume, metaMemberCount, metaMemberCapacity,
                    metaVolumeTaskCompleter);
            if (metaVolumeTaskCompleter.getLastStepStatus() == Job.JobStatus.SUCCESS) {
                if (volume.getIsComposite()) {
                    // Step 2: Expand meta volume with meta members used for expansion
                    _metaVolumeOperations.expandMetaVolume(storageSystem, storagePool, volume,
                            metaMembers, metaVolumeTaskCompleter);
                    // Step 3: Delete BCV helper volume from array. Required only for vmax.
                    if (expansionType.equals(Volume.CompositionType.STRIPED.toString()) &&
                            metaVolumeTaskCompleter.getLastStepStatus() == Job.JobStatus.SUCCESS &&
                            storageSystem.getSystemType().equalsIgnoreCase(DiscoveredDataObject.Type.vmax.toString())) {
                        _metaVolumeOperations.deleteBCVHelperVolume(storageSystem, volume);

                    }
                } else {
                    // Step 2: Create meta volume from the original volume (head) and meta
                    // members used for expansion
                    _metaVolumeOperations.expandVolumeAsMetaVolume(storageSystem, storagePool,
                            volume, metaMembers, expansionType, metaVolumeTaskCompleter);
                }
            }
        } catch (Exception e) {
            _log.error(
                    "Problem in doExpandMetaVolumes: failed to expand meta volume "
                            + volume.getLabel() + " .", e
                    );
        } finally {
            _log.info(String.format(
                    "End of steps to expand volume as meta volume: %s, \n   volume ID: %s"
                            + "\n   type: %s, new member count: %s, member size: %s.",
                    volume.getLabel(), volume.getId(), expansionType, metaMemberCount,
                    metaMemberCapacity
                    ));
        }
        logMsgBuilder = new StringBuilder(String.format(
                "Expand Volume End - Array:%s, Pool:%s%n    Volume: %s, id: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeId(), volume.getLabel(),
                volume.getId()));
        _log.info(logMsgBuilder.toString());
    }

    @Override
    public void doExpandVolume(final StorageSystem storageSystem, final StoragePool pool,
            final Volume volume, final Long size, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info(String.format(
                "Expand Volume Start - Array: %s, Pool: %s, Volume: %s, New size: %d",
                storageSystem.getSerialNumber(), pool.getNativeGuid(), volume.getLabel(), size));
        MetaVolumeTaskCompleter metaVolumeTaskCompleter = new MetaVolumeTaskCompleter(
                taskCompleter);
        try {
            if (!doesStorageSystemSupportVolumeExpand(storageSystem)) {
                ServiceError error = DeviceControllerErrors.smis.volumeExpandIsNotSupported(storageSystem.getNativeGuid());
                taskCompleter.error(_dbClient, error);
                return;
            }
            _helper.doApplyRecoverPointTag(storageSystem, volume, false);
            CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storageSystem);
            CIMArgument[] inArgs = _helper.getExpandVolumeInputArguments(storageSystem, pool, volume,
                    size);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storageSystem, configSvcPath,
                    SmisConstants.CREATE_OR_MODIFY_ELEMENT_FROM_STORAGE_POOL, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisVolumeExpandJob(job, storageSystem
                        .getId(), pool.getId(), metaVolumeTaskCompleter, "ExpandVolume")));
            }
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e
                    .getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            _log.error("Problem in doExpandVolume: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doExpandVolume",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _log.info(String.format("Expand Volume End - Array: %s, Pool: %s, Volume: %s",
                storageSystem.getSerialNumber(), pool.getNativeGuid(), volume.getLabel()));
    }

    @Override
    public void doDeleteVolumes(final StorageSystem storageSystem, final String opId,
            final List<Volume> volumes, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            int volumeCount = 0;
            String[] volumeNativeIds = new String[volumes.size()];
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Delete Volume Start - Array:%s", storageSystem.getSerialNumber()));
            MultiVolumeTaskCompleter multiVolumeTaskCompleter = (MultiVolumeTaskCompleter) taskCompleter;
            Set<CIMInstance> parkingSLOStorageGroups = new HashSet<>();
            Set<Volume> cloneVolumes = new HashSet<Volume>();
            for (Volume volume : volumes) {
                logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
                if (storageSystem.checkIfVmax3()) {
                    // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
                    // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
                    // operation on these volumes would fail otherwise.
                    boolean forceFlag = ExportUtils.useEMCForceFlag(_dbClient, volume.getId());
                    CIMInstance sloStorageGroup =
                            _helper.removeVolumeFromParkingSLOStorageGroup(storageSystem, volume.getNativeId(), forceFlag);
                    if (sloStorageGroup != null) {
                        parkingSLOStorageGroups.add(sloStorageGroup);
                    }
                    _log.info("Done invoking remove volume from storage group");
                }
                // For clones, 'replicationgroupinstance' property contains the Replication Group name.
                if (volume.getReplicationGroupInstance() != null) {
                    removeVolumeFromConsistencyGroup(storageSystem, volume);
                }
                if (volume.getConsistencyGroup() != null) {
                    BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());
                    // Only perform native CG operations if the CG is not for RecoverPoint alone. If the
                    // CG is for RecoverPoint alone, there are no native array artifacts to cleanup/remove.
                    if (cg != null && cg.getTypes() != null &&
                            !(cg.getTypes().size() == 1 && cg.checkForType(BlockConsistencyGroup.Types.RP))) {
                        if (storageSystem.deviceIsType(Type.vnxblock)) {
                            cleanupAnyGroupBackupSnapshots(storageSystem, volume);
                        }
                        removeVolumeFromConsistencyGroup(storageSystem, volume);
                    }
                } else {
                    // for VMAX3, clean up unlinked snapshot session, which is possible for ingested volume
                    if (storageSystem.deviceIsType(Type.vnxblock) || storageSystem.checkIfVmax3()) {
                        cleanupAnyBackupSnapshots(storageSystem, volume);
                    }
                }
                if (storageSystem.deviceIsType(Type.vmax) && !storageSystem.checkIfVmax3()) {
                    // VMAX2 - remove volume from Storage Groups if volume is not in any MaskingView
                    // COP-16705 - Ingested non-exported Volume may be associated with FAST SG outside of ViPR. Clear them during delete.
                    _helper.removeVolumeFromStorageGroupsIfVolumeIsNotInAnyMV(storageSystem, volume);
                }
                StorageSystem forProvider = _helper.getStorageSystemForProvider(storageSystem,
                        volumes.get(0));
                CIMInstance volumeInstance = _helper.checkExists(forProvider,
                        _cimPath.getBlockObjectPath(storageSystem, volume), false, false);
                _helper.doApplyRecoverPointTag(storageSystem, volume, false);
                if (volumeInstance == null) {
                    // related volume state (if any) has been deleted. skip processing, if already
                    // deleted from array.
                    _log.info(String.format("Volume %s already deleted: ", volume.getNativeId()));
                    volume.setInactive(true);
                    _dbClient.persistObject(volume);
                    VolumeTaskCompleter deleteTaskCompleter = multiVolumeTaskCompleter
                            .skipTaskCompleter(volume.getId());
                    deleteTaskCompleter.ready(_dbClient);
                    continue;
                }
                // Compare the volume labels of the to-be-deleted and existing volumes
                /**
                 * This will fail in the case when the user just changes the label of the
                 * volume...until we subscribe to indications from the provider, we will live with
                 * that.
                 */
                String volToDeleteLabel = volume.getDeviceLabel();
                String volInstanceLabel = CIMPropertyFactory.getPropertyValue(volumeInstance,
                        SmisConstants.CP_ELEMENT_NAME);
                if (volToDeleteLabel != null && volInstanceLabel != null
                        && !volToDeleteLabel.equals(volInstanceLabel)) {
                    // related volume state (if any) has been deleted. skip processing, if already
                    // deleted from array.
                    _log.info("VolToDeleteLabel {} : volInstancelabel {}", volToDeleteLabel, volInstanceLabel);
                    _log.info(String.format("Volume %s already deleted: ", volume.getNativeId()));
                    volume.setInactive(true);
                    // clear the associated consistency group from the volume
                    volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    _dbClient.updateAndReindexObject(volume);
                    VolumeTaskCompleter deleteTaskCompleter = multiVolumeTaskCompleter
                            .skipTaskCompleter(volume.getId());
                    deleteTaskCompleter.ready(_dbClient);
                    continue;
                }
                // Check if this volume has any dangling meta members on array. May not necessary be meta volume. Regular volume can have
                // dangling meta members as a result of expansion failure (and rollback failure).
                if (!storageSystem.checkIfVmax3()) {
                    boolean cleanupSuccess = cleanupDanglingMetaMembers(storageSystem, volume);
                    if (!cleanupSuccess) {
                        // cannot delete volume
                        String errorMessage = String.format("Failed to delete meta volume: %s ,  nativeId: %s .\n" +
                                "Could not cleanup dangling meta members.",
                                volume.getId(), volume.getNativeId());
                        ServiceError error = DeviceControllerErrors.smis.methodFailed("doDeleteVolume", errorMessage);
                        VolumeTaskCompleter deleteTaskCompleter = multiVolumeTaskCompleter.skipTaskCompleter(volume.getId());
                        deleteTaskCompleter.error(_dbClient, error);
                        _log.error(String.format(errorMessage));
                        continue;
                    }
                }
                if (!NullColumnValueGetter.isNullURI(volume.getAssociatedSourceVolume())) {
                    cloneVolumes.add(volume);
                }
                volumeNativeIds[volumeCount++] = volume.getNativeId();
            }
            _log.info(logMsgBuilder.toString());

            // VMAX3 has parking SLO storage groups that the volumes will be removed from
            // prior to the deletion. We need to check any of these SLOs StorageGroups to
            // see if they are empty. If so, we will delete them as part of the volume
            // delete operation.
            if (!parkingSLOStorageGroups.isEmpty()) {
                _helper.deleteParkingSLOStorageGroupsIfEmpty(storageSystem, parkingSLOStorageGroups);
            }

            // execute SMI-S Call , only if any Volumes left for deletion.
            if (!multiVolumeTaskCompleter.isVolumeTaskCompletersEmpty()) {
                if (!cloneVolumes.isEmpty()) {
                    processClonesBeforeDeletion(storageSystem, cloneVolumes);
                }
                CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storageSystem);
                CIMArgument[] inArgs = _helper.getDeleteVolumesInputArguments(storageSystem,
                        volumeNativeIds);
                CIMArgument[] outArgs = new CIMArgument[5];
                String returnElementsMethod;
                if (storageSystem.getUsingSmis80()) {
                    returnElementsMethod = SmisConstants.RETURN_ELEMENTS_TO_STORAGE_POOL;
                } else {
                    returnElementsMethod = SmisConstants.EMC_RETURN_TO_STORAGE_POOL;
                }
                _helper.invokeMethod(storageSystem, configSvcPath,
                        returnElementsMethod, inArgs, outArgs);
                CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs,
                        SmisConstants.JOB);
                if (job != null) {
                    ControllerServiceImpl.enqueueJob(new QueueJob(new SmisDeleteVolumeJob(job,
                            storageSystem.getId(), taskCompleter)));
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
                multiVolumeTaskCompleter.ready(_dbClient);
            }
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e
                    .getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            _log.error("Problem in doDeleteVolume: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doDeleteVolume",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Delete Volume End - Array: %s", storageSystem.getSerialNumber()));
        for (Volume volume : volumes) {
            logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
        }
        _log.info(logMsgBuilder.toString());
    }

    @Override
    public void doExportGroupCreate(final StorageSystem storage, final ExportMask exportMask,
            final Map<URI, Integer> volumeMap, final List<Initiator> initiators,
            final List<URI> targets, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportGroupCreate START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumeMap, _dbClient);
        _exportMaskOperationsHelper.createExportMask(storage, exportMask.getId(), volumeLunArray,
                targets, initiators, taskCompleter);
        _log.info("{} doExportGroupCreate END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportGroupDelete(final StorageSystem storage, final ExportMask exportMask,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportGroupDelete START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.deleteExportMask(storage, exportMask.getId(),
                new ArrayList<URI>(), new ArrayList<URI>(), new ArrayList<Initiator>(),
                taskCompleter);
        _log.info("{} doExportGroupDelete END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddVolume(final StorageSystem storage, final ExportMask exportMask,
            final URI volume, final Integer lun, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        map.put(volume, lun);
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), map, _dbClient);
        _exportMaskOperationsHelper.addVolume(storage, exportMask.getId(), volumeLunArray,
                taskCompleter);
        _log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddVolumes(final StorageSystem storage, final ExportMask exportMask,
            final Map<URI, Integer> volumes, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddVolume START ...", storage.getSerialNumber());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumes, _dbClient);
        _exportMaskOperationsHelper.addVolume(storage, exportMask.getId(), volumeLunArray,
                taskCompleter);
        _log.info("{} doExportAddVolume END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveVolume(final StorageSystem storage, final ExportMask exportMask,
            final URI volume, final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(),
                Arrays.asList(volume), taskCompleter);
        _log.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveVolumes(final StorageSystem storage, final ExportMask exportMask,
            final List<URI> volumes, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportRemoveVolume START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(), volumes,
                taskCompleter);
        _log.info("{} doExportRemoveVolume END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddInitiator(final StorageSystem storage, final ExportMask exportMask,
            final Initiator initiator, final List<URI> targets, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(),
                Arrays.asList(initiator), targets, taskCompleter);
        _log.info("{} doExportAddInitiator END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportAddInitiators(final StorageSystem storage, final ExportMask exportMask,
            final List<Initiator> initiators, final List<URI> targets,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportAddInitiator START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(), initiators, targets,
                taskCompleter);
        _log.info("{} doExportAddInitiator END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveInitiator(final StorageSystem storage, final ExportMask exportMask,
            final Initiator initiator, final List<URI> targets, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(),
                Arrays.asList(initiator), targets, taskCompleter);
        _log.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());
    }

    @Override
    public void doExportRemoveInitiators(final StorageSystem storage, final ExportMask exportMask,
            final List<Initiator> initiators, final List<URI> targets,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiator START ...", storage.getSerialNumber());
        _exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(), initiators,
                targets, taskCompleter);
        _log.info("{} doExportRemoveInitiator END ...", storage.getSerialNumber());
    }

    @Override
    public void doConnect(final StorageSystem storage) {
        try {
            _helper.getConnection(storage);
        } catch (Exception e) {
            throw new IllegalStateException("No cim connection for " + storage.getIpAddress(), e);
        }
    }

    @Override
    public void doDisconnect(final StorageSystem storage) {
    }

    @Override
    public void doCreateSingleSnapshot(final StorageSystem storage, final List<URI> snapshotList,
            final Boolean createInactive, final Boolean readOnly, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotList);
            URI snapshot = snapshots.get(0).getId();
            _snapshotOperations.createSingleVolumeSnapshot(storage, snapshot, createInactive,
                    readOnly, taskCompleter);
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to create snapshot(s) on array %s",
                    storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doCreateSingleSnapshot",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doCreateSnapshot(final StorageSystem storage, final List<URI> snapshotList,
            final Boolean createInactive, final Boolean readOnly, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotList);
            if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
                _snapshotOperations.createGroupSnapshots(storage, snapshotList, createInactive,
                        readOnly, taskCompleter);
            } else {
                URI snapshot = snapshots.get(0).getId();
                _snapshotOperations.createSingleVolumeSnapshot(storage, snapshot, createInactive,
                        readOnly, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to create snapshot(s) on array %s",
                    storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doCreateSnapshot",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doActivateSnapshot(final StorageSystem storage, final List<URI> snapshotList,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient
                    .queryObject(BlockSnapshot.class, snapshotList);
            URI snapshot = snapshots.get(0).getId();
            if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
                _snapshotOperations.activateGroupSnapshots(storage, snapshot, taskCompleter);
            } else {
                _snapshotOperations.activateSingleVolumeSnapshot(storage, snapshot, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to create snapshot(s) on array %s",
                    storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doActivateSnapshot",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doDeleteSnapshot(final StorageSystem storage, final URI snapshot,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class,
                    Arrays.asList(snapshot));
            if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
                _snapshotOperations.deleteGroupSnapshots(storage, snapshot, taskCompleter);
            } else {
                _snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to delete snapshot(s) on array %s",
                    storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doDeleteSnapshot",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doDeleteSelectedSnapshot(final StorageSystem storage, final URI snapshot,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            _snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to delete snapshot(s) on array %s",
                    storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doDeleteSnapshot",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doRestoreFromSnapshot(final StorageSystem storage, final URI volume,
            final URI snapshot, final TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class,
                    Arrays.asList(snapshot));
            if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
                _snapshotOperations.restoreGroupSnapshots(storage, volume, snapshot, taskCompleter);
            } else {
                _snapshotOperations.restoreSingleVolumeSnapshot(storage, volume, snapshot,
                        taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to restore snapshot(s) on array %s",
                    storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doRestoreFromSnapshot",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            _log.error("Problem in doRestoreFromSnapshot: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doRestoreFromSnapshot",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doResyncSnapshot(final StorageSystem storage, final URI volume,
            final URI snapshot, final TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class,
                    Arrays.asList(snapshot));
            if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
                _snapshotOperations.resyncGroupSnapshots(storage, volume, snapshot, taskCompleter);
            } else {
                _snapshotOperations.resyncSingleVolumeSnapshot(storage, volume, snapshot,
                        taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String.format(
                    "IO exception when trying to restore snapshot(s) on array %s",
                    storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doRestoreFromSnapshot",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * This interface will return a mapping of the port name to the URI of the ExportMask in which
     * it is contained.
     * 
     * @param storage
     *            [in] - StorageSystem object representing the array
     * @param initiatorNames
     *            [in] - Port identifiers (WWPN or iSCSI name)
     * @param mustHaveAllPorts
     *            [in] Indicates if true, *all* the passed in initiators have to be in the existing
     *            matching mask. If false, a mask with *any* of the specified initiators will be
     *            considered a hit.
     * @return Map of port name to Set of ExportMask URIs
     */
    @Override
    public Map<String, Set<URI>> findExportMasks(final StorageSystem storage,
            final List<String> initiatorNames, final boolean mustHaveAllPorts) {
        return _exportMaskOperationsHelper.findExportMasks(storage, initiatorNames,
                mustHaveAllPorts);
    }

    @Override
    public ExportMask refreshExportMask(final StorageSystem storage, final ExportMask mask) {
        return _exportMaskOperationsHelper.refreshExportMask(storage, mask);
    }

    @Override
    public void doActivateFullCopy(final StorageSystem storageSystem, final URI fullCopy,
            final TaskCompleter completer) {
        _cloneOperations.activateSingleClone(storageSystem, fullCopy, completer);
    }

    @Override
    public Integer checkSyncProgress(final URI storage, final URI source, final URI target)
            throws DeviceControllerException {
        _log.info("START checkSyncProgress for source: {} target: {}", source, target);

        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            BlockObject sourceObj = BlockObject.fetch(_dbClient, source);
            Volume targetObject = _dbClient.queryObject(Volume.class, target);
            String percentSyncValue = null;
            if (ReplicationState.getEnumValue(targetObject.getReplicaState()) == ReplicationState.DETACHED) {
                return -1;
            }
            CIMObjectPath syncObject = null;
            if (storageSystem.deviceIsType(Type.vmax) &&
                    ConsistencyUtils.isCloneInConsistencyGroup(targetObject.getId(), _dbClient)) {
                String consistencyGroupName = _helper.getConsistencyGroupName(sourceObj, storageSystem);
                String replicationGroupName = targetObject.getReplicationGroupInstance();
                syncObject = _cimPath.getGroupSynchronizedPath(storageSystem, consistencyGroupName, replicationGroupName);
            } else {
                syncObject = _cimPath.getStorageSynchronized(storageSystem, sourceObj, storageSystem, targetObject);
            }

            CIMInstance syncInstance = _helper.getInstance(storageSystem, syncObject, false, false, null);
            percentSyncValue = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_PERCENT_SYNCED);
            String copyState = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_COPY_STATE);
            if (copyState.equals(Integer.toString(SmisConstants.FRACTURED)) ||
                    copyState.equals(Integer.toString(SmisConstants.SPLIT))) {
                // when fractured or split, the synchronization should have been done.
                percentSyncValue = "100";
            }

            _log.info("DBG Got progress {}", percentSyncValue);

            return Integer.parseInt(percentSyncValue);
        } catch (Exception e) {
            String msg = String.format("Failed to check synchronization progress for %s", target);
            _log.error(msg, e);
        }
        return null;
    }

    @Override
    public void doCreateMirror(final StorageSystem storage, final URI mirror,
            final Boolean createInactive, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _mirrorOperations.createSingleVolumeMirror(storage, mirror, createInactive, taskCompleter);
    }

    @Override
    public void doCreateGroupMirrors(final StorageSystem storage, final List<URI> mirrorList,
            final Boolean createInactive, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _mirrorOperations.createGroupMirrors(storage, mirrorList, createInactive, taskCompleter);
    }

    @Override
    public void doFractureMirror(final StorageSystem storage, final URI mirror, final Boolean sync,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _mirrorOperations.fractureSingleVolumeMirror(storage, mirror, sync, taskCompleter);
    }

    @Override
    public void doFractureGroupMirrors(final StorageSystem storage, final List<URI> mirrorList, final Boolean sync,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _mirrorOperations.fractureGroupMirrors(storage, mirrorList, sync, taskCompleter);
    }

    @Override
    public void doDetachMirror(final StorageSystem storage, final URI mirror,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _mirrorOperations.detachSingleVolumeMirror(storage, mirror, taskCompleter);
    }

    @Override
    public void doDetachGroupMirrors(final StorageSystem storage, final List<URI> mirrorList,
            final Boolean deleteGroup, final TaskCompleter taskCompleter) throws DeviceControllerException {
        _mirrorOperations.detachGroupMirrors(storage, mirrorList, deleteGroup, taskCompleter);
    }

    @Override
    public void doResumeNativeContinuousCopy(final StorageSystem storage, final URI mirror,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _mirrorOperations.resumeSingleVolumeMirror(storage, mirror, taskCompleter);
    }

    @Override
    public void doResumeGroupNativeContinuousCopies(final StorageSystem storage, final List<URI> mirrorList,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _mirrorOperations.resumeGroupMirrors(storage, mirrorList, taskCompleter);
    }

    @Override
    public void doDeleteMirror(final StorageSystem storage, final URI mirror,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _mirrorOperations.deleteSingleVolumeMirror(storage, mirror, taskCompleter);
    }

    @Override
    public void doDeleteGroupMirrors(final StorageSystem storage, final List<URI> mirrorList,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _mirrorOperations.deleteGroupMirrors(storage, mirrorList, taskCompleter);
    }

    @Override
    public void doCreateClone(final StorageSystem storage, final URI sourceVolume,
            final URI cloneVolume, final Boolean createInactive, final TaskCompleter taskCompleter) {
        _cloneOperations.createSingleClone(storage, sourceVolume, cloneVolume, createInactive,
                taskCompleter);
    }

    @Override
    public void doDetachClone(final StorageSystem storage, final URI cloneVolume,
            final TaskCompleter taskCompleter) {
        Volume clone = _dbClient.queryObject(Volume.class, cloneVolume);
        if (clone != null && clone.getReplicaState().equals(ReplicationState.DETACHED.name())) {
            taskCompleter.ready(_dbClient);
            return;
        }
        _cloneOperations.detachSingleClone(storage, cloneVolume, taskCompleter);
    }

    @Override
    public void doRestoreFromClone(final StorageSystem storage,
            final URI cloneVolume, final TaskCompleter taskCompleter) {
        _cloneOperations.restoreFromSingleClone(storage, cloneVolume, taskCompleter);
    }

    @Override
    public void doResyncClone(final StorageSystem storage,
            final URI cloneVolume, final TaskCompleter taskCompleter) {
        _cloneOperations.resyncSingleClone(storage, cloneVolume, taskCompleter);
    }

    @Override
    public void doFractureClone(StorageSystem storageDevice, URI source, URI clone,
            TaskCompleter completer) {
        _cloneOperations.fractureSingleClone(storageDevice, source, clone, completer);

    }

    private boolean isSRDFProtected(final Volume volume) {
        return volume.getSrdfParent() != null || volume.getSrdfTargets() != null;
    }

    /**
     * This method is for adding volumes to a consistency group. Be aware that this method is going
     * to be invoked by the SmisCreateVolumeJob, after there is a successful completion of the
     * volume create.
     * 
     * @param storage
     * @param consistencyGroup
     * @param volumes
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    public void addVolumesToConsistencyGroup(StorageSystem storage,
            final BlockConsistencyGroup consistencyGroup, final List<Volume> volumes,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        if (isSRDFProtected(volumes.get(0))) {
            return;
        }

        if (consistencyGroup == null || !consistencyGroup.created(storage.getId())) {
            final String errMsg = "Unable to add volumes to consistency group: no consistency group provided or it has not been created in the array";
            _log.error(errMsg);
            ServiceError error = DeviceControllerErrors.smis.noConsistencyGroupProvided();
            taskCompleter.error(_dbClient, error);
            return;
        }

        if (consistencyGroup.getTypes().contains(BlockConsistencyGroup.Types.SRDF.name())) {
            taskCompleter.ready(_dbClient);
            return;
        }
        if (volumes == null || volumes.isEmpty()) {
            final String errMsg = format(
                    "Unable to add volumes to consistency group {0}: no volumes provided",
                    consistencyGroup.getId());
            _log.error(errMsg);
            ServiceError error = DeviceControllerErrors.smis.noConsistencyGroupProvided();
            taskCompleter.error(_dbClient, error);
            return;
        }
        if (storage == null) {
            final String errMsg = format(
                    "Unable to add volumes to consistency group {0}: no storage system provided",
                    consistencyGroup.getId());
            _log.error(errMsg);
            ServiceError error = DeviceControllerErrors.smis.noStorageSystemProvided();
            taskCompleter.error(_dbClient, error);
            return;
        }
        _log.info("Adding Volumes to Consistency Group: {}", consistencyGroup.getId());
        try {
            // Check if the consistency group exists
            String groupName = _helper.getConsistencyGroupName(consistencyGroup, storage);
            storage = findProviderFactory.withGroup(storage, groupName).find();

            if (storage == null) {
                ServiceError error = DeviceControllerErrors.smis.noConsistencyGroupWithGivenName();
                taskCompleter.error(_dbClient, error);
                return;
            }

            final CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            // Build list of native ids
            final Set<String> nativeIds = new HashSet<String>();
            for (final Volume volume : volumes) {
                // Do not add RP+VPlex journal or target backing volumes to consistency groups.
                // This causes issues with local array snapshots of RP+VPlex volumes.
                if (!RPHelper.isAssociatedToRpVplexType(volume, _dbClient,
                        PersonalityTypes.METADATA, PersonalityTypes.TARGET)) {
                    nativeIds.add(volume.getNativeId());
                } else {
                    _log.info("Volume {} will not be added to consistency group because it is a backing volume for "
                            + "an RP+VPlex virtual journal volume.", volume.getId().toString());
                }
            }
            _log.info("List of native ids to be added: {}", nativeIds);

            if (!nativeIds.isEmpty()) {
                // At this point the 'nativeIds' list would have a list of members that would need to be
                // added to the CG
                final CIMArgument[] outArgs = new CIMArgument[5];
                final String[] memberNames = nativeIds.toArray(new String[nativeIds.size()]);
                final CIMObjectPath[] volumePaths = _cimPath.getVolumePaths(storage, memberNames);
                boolean cgHasGroupRelationship = ControllerUtils.checkCGHasGroupRelationship(consistencyGroup.getId(), _dbClient);
                if (!cgHasGroupRelationship) {
                    final CIMObjectPath cgPath = _cimPath.getReplicationGroupPath(storage, groupName);
                    final CIMArgument[] inArgs = _helper.getAddMembersInputArguments(cgPath, volumePaths);
                    _helper.invokeMethod(storage, replicationSvc, SmisConstants.ADD_MEMBERS, inArgs,
                            outArgs);
                } else {
                    final CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                            SmisConstants.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                    _log.info("Adding volumes {} to device masking group {}", StringUtils.join(memberNames, ", "),
                            maskingGroupPath.toString());
                    final CIMArgument[] inArgs = _helper.getAddOrRemoveMaskingGroupMembersInputArguments(maskingGroupPath,
                            volumePaths, true);
                    _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                            SmisConstants.ADD_MEMBERS, inArgs, outArgs, null);
                }

                _log.info("Volumes sucessfully added to the Consistency Group: {}"
                        + consistencyGroup.getId());
            }
        } catch (Exception e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e
                    .getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * Method will remove the volume from the consistency group to which it currently belongs.
     * 
     * @param storage
     *            [required] - StorageSystem object
     * @param volume
     *            [required] - could be a clone, or a volume
     */
    private void removeVolumeFromConsistencyGroup(StorageSystem storage, final Volume volume)
            throws Exception {
        CloseableIterator<CIMObjectPath> assocVolNamesIter = null;
        try {
            // Check if the consistency group exists
            String groupName = null;
            // In case of clone, 'replicationgroupinstance' property contains the Replication Group name.
            if (NullColumnValueGetter.isNotNullValue(volume.getReplicationGroupInstance())) {
                groupName = volume.getReplicationGroupInstance();
            } else {
                groupName = _helper.getConsistencyGroupName(volume, storage);
            }

            storage = findProviderFactory.withGroup(storage, groupName).find();

            if (storage == null) {
                _log.warn("Replication Group {} not found. Skipping Remove Volume from CG step.", groupName);
                return;
            }
            CIMObjectPath cgPath = _cimPath.getReplicationGroupPath(storage, groupName);

            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            CIMArgument[] inArgs;
            CIMArgument[] outArgs = new CIMArgument[5];
            CIMInstance cgPathInstance = _helper.checkExists(storage, cgPath, false, false);
            if (cgPathInstance != null) {
                CIMObjectPath[] volumePaths = _cimPath.getVolumePaths(storage,
                        new String[] { volume.getNativeId() });
                boolean volumeIsInGroup = false;
                assocVolNamesIter = _helper.getAssociatorNames(storage, cgPath, null,
                        SmisConstants.CIM_STORAGE_VOLUME, null, null);
                while (assocVolNamesIter.hasNext()) {
                    CIMObjectPath assocVolPath = assocVolNamesIter.next();
                    String deviceId = assocVolPath.getKey(SmisConstants.CP_DEVICE_ID).getValue()
                            .toString();
                    if (deviceId.equalsIgnoreCase(volume.getNativeId())) {
                        volumeIsInGroup = true;
                        break;
                    }
                }
                if (volumeIsInGroup) {
                    boolean cgHasGroupRelationship = false;
                    if (volume.isInCG()) {
                        cgHasGroupRelationship = ControllerUtils.checkCGHasGroupRelationship(volume.getConsistencyGroup(), _dbClient);
                    }

                    if (cgHasGroupRelationship) {
                        // remove from DeviceMaskingGroup
                        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                                SmisConstants.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                        _log.info("Removing volume {} from device masking group {}", volume.getNativeId(), maskingGroupPath.toString());
                        inArgs = _helper.getRemoveAndUnmapMaskingGroupMembersInputArguments(maskingGroupPath,
                                volumePaths, storage, true);
                        _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                SmisConstants.REMOVE_MEMBERS, inArgs, outArgs, null);
                    } else {
                        inArgs = _helper.getRemoveMembersInputArguments(cgPath, volumePaths);
                        _helper.invokeMethod(storage, replicationSvc, SmisConstants.REMOVE_MEMBERS,
                                inArgs, outArgs);
                    }
                } else {
                    _log.info("Volume {} is no longer in the replication group {}",
                            volume.getNativeId(), cgPath.toString());
                }
            } else {
                _log.warn("The Consistency Group {} does not exist on the array.", cgPath);
            }
        } catch (Exception e) {
            _log.error("Problem making SMI-S call: ", e);
            throw e;
        } finally {
            if (assocVolNamesIter != null) {
                assocVolNamesIter.close();
            }
        }
    }

    /**
     * Method will make synchronous SMI-S calls to clean up any backup snapshots that may exist for
     * the volume. Typically, on VNX arrays, if there's a restore operation against an 'advanced'
     * snap, there will be backup snapshot created. There isn't any easy way to get to this backup
     * using the SMI-S API, so we'll have to clean them all up when we go to delete the volume.
     * 
     * @param storage
     *            [required] - StorageSystem object
     * @param volume
     *            [required] - Volume object
     * @throws Exception
     */
    private void cleanupAnyBackupSnapshots(final StorageSystem storage, final Volume volume)
            throws Exception {
        CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storage, volume);
        if (_helper.checkExists(storage, volumePath, false, false) == null) {
            _log.info(String
                    .format("cleanupAnyBackupSnapshots(%s, %s) -- volumePath does not exist, perhaps it has already been deleted?",
                            storage.getSerialNumber(), volume.getLabel()));
            return;
        }
        CloseableIterator<CIMObjectPath> settingsIterator = null;
        try {
            settingsIterator = _helper.getReference(storage, volumePath,
                    SmisConstants.CIM_SETTINGS_DEFINE_STATE, null);
            while (settingsIterator.hasNext()) {
                CIMObjectPath settingsPath = settingsIterator.next();
                CIMArgument[] outArgs = new CIMArgument[5];
                _helper.callModifySettingsDefineState(storage,
                        _helper.getDeleteSettingsForSnapshotInputArguments(settingsPath), outArgs);
            }
        } finally {
            if (settingsIterator != null) {
                settingsIterator.close();
            }
        }
    }

    /**
     * Method will look up backup snapshots that were created when a snapshot restore operation was
     * performed, then clean them up. This would be required in order to do the volume delete.
     * 
     * @param storage
     *            [required] - StorageSystem object representing the array
     * @param volume
     *            [required] - Volume object representing the volume that has a snapshot created for
     *            it
     */
    private void cleanupAnyGroupBackupSnapshots(final StorageSystem storage, final Volume volume) {
        CloseableIterator<CIMObjectPath> settingsIterator = null;
        try {
            String groupName = _helper.getConsistencyGroupName(volume, storage);
            CIMObjectPath cgPath = _cimPath.getReplicationGroupPath(storage, groupName);
            CIMArgument[] outArgs = new CIMArgument[5];
            CIMInstance cgPathInstance = _helper.checkExists(storage, cgPath, false, false);
            if (cgPathInstance != null) {
                settingsIterator = _helper.getAssociatorNames(storage, cgPath, null,
                        SmisConstants.CLAR_SYNCHRONIZATION_ASPECT_FOR_SOURCE_GROUP, null, null);
                while (settingsIterator.hasNext()) {
                    CIMObjectPath aspectPath = settingsIterator.next();
                    CIMObjectPath settingsPath = _cimPath.getGroupSynchronizedSettingsPath(storage,
                            groupName, (String) aspectPath.getKey(SmisConstants.CP_INSTANCE_ID)
                                    .getValue());
                    CIMArgument[] deleteSettingsInput = _helper
                            .getDeleteSettingsForSnapshotInputArguments(settingsPath);
                    _helper.callModifySettingsDefineState(storage, deleteSettingsInput, outArgs);
                }
            }
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
        } finally {
            if (settingsIterator != null) {
                settingsIterator.close();
            }
        }
    }

    /**
     * Method will look up backup snapshots that were created when a snapshot restore operation was
     * performed, then clean them up. This would be required in order to delete the ReplicationGroup.
     * 
     * @param storage
     *            [required] - StorageSystem object representing the array
     * @param replicationGroupPath
     *            [required] - CIMObjectPath object representing the ReplicationGroup
     */
    private void cleanupAnyGroupBackupSnapshots(StorageSystem storage, CIMObjectPath replicationGroupPath) {
        _log.info("Cleaning up backup snapshots for: {}", replicationGroupPath);
        CloseableIterator<CIMObjectPath> settings = null;
        try {
            settings = _helper.getReference(storage, replicationGroupPath,
                    SmisConstants.CLAR_SETTINGS_DEFINE_STATE_RG_SAFS, null);
            while (settings.hasNext()) {
                CIMObjectPath path = settings.next();
                CIMArgument[] inArgs = _helper.getDeleteSettingsForSnapshotInputArguments(path);
                CIMArgument[] outArgs = new CIMArgument[5];
                _helper.callModifySettingsDefineState(storage, inArgs, outArgs);
            }
        } catch (Exception e) {
            _log.warn("Problem making SMI-S call: ", e);
        } finally {
            if (settings != null) {
                settings.close();
            }
        }
    }

    @Override
    public String doAddStorageSystem(final StorageSystem storage) throws DeviceControllerException {
        try {
            String system = "";
            CIMObjectPath seSystemRegistrationSvc = _helper.getRegistrationService(storage);
            CIMArgument[] inArgs = _helper.getAddStorageCIMArguments(storage);
            CIMArgument[] outArgs = new CIMArgument[5];
            Object result = _helper.invokeMethod(storage, seSystemRegistrationSvc,
                    SmisConstants.EMC_ADD_SYSTEM, inArgs, outArgs);
            javax.cim.UnsignedInteger32 status = (javax.cim.UnsignedInteger32) result;
            if (status.intValue() == 0 && outArgs[0] != null) {
                outArgs[0].getName();
                CIMObjectPath objPath = (CIMObjectPath) outArgs[0].getValue();
                system = objPath.getKey(Constants._Name).getValue().toString();
            }
            return system;
        } catch (WBEMException ex) {
            _log.debug("Failed to add storage system to SMI-S Provider : " + ex.getMessage());
            throw new DeviceControllerException(ex);
        }
    }

    @Override
    public void doRemoveStorageSystem(final StorageSystem storage) throws DeviceControllerException {
        try {
            CIMObjectPath seSystemRegistrationSvc = _helper.getRegistrationService(storage);
            CIMArgument[] inArgs = _helper.getRemStorageCIMArguments(storage);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, seSystemRegistrationSvc, SmisConstants.EMC_REMOVE_SYSTEM,
                    inArgs, outArgs);
        } catch (WBEMException ex) {
            _log.debug("Failed to remove storage system from SMI-S Provider : " + ex.getMessage());
            throw new DeviceControllerException(ex);
        }
    }

    @Override
    public void doCopySnapshotsToTarget(final StorageSystem storage, final List<URI> snapshotList,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient
                    .queryObject(BlockSnapshot.class, snapshotList);
            if (ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, taskCompleter)) {
                _snapshotOperations
                        .copyGroupSnapshotsToTarget(storage, snapshotList, taskCompleter);
            } else {
                for (URI snapshot : snapshotList) {
                    _snapshotOperations.copySnapshotToTarget(storage, snapshot, taskCompleter);
                }
            }
        } catch (DatabaseException e) {
            taskCompleter.error(_dbClient, DatabaseException.fatals.queryFailed(e));
        }
    }

    @Override
    public void doCreateConsistencyGroup(final StorageSystem storage, final URI consistencyGroupId,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);
        try {

            CIMArgument[] inArgs;
            CIMArgument[] outArgs = new CIMArgument[5];
            // Invoke the creation of the consistency group with a null name so that it generates a
            // random name avoiding name collisions
            // Note: For SRDF source and target CGs, we create group on array with user requested name
            String groupName = null;
            boolean srdfCG = false;

            // create target CG on source provider
            StorageSystem forProvider = storage;
            if (consistencyGroup.getRequestedTypes().contains(Types.SRDF.name())) {
                srdfCG = true;
                groupName = (consistencyGroup.getAlternateLabel() != null) ?
                        consistencyGroup.getAlternateLabel() : consistencyGroup.getLabel();

                if (NullColumnValueGetter.isNotNullValue(consistencyGroup.getAlternateLabel())) {
                    forProvider = getSRDFSourceProvider(consistencyGroup);
                    _log.debug("Creating target Consistency Group on source provider");
                }
            }
            inArgs = _helper.getCreateReplicationGroupInputArguments(groupName);
            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            _helper.invokeMethod(forProvider, replicationSvc, SmisConstants.CREATE_GROUP, inArgs, outArgs);
            // Grab the generated name from the instance ID and store it in the db
            final String instanceID = (String) _cimPath
                    .getCimObjectPathFromOutputArgs(outArgs, CP_REPLICATION_GROUP)
                    .getKey(CP_INSTANCE_ID).getValue();

            // VMAX instanceID, e.g., 000196700567+EMC_SMI_RG1414546375042 (8.0.2 provider)
            final String deviceName = instanceID.split(Constants.PATH_DELIMITER_REGEX)[storage.getUsingSmis80() ? 1 : 0];
            consistencyGroup.addSystemConsistencyGroup(storage.getId().toString(), deviceName);
            if (srdfCG) {
                consistencyGroup.addConsistencyGroupTypes(Types.SRDF.name());
            } else {
                consistencyGroup.addConsistencyGroupTypes(Types.LOCAL.name());
            }
            if (NullColumnValueGetter.isNullURI(consistencyGroup.getStorageController())) {
                consistencyGroup.setStorageController(storage.getId());
            }
            _dbClient.persistObject(consistencyGroup);
            // This function could be called from doAddToConsistencyGroup() with a null taskCompleter.
            if (taskCompleter != null) {
                // Set task to ready.
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            _log.error("Failed to create consistency group: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "doCreateConsistencyGroup", e.getMessage());
            // Set task to error
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, error);
            }
        }
    }

    /**
     * Gets the SRDF source provider, given target CG.
     */
    private StorageSystem getSRDFSourceProvider(
            BlockConsistencyGroup consistencyGroup) {
        StorageSystem sourceProvider = null;
        List<BlockConsistencyGroup> groups = CustomQueryUtility
                .queryActiveResourcesByConstraint(_dbClient,
                        BlockConsistencyGroup.class, PrefixConstraint.Factory
                                .getFullMatchConstraint(
                                        BlockConsistencyGroup.class, "label",
                                        consistencyGroup.getAlternateLabel()));
        BlockConsistencyGroup sourceCG = groups.iterator().next();
        URI sourceSystemURI = sourceCG.getStorageController();
        if (!NullColumnValueGetter.isNullURI(sourceSystemURI)) {
            sourceProvider = _dbClient.queryObject(StorageSystem.class, sourceSystemURI);
        }
        return sourceProvider;
    }

    /**
     * Attempts to delete a system consistency group via an SMI-S provider, if it exists.
     * Since a {@link BlockConsistencyGroup} may reference multiple system consistency groups, attempt to remove its
     * reference, in addition to updating the types field.
     *
     * This method may be called as part of a workflow rollback.
     *
     * @param storage               StorageSystem
     * @param consistencyGroupId    BlockConsistencyGroup URI
     * @param markInactive          True, if the user initiated removal of the BlockConsistencyGroup
     * @param taskCompleter         TaskCompleter
     * @throws DeviceControllerException
     */
    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage, final URI consistencyGroupId,
            Boolean markInactive, final TaskCompleter taskCompleter) throws DeviceControllerException {

        ServiceError serviceError = null;
        URI systemURI = storage.getId();
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupId);

        try {
            if (consistencyGroup == null || consistencyGroup.getInactive()) {
                _log.info(String.format("%s is inactive or deleted", consistencyGroupId));
                return;
            }

            // This will be null, if consistencyGroup references no system CG's for storage.
            String groupName = _helper.getConsistencyGroupName(consistencyGroup, storage);
            if (groupName == null) {
                _log.info(String.format("%s contains no system CG for %s.  Assuming it has already been deleted.",
                        consistencyGroupId, systemURI));
                return;
            }

            // Find a provider with reference to the CG
            storage = findProviderFactory.withGroup(storage, groupName).find();
            if (storage == null) {
                // Fail the task
                serviceError = DeviceControllerErrors.smis.noConsistencyGroupWithGivenName();
                _log.warn(String.format("Consistency group %s not found on %s", groupName, systemURI));
                return;
            }

            // Check if the CG exists
            CIMObjectPath cgPath = _cimPath.getReplicationGroupPath(storage, groupName);
            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            CIMInstance cgPathInstance = _helper.checkExists(storage, cgPath, false, false);
            if (cgPathInstance != null) {
                if (storage.deviceIsType(Type.vnxblock)) {
                    cleanupAnyGroupBackupSnapshots(storage, cgPath);
                }

                // Invoke the deletion of the consistency group
                CIMArgument[] inArgs;
                CIMArgument[] outArgs = new CIMArgument[5];
                inArgs = _helper.getDeleteReplicationGroupInputArguments(storage, groupName);
                _helper.invokeMethod(storage, replicationSvc, SmisConstants.DELETE_GROUP, inArgs,
                        outArgs);
            }

            // Remove the replication group name from the SystemConsistencyGroup field
            consistencyGroup.removeSystemConsistencyGroup(systemURI.toString(), groupName);

            /*
             * Verify if the BlockConsistencyGroup references any LOCAL arrays.
             * If we no longer have any references we can remove the 'LOCAL' type from the BlockConsistencyGroup.
             */
            List<URI> referencedArrays = BlockConsistencyGroupUtils.getLocalSystems(consistencyGroup, _dbClient);
            boolean cgReferenced = false;
            for (URI storageSystemUri : referencedArrays) {
                StringSet cgs = consistencyGroup.getSystemConsistencyGroups().get(storageSystemUri.toString());
                if (cgs != null && !cgs.isEmpty()) {
                    cgReferenced = true;
                    break;
                }
            }

            if (!cgReferenced) {
                // Remove the LOCAL type
                StringSet cgTypes = consistencyGroup.getTypes();
                cgTypes.remove(BlockConsistencyGroup.Types.LOCAL.name());
                consistencyGroup.setTypes(cgTypes);

                // Remove the referenced storage system as well, but only if there are no other types
                // of storage systems associated with the CG.
                if (!BlockConsistencyGroupUtils.referencesNonLocalCgs(consistencyGroup, _dbClient)) {
                    consistencyGroup.setStorageController(NullColumnValueGetter.getNullURI());
                }
            }

            // Update the consistency group model
            consistencyGroup.setInactive(markInactive);
            _dbClient.updateObject(consistencyGroup);
        } catch (Exception e) {
            _log.error("Failed to delete consistency group: ", e);
            serviceError = DeviceControllerErrors.smis.methodFailed("doDeleteConsistencyGroup", e.getMessage());
        } finally {
            if (serviceError != null) {
                taskCompleter.error(_dbClient, serviceError);
            } else {
                taskCompleter.ready(_dbClient);
            }
        }
    }

    private boolean cleanupDanglingMetaMembers(StorageSystem storageSystem, Volume volume) {
        // Check if this volume has associated meta members which have not be added to the volume
        // (create of meta volume failed before it was formed and rollback failed to clean meta members).
        // Delete these members.

        boolean isSuccess = false;
        try {
            StringSet metaMembers = volume.getMetaVolumeMembers();
            if (metaMembers == null || metaMembers.isEmpty()) {
                // No members to clean up
                isSuccess = true;
            } else {
                // Delete meta member volumes from array
                URI volumeURI = volume.getId();
                boolean isWFStep = false;
                CleanupMetaVolumeMembersCompleter cleanupCompleter = new CleanupMetaVolumeMembersCompleter(volumeURI, isWFStep, null, null);
                doCleanupMetaMembers(storageSystem, volume, cleanupCompleter);
                isSuccess = cleanupCompleter.isSuccess();
            }
        } catch (DeviceControllerException e) {
            _log.error("Problem in cleanupDanglingMetaMembers: ", e);
        }

        return isSuccess;
    }

    @Override
    public void doCleanupMetaMembers(final StorageSystem storageSystem, final Volume volume,
            CleanupMetaVolumeMembersCompleter cleanupCompleter)
            throws DeviceControllerException {
        // Remove meta member volumes from storage device
        try {
            _log.info(String.format("doCleanupMetaMembers  Start - Array: %s, Volume: %s",
                    storageSystem.getSerialNumber(), volume.getLabel()));

            // Get meta volume members from the volume data
            StringSet metaMembers = volume.getMetaVolumeMembers();

            if (metaMembers != null && !metaMembers.isEmpty()) {
                _log.info(String.format(
                        "doCleanupMetaMembers: Members stored for meta volume: %n   %s",
                        metaMembers));
                // Check if volumes still exist in array and if it is not composite member (already
                // added to the meta volume)
                Set<String> volumeIds = new HashSet<String>();
                for (String nativeId : metaMembers) {
                    CIMInstance volumeInstance = _helper.checkExists(storageSystem,
                            _cimPath.getVolumePath(storageSystem, nativeId), false, false);
                    if (volumeInstance != null) {
                        // Check that volume is not "Composite Volume Member", "Usage" property
                        // equals 15
                        String usage = CIMPropertyFactory.getPropertyValue(volumeInstance,
                                SmisConstants.CP_VOLUME_USAGE);
                        int usageInt = Integer.valueOf(usage);
                        _log.debug("doCleanupMetaMembers: Volume: " + nativeId
                                + ", Usage of volume: " + usageInt);
                        if (usageInt != SmisConstants.COMPOSITE_ELEMENT_MEMBER) {
                            volumeIds.add(nativeId);
                        }
                    }
                }
                if (volumeIds.isEmpty()) {
                    cleanupCompleter.setSuccess(true);
                    _log.info("doCleanupMetaMembers: No meta members to cleanup in array.");
                } else {
                    _log.info(String
                            .format("doCleanupMetaMembers: Members to cleanup in array: %n   %s",
                                    volumeIds));
                    String[] nativeIds = volumeIds.toArray(new String[0]);
                    // Prepare parameters and call method to delete meta members from array
                    CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storageSystem);
                    CIMArgument[] inArgs = _helper.getDeleteVolumesInputArguments(storageSystem,
                            nativeIds);
                    CIMArgument[] outArgs = new CIMArgument[5];
                    SmisCleanupMetaVolumeMembersJob smisJobCompleter = null;
                    String returnElementsMethod;
                    if (storageSystem.getUsingSmis80()) {
                        returnElementsMethod = SmisConstants.RETURN_ELEMENTS_TO_STORAGE_POOL;
                    } else {
                        returnElementsMethod = SmisConstants.EMC_RETURN_TO_STORAGE_POOL;
                    }
                    // When "cleanup" is separate workflow step, call async (for example rollback
                    // step in volume expand)
                    // Otherwise, call synchronously (for example when cleanup is part of meta
                    // volume create rollback)
                    if (cleanupCompleter.isWFStep()) {
                        // invoke async
                        _helper.invokeMethod(storageSystem, configSvcPath,
                                returnElementsMethod, inArgs, outArgs);
                        CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs,
                                SmisConstants.JOB);
                        if (job != null) {
                            ControllerServiceImpl.enqueueJob(new QueueJob(
                                    new SmisCleanupMetaVolumeMembersJob(job, storageSystem.getId(),
                                            volume.getId(), cleanupCompleter)));
                        }
                    } else {
                        // invoke synchronously
                        smisJobCompleter = new SmisCleanupMetaVolumeMembersJob(null,
                                storageSystem.getId(), volume.getId(), cleanupCompleter);
                        _helper.invokeMethodSynchronously(storageSystem, configSvcPath,
                                returnElementsMethod, inArgs, outArgs,
                                smisJobCompleter);
                    }
                }
            } else {
                _log.info("doCleanupMetaMembers: No meta members stored for meta volume. Nothing to cleanup in array.");
                cleanupCompleter.setSuccess(true);
            }
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e
                    .getMessage());
            cleanupCompleter.setError(error);
            cleanupCompleter.setSuccess(false);
        } catch (Exception e) {
            _log.error("Problem in doCleanupMetaMembers: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doCleanupMetaMembers",
                    e.getMessage());
            cleanupCompleter.setError(error);
            cleanupCompleter.setSuccess(false);
        }
        _log.info(String.format("doCleanupMetaMembers End - Array: %s,  Volume: %s",
                storageSystem.getSerialNumber(), volume.getLabel()));
    }

    @Override
    public void doWaitForSynchronized(final Class<? extends BlockObject> clazz,
            final StorageSystem storageObj, final URI target, final TaskCompleter completer) {
        _log.info("START waitForSynchronized for {}", target);
        try {
            BlockObject targetObj = _dbClient.queryObject(clazz, target);
            CIMObjectPath path = _cimPath.getBlockObjectPath(storageObj, targetObj);
            ControllerServiceImpl.enqueueJob(new QueueJob(new SmisWaitForSynchronizedJob(clazz,
                    path, storageObj.getId(), completer)));
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: " + e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void doWaitForGroupSynchronized(StorageSystem storageObj, List<URI> targets, TaskCompleter completer) {
        _log.info("START waitForSynchronized for {}", targets.get(0));
        try {
            if (storageObj.deviceIsType(Type.vmax)) {
                Volume clone = _dbClient.queryObject(Volume.class, targets.get(0));
                Volume sourceVol = _dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
                String consistencyGroupName = _helper.getConsistencyGroupName(sourceVol, storageObj);
                String replicationGroupName = clone.getReplicationGroupInstance();
                CIMObjectPath groupSynchronized = _cimPath.getGroupSynchronizedPath(storageObj, consistencyGroupName, replicationGroupName);
                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisWaitForGroupSynchronizedJob(groupSynchronized,
                        storageObj.getId(), completer)));
            } else {
                // for VNX
                throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
            }
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: " + e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }

    }

    @Override
    public void doAddToConsistencyGroup(StorageSystem storage, final URI consistencyGroupId,
            final List<URI> blockObjectURIs, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);
        Map<URI, BlockObject> uriToBlockObjectMap = new HashMap<URI, BlockObject>();
        List<URI> replicas = new ArrayList<URI>();
        List<URI> volumes = new ArrayList<URI>();
        try {
            List<BlockObject> blockObjects = new ArrayList<BlockObject>();
            for (URI blockObjectURI : blockObjectURIs) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, blockObjectURI);
                if (blockObject != null) {
                    blockObjects.add(blockObject);
                    uriToBlockObjectMap.put(blockObjectURI, blockObject);
                }
            }
            /**
             * Request: Volume CG with volume objects OR
             * Volume CG with replica objects(snap/clone/mirror)
             * 
             * make sure that the blockObjects are of same type (Volume/Snap/Clone/Mirror)
             * If Volume:
             * add them to group
             * If Replica (supported only for 8.0):
             * If existing replicas do not have replicationGroupInstance set:
             * create new RG on array with random name,
             * add replicas to that RG,
             * set RG name in replicationGroupInstance field for new replicas.
             * Else:
             * Get RG name from existing replica,
             * Add new replicas to DMG with same name as RG name,
             * set RG name in replicationGroupInstance field for new replicas.
             * 
             * For all objects except Clone, set CG URI.
             */
            for (BlockObject blockObject : blockObjects) {
                boolean isFullCopy = false;
                if (blockObject instanceof Volume) {
                    isFullCopy = ControllerUtils.isVolumeFullCopy((Volume) blockObject, _dbClient);
                }
                if (blockObject instanceof BlockSnapshot || isFullCopy
                        || blockObject instanceof BlockMirror) {
                    replicas.add(blockObject.getId());
                } else {
                    volumes.add(blockObject.getId());
                }
            }
            // adding replicas to ReplicationGroup is supported only for 8.0
            if (!storage.getUsingSmis80() && !replicas.isEmpty()) {
                String errMsg = "Adding replicas to Consistency Group is not supported on 4.6.x Provider";
                _log.warn(errMsg);
                taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                        .failedToAddMembersToConsistencyGroup(consistencyGroup.getLabel(),
                                consistencyGroup.fetchArrayCgName(storage.getId()), errMsg));
                return;
            }

            if (!volumes.isEmpty() && !replicas.isEmpty()) {
                String errMsg = "Mix of Volumes and Replica types is not supported";
                _log.warn(errMsg);
                taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                        .failedToAddMembersToConsistencyGroup(consistencyGroup.getLabel(),
                                consistencyGroup.getCgNameOnStorageSystem(storage.getId()), errMsg));
                return;
            }
            if (!replicas.isEmpty()) {
                addReplicasToConsistencyGroup(storage, consistencyGroup, replicas, uriToBlockObjectMap);
            } else if (!volumes.isEmpty()) {
                // get source provider for SRDF target volumes
                // target CG is created using source system provider
                StorageSystem forProvider = storage;
                boolean isSrdfTarget = false;
                Volume vol = (Volume) uriToBlockObjectMap.get(volumes.iterator().next());
                if (vol.checkForSRDF() && !NullColumnValueGetter.isNullNamedURI(vol.getSrdfParent())) {
                    Volume srcVolume = _dbClient.queryObject(Volume.class, vol.getSrdfParent().getURI());
                    forProvider = _dbClient.queryObject(StorageSystem.class, srcVolume.getStorageController());
                    isSrdfTarget = true;
                }

                // Check if the consistency group exists
                boolean createCG = false;
                CIMObjectPath cgPath = null;
                CIMInstance cgPathInstance = null;
                boolean isVPlex = consistencyGroup.checkForType(Types.VPLEX);
                String groupName = _helper.getConsistencyGroupName(consistencyGroup, storage);
                // If this is for VPlex, we would create backend consistency group if it does not exist yet.
                if (groupName == null || groupName.isEmpty()) {
                    if (isVPlex) {
                        createCG = true;
                        _log.info(String.format("No consistency group exists for the storage: %s", storage.getId()));
                    } else {
                        ServiceError error = DeviceControllerErrors.smis.noConsistencyGroupWithGivenName();
                        taskCompleter.error(_dbClient, error);
                        return;
                    }
                } else {
                    if (!isSrdfTarget) {
                        StorageSystem storageSystem = findProviderFactory.withGroup(storage, groupName).find();
                        if (storageSystem == null) {
                            if (isVPlex) {
                                _log.info(String.format("Could not find consistency group with the name: %s", groupName));
                                createCG = true;
                            } else {
                                ServiceError error = DeviceControllerErrors.smis.noConsistencyGroupWithGivenName();
                                taskCompleter.error(_dbClient, error);
                                return;
                            }
                        } else {
                            forProvider = storageSystem;
                        }
                    }
                    if (!createCG) {
                        cgPath = _cimPath.getReplicationGroupPath(forProvider, storage.getSerialNumber(), groupName);
                        cgPathInstance = _helper.checkExists(forProvider, cgPath, false, false);
                        // If there is no consistency group with the given name, set the
                        // operation to error
                        if (cgPathInstance == null) {
                            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                                    .consistencyGroupNotFound(consistencyGroup.getLabel(),
                                            consistencyGroup.getCgNameOnStorageSystem(storage.getId())));
                            return;
                        }
                    }
                }
                if (createCG) {
                    doCreateConsistencyGroup(storage, consistencyGroupId, null);
                    consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class,
                            consistencyGroupId);
                    groupName = _helper.getConsistencyGroupName(consistencyGroup, storage);
                    cgPath = _cimPath.getReplicationGroupPath(storage, groupName);
                }

                CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
                String[] blockObjectNames = _helper.getBlockObjectAlternateNames(volumes);
                // Smis call to add volumes that are already available in Group, will result in error.
                Set<String> blockObjectsToAdd = _helper.filterVolumesAlreadyPartOfReplicationGroup(
                        forProvider, cgPath, blockObjectNames);
                if (!blockObjectsToAdd.isEmpty()) {
                    CIMObjectPath[] members = _cimPath.getVolumePaths(storage,
                            blockObjectsToAdd.toArray(new String[blockObjectsToAdd.size()]));
                    CIMArgument[] addMembersInput = _helper.getAddMembersInputArguments(cgPath, members);
                    CIMArgument[] output = new CIMArgument[5];
                    _helper.invokeMethod(forProvider, replicationSvc, SmisConstants.ADD_MEMBERS,
                            addMembersInput, output);
                } else {
                    _log.info("Requested volumes {} are already part of the Replication Group {}, hence skipping AddMembers call..",
                            Joiner.on(", ").join(blockObjectNames), groupName);
                }

                for (URI volume : volumes) {
                    BlockObject volumeObject = uriToBlockObjectMap.get(volume);
                    volumeObject.setConsistencyGroup(consistencyGroupId);
                    _dbClient.updateAndReindexObject(volumeObject);
                }

                // refresh target provider to update its view on target CG
                if (isSrdfTarget) {
                    refreshStorageSystem(storage.getId(), null);
                }
            }
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Problem in adding volumes to Consistency Group {}", consistencyGroupId, e);
            // Remove any references to the consistency group
            for (URI volume : volumes) {
                BlockObject volumeObject = uriToBlockObjectMap.get(volume);
                volumeObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                _dbClient.updateAndReindexObject(volumeObject);
            }
            // Remove replication group instance
            for (URI replica : replicas) {
                BlockObject replicaObject = uriToBlockObjectMap.get(replica);
                replicaObject.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                if (!(replicaObject instanceof Volume && ControllerUtils.isVolumeFullCopy((Volume) replicaObject, _dbClient))) {
                    replicaObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
                _dbClient.updateAndReindexObject(replicaObject);
            }
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .failedToAddMembersToConsistencyGroup(consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId()), e.getMessage()));
        }
    }

    /**
     * Adds the replicas to consistency group.
     */
    private void addReplicasToConsistencyGroup(StorageSystem storage,
            BlockConsistencyGroup consistencyGroup, List<URI> replicas,
            Map<URI, BlockObject> uriToBlockObjectMap) throws Exception {
        String replicationGroupName = ControllerUtils.getGroupNameFromReplicas(
                replicas, consistencyGroup, _dbClient);
        if (replicationGroupName == null) {
            // create Replication Group with random name
            _log.info("Creating Replication Group for replicas");
            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            CIMArgument[] inArgs = _helper.getCreateReplicationGroupInputArguments(null);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, replicationSvc, SmisConstants.CREATE_GROUP, inArgs,
                    outArgs);
            // Grab the generated name from the instance.
            final String instanceID = (String) _cimPath
                    .getCimObjectPathFromOutputArgs(outArgs, CP_REPLICATION_GROUP)
                    .getKey(CP_INSTANCE_ID).getValue();
            // VMAX instanceID, e.g., 000196700567+EMC_SMI_RG1414546375042 (8.0.2 provider)
            final String groupName = instanceID.split(Constants.PATH_DELIMITER_REGEX)[storage.getUsingSmis80() ? 1 : 0];
            replicationGroupName = groupName;
            _log.info("Group name generated: {}", groupName);

            _log.info("Adding replicas to Replication Group {}", groupName);
            CIMObjectPath cgPath = _cimPath.getReplicationGroupPath(storage, groupName);
            String[] blockObjectNames = _helper.getBlockObjectAlternateNames(replicas);
            CIMObjectPath[] members = _cimPath.getVolumePaths(storage, blockObjectNames);
            CIMArgument[] addMembersInput = _helper.getAddMembersInputArguments(cgPath, members);
            CIMArgument[] output = new CIMArgument[5];
            _helper.invokeMethod(storage, replicationSvc, SmisConstants.ADD_MEMBERS,
                    addMembersInput, output);
        } else {
            // 8.0.3 will support adding replicas to consistency groups but the replicas should be added to the
            // device masking group corresponding to the consistency group
            _log.info("Adding replicas to Device Masking Group equivalent to its ReplicationGroup {}",
                    replicationGroupName);
            List<URI> replicasToAdd = _helper.filterReplicasAlreadyPartOfReplicationGroup(
                    storage, replicationGroupName, replicas);
            if (!replicasToAdd.isEmpty()) {
                CIMArgument[] inArgsAdd = _helper.getAddVolumesToMaskingGroupInputArguments(storage,
                        replicationGroupName, replicasToAdd, null, true);
                CIMArgument[] outArgsAdd = new CIMArgument[5];
                _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                        SmisConstants.ADD_MEMBERS, inArgsAdd, outArgsAdd, null);
            } else {
                _log.info("Requested replicas {} are already part of the Replication Group {}, hence skipping AddMembers call..",
                        Joiner.on(", ").join(replicas), replicationGroupName);
            }
        }
        // persist group name in Replica objects
        for (URI replica : replicas) {
            BlockObject replicaObject = uriToBlockObjectMap.get(replica);
            replicaObject.setReplicationGroupInstance(replicationGroupName);
            // don't set CG on Clones
            if (!(replicaObject instanceof Volume && ControllerUtils.isVolumeFullCopy((Volume) replicaObject, _dbClient))) {
                replicaObject.setConsistencyGroup(consistencyGroup.getId());
            } else if (replicaObject instanceof BlockSnapshot) {
                String snapSetLabel = ControllerUtils.getSnapSetLabelFromExistingSnaps(replicationGroupName, _dbClient);
                // set the snapsetLabel for the snapshots to add
                if (null != snapSetLabel) {
                    ((BlockSnapshot) replicaObject).setSnapsetLabel(snapSetLabel);
                } else {
                    ((BlockSnapshot) replicaObject).setSnapsetLabel(replicationGroupName);
                }
            }
            _dbClient.updateAndReindexObject(replicaObject);
        }
    }

    @Override
    public void doRemoveFromConsistencyGroup(StorageSystem storage,
            final URI consistencyGroupId, final List<URI> blockObjects,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class,
                consistencyGroupId);
        try {
            // Check if the consistency group exists
            String groupName = _helper.getConsistencyGroupName(consistencyGroup, storage);
            storage = findProviderFactory.withGroup(storage, groupName).find();

            if (storage == null) {
                ServiceError error = DeviceControllerErrors.smis.noConsistencyGroupWithGivenName();
                taskCompleter.error(_dbClient, error);
                return;
            }

            CIMObjectPath cgPath = _cimPath.getReplicationGroupPath(storage, groupName);
            CIMInstance cgPathInstance = _helper.checkExists(storage, cgPath, false, false);
            // If there is no consistency group with the given name, set the
            // operation to error
            if (cgPathInstance == null) {
                taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                        .consistencyGroupNotFound(consistencyGroup.getLabel(),
                                consistencyGroup.getCgNameOnStorageSystem(storage.getId())));
                return;
            }
            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            String[] blockObjectNames = _helper.getBlockObjectAlternateNames(blockObjects);
            CIMObjectPath[] members = _cimPath.getVolumePaths(storage, blockObjectNames);
            CIMArgument[] removeMembersInput = _helper.getRemoveMembersInputArguments(cgPath,
                    members);
            CIMArgument[] output = new CIMArgument[5];
            _helper.invokeMethod(storage, replicationSvc, SmisConstants.REMOVE_MEMBERS,
                    removeMembersInput, output);
            // Remove any references to the consistency group
            for (URI blockObjectURI : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, blockObjectURI);
                if (blockObject != null) {
                    blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
                _dbClient.persistObject(blockObject);
            }
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Problem while removing volume from CG :{}", consistencyGroupId, e);
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .failedToRemoveMembersToConsistencyGroup(consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId()), e.getMessage()));
        }
    }

    @Override
    public void doAddToReplicationGroup(final StorageSystem storage, final URI consistencyGroupId, final String replicationGroupName,
            final List<URI> replicas, final TaskCompleter taskCompleter) throws DeviceControllerException {
        // 8.0.3 will support adding replicas to replication group but the replicas should be added to the
        // device masking group corresponding to the consistency group
        _log.info("Adding replicas to Device Masking Group equivalent to its ReplicationGroup {}",
                replicationGroupName);
        List<URI> replicasToAdd;
        try {
            replicasToAdd = _helper.filterReplicasAlreadyPartOfReplicationGroup(
                    storage, replicationGroupName, replicas);

            if (!replicasToAdd.isEmpty()) {
                CIMArgument[] inArgsAdd;
                inArgsAdd = _helper.getAddVolumesToMaskingGroupInputArguments(storage,
                        replicationGroupName, replicasToAdd, null, true);

                CIMArgument[] outArgsAdd = new CIMArgument[5];
                _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                        SmisConstants.ADD_MEMBERS, inArgsAdd, outArgsAdd, null);
            } else {
                _log.info("Requested replicas {} are already part of the Replication Group {}, hence skipping AddMembers call..",
                        Joiner.on(", ").join(replicas), replicationGroupName);
            }

            // persist group name/settings instance (for VMAX3) in replica objects
            List<BlockObject> replicaList = new ArrayList<BlockObject>();
            String settingsInst = null;
            if (storage.checkIfVmax3() && URIUtil.isType(replicas.get(0), BlockSnapshot.class)) {
                List<BlockSnapshot> blockSnapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(replicationGroupName, _dbClient);
                if (blockSnapshots != null && !blockSnapshots.isEmpty()) {
                    settingsInst = blockSnapshots.get(0).getSettingsInstance();
                }
            }

            for (URI replica : replicas) {
                BlockObject replicaObj = BlockObject.fetch(_dbClient, replica);

                replicaObj.setReplicationGroupInstance(replicationGroupName);
                // don't set CG on Clones
                if (replicaObj instanceof BlockMirror || replicaObj instanceof BlockSnapshot) {
                    replicaObj.setConsistencyGroup(consistencyGroupId);

                    if (replicaObj instanceof BlockMirror) {
                        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class,
                                consistencyGroupId);
                        String groupName = _helper.getConsistencyGroupName(consistencyGroup, storage);
                        CIMObjectPath syncPath = _cimPath.getGroupSynchronizedPath(storage, groupName, replicationGroupName);
                        ((BlockMirror) replicaObj).setSynchronizedInstance(syncPath.toString());
                    } else if (replicaObj instanceof BlockSnapshot) {
                        if (settingsInst != null) {
                            ((BlockSnapshot) replicaObj).setSettingsInstance(settingsInst);
                        }
                    }
                }

                replicaList.add(replicaObj);
            }

            _dbClient.updateAndReindexObject(replicaList);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            if (null != replicas && !replicas.isEmpty()) {
                for (URI replicaURI : replicas) {
                    BlockObject blockObj = _dbClient.queryObject(BlockObject.class, replicaURI);
                    blockObj.setReplicationGroupInstance(null);
                    _dbClient.updateObject(blockObj);
                }
            }
            _log.error("Problem while adding replica to device masking group :{}", consistencyGroupId, e);
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .failedToAddMembersToReplicationGroup(replicationGroupName,
                            storage.getLabel(), e.getMessage()));
            return;
        }
    }

    @Override
    public void doRemoveFromReplicationGroup(StorageSystem storage,
            URI consistencyGroupId, String replicationGroupName, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("Removing replicas from Device Masking Group equivalent to its ReplicationGroup {}",
                replicationGroupName);

        try {
            BlockObject replica = BlockObject.fetch(_dbClient, blockObjects.get(0));
            CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, replica.getReplicationGroupInstance(),
                    SmisConstants.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);

            List<URI> replicasPartOfGroup = _helper.findVolumesInReplicationGroup(storage, maskingGroupPath, blockObjects);
            if (replicasPartOfGroup.isEmpty()) {
                _log.info("Replicas {} have already been removed from Device Masking Group {}",
                        Joiner.on(", ").join(blockObjects), maskingGroupPath);
            } else {
                String[] members = _helper.getBlockObjectAlternateNames(replicasPartOfGroup);
                CIMObjectPath[] memberPaths = _cimPath.getVolumePaths(storage, members);
                CIMArgument[] inArgs = _helper.getRemoveAndUnmapMaskingGroupMembersInputArguments(
                        maskingGroupPath, memberPaths, storage, true);
                CIMArgument[] outArgs = new CIMArgument[5];

                _log.info("Invoking remove replicas {} from Device Masking Group equivalent to its Replication Group {}",
                        Joiner.on(", ").join(members), replica.getReplicationGroupInstance());
                _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                        SmisConstants.REMOVE_MEMBERS, inArgs, outArgs, null);
            }

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.info("Failed to remove from replication group", e);
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .failedToRemoveMembersFromReplicationGroup(replicationGroupName,
                            storage.getLabel(), e.getMessage()));
        }
    }

    @Override
    public void doAddVolumePairsToCg(StorageSystem system, List<URI> sourceURIs, URI remoteDirectorGroupURI,
            boolean forceAdd, TaskCompleter completer) {
        _srdfOperations.addVolumePairsToCg(system, sourceURIs, remoteDirectorGroupURI, forceAdd, completer);
    }

    @Override
    public void doCreateLink(final StorageSystem system, final URI sourceURI,
            final URI targetURI, final TaskCompleter completer) {
        _srdfOperations.createSRDFVolumePair(system, sourceURI, targetURI, completer);
    }

    @Override
    public void doCreateListReplicas(StorageSystem system, List<URI> sources, List<URI> targets, TaskCompleter completer) {
        _srdfOperations.createListReplicas(system, sources, targets, completer);
    }

    @Override
    public void doDetachLink(final StorageSystem system, final URI sourceURI,
            final URI targetURI, final boolean onGroup, final TaskCompleter completer) {
        Volume target = _dbClient.queryObject(Volume.class, targetURI);
        _srdfOperations.performDetach(system, target, onGroup, completer);
    }

    @Override
    public void doRemoveDeviceGroups(final StorageSystem system, final URI sourceURI,
            final URI targetURI, final TaskCompleter completer) {
        _srdfOperations.removeDeviceGroups(system, sourceURI, targetURI, completer);
    }

    @Override
    public void doRollbackLinks(final StorageSystem system,
            final List<URI> sourceURIs, final List<URI> targetURIs,
            final boolean isGroupRollback, final TaskCompleter completer) {
        _srdfOperations.rollbackSRDFMirrors(system, sourceURIs, targetURIs, isGroupRollback, completer);
    }

    @Override
    public void doSplitLink(final StorageSystem system, final Volume targetVolume, boolean rollback,
            final TaskCompleter completer) {
        _srdfOperations.performSplit(system, targetVolume, completer);
    }

    @Override
    public void doSuspendLink(StorageSystem system, Volume targetVolume, boolean consExempt, TaskCompleter completer) {
        _srdfOperations.performSuspend(system, targetVolume, consExempt, completer);
    }

    @Override
    public void doResumeLink(final StorageSystem system, final Volume targetVolume,
            final TaskCompleter completer) {
        _srdfOperations.performEstablish(system, targetVolume, completer);
    }

    @Override
    public void doFailoverLink(final StorageSystem system, final Volume targetVolume,
            final TaskCompleter completer) {
        _srdfOperations.performFailover(system, targetVolume, completer);
    }

    @Override
    public void doFailoverCancelLink(final StorageSystem system, final Volume targetVolume,
            final TaskCompleter completer) {
        _srdfOperations.failoverCancelSyncPair(system, targetVolume, completer);
    }

    @Override
    public void doResyncLink(final StorageSystem system, final URI sourceURI,
            final URI targetURI, final TaskCompleter completer) {
        _srdfOperations.reSyncSRDFSyncVolumePair(system, sourceURI, targetURI, completer);
    }

    @Override
    public void doRemoveVolumePair(final StorageSystem system, final URI sourceURI,
            final URI targetURI, final boolean rollback, final TaskCompleter completer) {
        _srdfOperations.removeSRDFSyncPair(system, sourceURI, targetURI, rollback, completer);
    }

    @Override
    public void doRemoveMirrorFromDeviceMaskingGroup(
            final StorageSystem system, final List<URI> mirrors,
            final TaskCompleter completer) {
        _mirrorOperations.removeMirrorFromDeviceMaskingGroup(system, mirrors, completer);
    }

    @Override
    public void doStartLink(final StorageSystem system, final Volume targetVolume,
            final TaskCompleter completer) {
        _srdfOperations.startSRDFLink(system, targetVolume, completer);
    }

    @Override
    public void doStopLink(final StorageSystem system, final Volume targetVolume,
            final TaskCompleter completer) {
        _srdfOperations.performStop(system, targetVolume, completer);
    }

    @Override
    public void doCreateCgPairs(StorageSystem system, List<URI> sourceURIs, List<URI> targetURIs,
            SRDFMirrorCreateCompleter completer) {
        _srdfOperations.createSRDFCgPairs(system, sourceURIs, targetURIs, completer);
    }

    @Override
    public Set<String> findVolumesPartOfRemoteGroup(StorageSystem system,
            RemoteDirectorGroup rdfGroup) {
        return _srdfOperations.findVolumesPartOfRDFGroups(system, rdfGroup);
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress,
            Integer portNumber) {
        return _helper.validateStorageProviderConnection(ipAddress, portNumber);
    }

    @Override
    public void doSwapVolumePair(StorageSystem system, Volume targetVolume, TaskCompleter completer) {
        _srdfOperations.performSwap(system, targetVolume, completer);

    }

    @Override
    public void updatePolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVpool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {
        _exportMaskOperationsHelper.updateStorageGroupPolicyAndLimits(
                storage, exportMask, volumeURIs, newVpool, rollback, taskCompleter);
    }

    @Override
    public void doTerminateAnyRestoreSessions(StorageSystem storageDevice, URI source, BlockObject snapshot,
            TaskCompleter completer) throws Exception {
        _snapshotOperations.terminateAnyRestoreSessions(storageDevice, snapshot, source, completer);
    }

    @Override
    public ExportMaskPolicy getExportMaskPolicy(StorageSystem storage, ExportMask mask) {
        return _helper.getExportMaskPolicy(storage, mask);
    }

    @Override
    public void doEstablishVolumeNativeContinuousCopyGroupRelation(final StorageSystem storage, final URI sourceVolume,
            final URI mirror, final TaskCompleter taskCompleter) throws DeviceControllerException {
        _mirrorOperations.establishVolumeNativeContinuousCopyGroupRelation(storage, sourceVolume, mirror, taskCompleter);
    }

    @Override
    public void doEstablishVolumeSnapshotGroupRelation(final StorageSystem storage, final URI sourceVolume,
            final URI snapshot, final TaskCompleter taskCompleter) throws DeviceControllerException {
        _snapshotOperations.establishVolumeSnapshotGroupRelation(storage, sourceVolume, snapshot, taskCompleter);
    }

    @Override
    public void doSyncLink(StorageSystem targetSystem, Volume targetVolume, TaskCompleter completer)
            throws Exception {
        _srdfOperations.performRestore(targetSystem, targetVolume, completer);
    }

    @Override
    public void doUpdateSourceAndTargetPairings(List<URI> sourceURIs, List<URI> targetURIs) {
        _srdfOperations.updateSourceAndTargetPairings(sourceURIs, targetURIs);
    }

    @Override
    public void doCreateGroupClone(final StorageSystem storage, final List<URI> cloneVolumes,
            final Boolean createInactive, final TaskCompleter taskCompleter) {
        _cloneOperations.createGroupClone(storage, cloneVolumes, createInactive,
                taskCompleter);
    }

    @Override
    public void doDetachGroupClone(StorageSystem storage, List<URI> cloneVolumes,
            TaskCompleter taskCompleter) {
        Volume clone = _dbClient.queryObject(Volume.class, cloneVolumes.get(0));
        if (clone != null && clone.getReplicaState().equals(ReplicationState.DETACHED.name())) {
            taskCompleter.ready(_dbClient);
            return;
        }
        _cloneOperations.detachGroupClones(storage, cloneVolumes, taskCompleter);

    }

    @Override
    public void doEstablishVolumeFullCopyGroupRelation(final StorageSystem storage, final URI sourceVolume,
            final URI fullCopy, final TaskCompleter taskCompleter) throws DeviceControllerException {
        _cloneOperations.establishVolumeCloneGroupRelation(storage, sourceVolume, fullCopy, taskCompleter);
    }

    @Override
    public void doRestoreFromGroupClone(StorageSystem storageSystem,
            List<URI> clones, TaskCompleter taskCompleter) {
        _cloneOperations.restoreGroupClones(storageSystem, clones, taskCompleter);

    }

    @Override
    public void doActivateGroupFullCopy(StorageSystem storage,
            List<URI> fullCopy, TaskCompleter completer) {
        _cloneOperations.activateGroupClones(storage, fullCopy, completer);

    }

    @Override
    public void doResyncGroupClone(StorageSystem storageDevice,
            List<URI> clone, TaskCompleter completer) throws Exception {
        _cloneOperations.resyncGroupClones(storageDevice, clone, completer);
    }

    @Override
    public void doFractureGroupClone(StorageSystem storageDevice, List<URI> clone,
            TaskCompleter completer) throws Exception {
        _cloneOperations.fractureGroupClones(storageDevice, clone, completer);
    }

    @Override
    public void refreshStorageSystem(final URI systemURI, List<URI> volumeURIs) {
        _srdfOperations.refreshStorageSystem(systemURI, volumeURIs);
    }

    /**
     * Before the clone could be deleted, if the clone is from a CG, we will
     * remove the target group, then reset the replicationGroupInstance for the clones in the group.
     * 
     * @param storage
     * @param clones
     * @throws Exception
     */
    private void processClonesBeforeDeletion(StorageSystem storage, Set<Volume> clones) throws Exception {
        _log.info("process clones before deletion");

        for (Volume clone : clones) {
            String groupName = clone.getReplicationGroupInstance();
            if (storage.deviceIsType(Type.vmax) &&
                    NullColumnValueGetter.isNotNullValue(groupName)) {
                CIMObjectPath cgPath = _cimPath.getReplicationGroupPath(storage, clone.getReplicationGroupInstance());
                ReplicationUtils.deleteTargetDeviceGroup(storage, cgPath, _dbClient, _helper, _cimPath);
                URIQueryResultList queryResults = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getCloneReplicationGroupInstanceConstraint(clone
                                .getReplicationGroupInstance()), queryResults);
                Iterator<URI> resultsIter = queryResults.iterator();
                while (resultsIter.hasNext()) {
                    URI cloneUri = resultsIter.next();
                    Volume theClone = _dbClient.queryObject(Volume.class, cloneUri);
                    theClone.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                    _dbClient.persistObject(theClone);
                }

            }
        }
    }

    @Override
    public void doChangeCopyMode(StorageSystem system, Volume target,
            TaskCompleter completer) {
        _srdfOperations.performChangeCopyMode(system, target, completer);
    }

    @Override
    public void doCreateListReplica(StorageSystem storage, List<URI> replicaList, Boolean createInactive, TaskCompleter taskCompleter) {
        _replicaOperations.createListReplica(storage, replicaList, createInactive, taskCompleter);
    }

    @Override
    public void doDetachListReplica(StorageSystem storage, List<URI> replicaList, TaskCompleter taskCompleter) {
        _replicaOperations.detachListReplica(storage, replicaList, taskCompleter);
    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return _exportMaskOperationsHelper.getExportMaskHLUs(storage, exportMask);
    }

    /**
     * This method tests if the array represented by 'storageSystem' supports volume expand.
     * 
     * @param storageSystem [IN] - StorageSystem object to check the volume expand capabilities
     * @return true iff The array indicates that it supports volume expand.
     */
    private boolean doesStorageSystemSupportVolumeExpand(StorageSystem storageSystem) throws WBEMException {
        boolean expandSupported = false;
        CloseableIterator<CIMInstance> cimInstances = null;
        CIMObjectPath storageConfigServicePath = _cimPath.getConfigSvcPath(storageSystem);
        try {
            cimInstances = _helper.getAssociatorInstances(storageSystem, storageConfigServicePath, null,
                    SmisConstants.EMC_STORAGE_CONFIGURATION_CAPABILITIES, null, null,
                    SmisConstants.PS_SUPPORTED_STORAGE_ELEMENT_FEATURES);
            if (cimInstances != null) {
                while (cimInstances.hasNext()) {
                    CIMInstance capabilitiesInstance = cimInstances.next();
                    UnsignedInteger16[] supportedFeatures = (UnsignedInteger16[]) capabilitiesInstance
                            .getPropertyValue(SmisConstants.CP_SUPPORTED_STORAGE_ELEMENT_FEATURES);
                    for (UnsignedInteger16 supportedFeatureEntry : supportedFeatures) {
                        if (supportedFeatureEntry.intValue() == SmisConstants.STORAGE_ELEMENT_CAPACITY_EXPANSION_VALUE) {
                            expandSupported = true;
                            return true;
                        }
                    }
                }
            }
        } finally {
            if (cimInstances != null) {
                cimInstances.close();
            }
            _log.info(String.format("StorageSystem %s %s volume expand", storageSystem.getNativeGuid(),
                    (expandSupported) ? "supports" : "does not support"));
        }
        return false;
    }
    
    @Override
    public void doUntagVolumes(StorageSystem storageSystem, String opId, List<Volume> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            int volumeCount = 0;
            String[] volumeNativeIds = new String[volumes.size()];
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Untag Volume Start - Array:%s", storageSystem.getSerialNumber()));
            MultiVolumeTaskCompleter multiVolumeTaskCompleter = (MultiVolumeTaskCompleter) taskCompleter;            
            for (Volume volume : volumes) {
                logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
                _helper.doApplyRecoverPointTag(storageSystem, volume, false);
            }                        
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e
                    .getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            _log.error("Problem in doUntagVolume: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("doUntagVolume",
                    e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Untag Volume End - Array: %s", storageSystem.getSerialNumber()));
        for (Volume volume : volumes) {
            logMsgBuilder.append(String.format("%nVolume:%s", volume.getLabel()));
        }
        _log.info(logMsgBuilder.toString());        
    }
}
