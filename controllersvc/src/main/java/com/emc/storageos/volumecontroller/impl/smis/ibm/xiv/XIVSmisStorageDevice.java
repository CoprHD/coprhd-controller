/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm.xiv;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MultiVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMCIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMSmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisWaitForSynchronizedJob;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * IBM XIV SMI-S block controller implementation.
 *
 * Key characteristics of IBM XIV array:
 *
 * 1. CIM method may return without exception, but with error code
 * a. return code is checked
 * b. depending on different situations, may throw exception, or ignore the error code
 *
 * 2. all CIM methods are synchronous (there is no job returned from CIM call)
 * a. XIVSmisStorageDevicePostProcessor is called to handle CIM call result,
 * which is handled via SmisXXXJob in case of asynchronous call.
 * b. for some methods (remove members from CG, or create group snapshots),
 * result may not be available immediately after a successful return,
 * in such cases, workaround are made (see IBMSmisSynchSubTaskJob).
 *
 * 3. all XIV volumes are thin provisioned regardless the pool type
 *
 * 4. all volumes in a CG has to be on the same storage pool
 * a. user cannot specify storage pool in CG creation, CG's storage pool association
 * is set implicitly by member volumes
 * b. creating an empty CG on array will result a CG associated to a storage pool
 * that system selected. User cannot change the association afterwards.
 *
 * 5. in mapping, target ports cannot be specified
 * a. target ports can be configured by zoning, so zoning must be done before masking
 *
 * 6. one host could have only one mapping representation on array side
 * a. a mapping on array side may not have any initiator/target port/LUN (empty mapping)
 * b. there could be multiple volumes in the mapping
 * c. one volume can be mapped to multiple hosts
 * d. host name is used on array side if no conflict,
 * otherwise, array side name will be set as tag of the host in ViPR
 *
 * 7. XIV Open API doesn't support creating/exporting to a cluster (an array side cluster)
 * a. a set of volumes can be mapping to multiple hosts via ViPR cluster
 *
 */
public class XIVSmisStorageDevice extends DefaultBlockStorageDevice {
    private static final Logger _log = LoggerFactory
            .getLogger(XIVSmisStorageDevice.class);
    private static final String EMTPY_CG_NAME = " ";
    private DbClient _dbClient;
    protected XIVSmisCommandHelper _helper;
    private IBMCIMObjectPathFactory _cimPath;
    private NameGenerator _nameGenerator;

    // TODO - place holder for now
    private XIVSmisStorageDevicePreProcessor _smisStorageDevicePreProcessor;
    private XIVSmisStorageDevicePostProcessor _smisStorageDevicePostProcessor;

    private ExportMaskOperations _exportMaskOperationsHelper;
    private SnapshotOperations _snapshotOperations;
    private CloneOperations _cloneOperations;
    private boolean isForceSnapshotGroupRemoval = false;

    public void setCimObjectPathFactory(
            final IBMCIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(final DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setSmisCommandHelper(
            final XIVSmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setNameGenerator(final NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    public void setSmisStorageDevicePreProcessor(
            final XIVSmisStorageDevicePreProcessor smisStorageDevicePreProcessor) {
        _smisStorageDevicePreProcessor = smisStorageDevicePreProcessor;
    }

    public void setSmisStorageDevicePostProcessor(
            final XIVSmisStorageDevicePostProcessor smisStorageDevicePostProcessor) {
        _smisStorageDevicePostProcessor = smisStorageDevicePostProcessor;
    }

    public void setExportMaskOperationsHelper(final ExportMaskOperations exportMaskOperationsHelper) {
        _exportMaskOperationsHelper = exportMaskOperationsHelper;
    }

    public void setSnapshotOperations(final SnapshotOperations snapshotOperations) {
        _snapshotOperations = snapshotOperations;
    }

    public void setCloneOperations(final CloneOperations cloneOperations) {
        _cloneOperations = cloneOperations;
    }

    public void setIsForceSnapshotGroupRemoval(boolean isForceSnapshotGroupRemoval) {
        this.isForceSnapshotGroupRemoval = isForceSnapshotGroupRemoval;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.volumecontroller.BlockStorageDevice#doCreateVolumes
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.StoragePool, java.lang.String,
     * java.util.List, com.emc.storageos.volumecontroller.impl.utils.
     * VirtualPoolCapabilityValuesWrapper,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doCreateVolumes(final StorageSystem storageSystem,
            final StoragePool storagePool, final String opId,
            final List<Volume> volumes,
            final VirtualPoolCapabilityValuesWrapper capabilities,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        Set<URI> volumeURIs = new HashSet<URI>(0);
        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Create Volume Start - Array:%s, Pool:%s",
                storageSystem.getLabel(), storagePool.getNativeId()));

        Volume firstVolume = volumes.get(0);
        Long capacity = firstVolume.getCapacity();
        boolean isThinlyProvisioned = firstVolume.getThinlyProvisioned();

        String tenantName = "";
        try {
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class,
                    firstVolume.getTenant().getURI());
            tenantName = tenant.getLabel();
        } catch (DatabaseException e) {
            _log.error("Error lookup TenantOrg object", e);
        }

        List<String> labels = new ArrayList<String>(volumes.size());
        for (Volume volume : volumes) {
            String label = volume.getLabel();
            logMsgBuilder.append("\nVolume: ").append(label);
            labels.add(_nameGenerator.generate(tenantName, label, volume
                    .getId().toString(), '-',
                    SmisConstants.MAX_VOLUME_NAME_LENGTH));
        }

        _log.info(logMsgBuilder.toString());
        try {
            CIMObjectPath configSvcPath = _cimPath
                    .getConfigSvcPath(storageSystem);
            CIMArgument[] inArgs = _helper.getCreateVolumesInputArguments(
                    storageSystem, storagePool, labels, capacity,
                    volumes.size(), isThinlyProvisioned);

            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storageSystem, configSvcPath,
                    SmisConstants.CREATE_OR_MODIFY_ELEMENTS_FROM_STORAGE_POOL,
                    inArgs, outArgs);
            volumeURIs = _smisStorageDevicePostProcessor
                    .processVolumeCreation(storageSystem, storagePool.getId(),
                            volumes, outArgs);

            if (!volumeURIs.isEmpty()) {
                // see SmisAbstractCreateVolumeJob.addVolumeToConsistencyGroup
                // All the volumes will be in the same consistency group
                final URI consistencyGroupId = firstVolume.getConsistencyGroup();
                if (consistencyGroupId != null) {
                    addVolumesToCG(storageSystem, consistencyGroupId, new ArrayList<URI>(volumeURIs), true);
                }
            }

            taskCompleter.ready(_dbClient);
        } catch (final InternalException e) {
            _log.error("Problem in doCreateVolumes: ", e);
            taskCompleter.error(_dbClient, e);
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError serviceError = DeviceControllerErrors.smis
                    .unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        } catch (Exception e) {
            _log.error("Problem in doCreateVolumes: ", e);
            ServiceError serviceError = DeviceControllerErrors.smis
                    .methodFailed("doCreateVolumes", e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        }

        List<Volume> volumesToSave = new ArrayList<Volume>();
        for (URI id : taskCompleter.getIds()) {
            if (!volumeURIs.contains(id)) {
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to create volume: %s", opId,
                        id.toString()));
                Volume volume = _dbClient.queryObject(Volume.class, id);
                volume.setInactive(true);
                volumesToSave.add(volume);
            }
        }

        if (!volumesToSave.isEmpty()) {
            _dbClient.persistObject(volumesToSave);
        }

        logMsgBuilder = new StringBuilder(String.format(
                "Create Volumes End - Array:%s, Pool:%s",
                storageSystem.getLabel(), storagePool.getNativeId()));
        for (Volume volume : volumes) {
            logMsgBuilder
                    .append(String.format("%nVolume:%s", volume.getLabel()));
        }
        _log.info(logMsgBuilder.toString());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.BlockStorageDevice#doExpandVolume
     * (com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.StoragePool,
     * com.emc.storageos.db.client.model.Volume, java.lang.Long,
     * com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public void doExpandVolume(final StorageSystem storageSystem,
            final StoragePool pool, final Volume volume, final Long size,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info(String
                .format("Expand Volume Start - Array: %s, Pool: %s, Volume: %s, New size: %d",
                        storageSystem.getLabel(), pool.getNativeId(),
                        volume.getLabel(), size));
        try {
            CIMObjectPath configSvcPath = _cimPath
                    .getConfigSvcPath(storageSystem);
            CIMArgument[] inArgs = _helper.getExpandVolumeInputArguments(
                    storageSystem, volume, size);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storageSystem, configSvcPath,
                    SmisConstants.CREATE_OR_MODIFY_ELEMENT_FROM_STORAGE_POOL,
                    inArgs, outArgs);
            _smisStorageDevicePostProcessor.processVolumeExpansion(
                    storageSystem, pool.getId(), volume.getId(), outArgs);
            taskCompleter.ready(_dbClient);
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis
                    .unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            _log.error("Problem in doExpandVolume: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "doExpandVolume", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info(String.format(
                "Expand Volume End - Array: %s, Pool: %s, Volume: %s",
                storageSystem.getLabel(), pool.getNativeId(), volume.getLabel()));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.volumecontroller.BlockStorageDevice#doDeleteVolumes
     * (com.emc.storageos.db.client.model.StorageSystem, java.lang.String,
     * java.util.List, com.emc.storageos.volumecontroller.TaskCompleter)
     * 
     * Note: assume volumes could be from different consistency groups
     */
    @Override
    public void doDeleteVolumes(final StorageSystem storageSystem,
            final String opId, final List<Volume> volumes,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<String> volumeNativeIds = new ArrayList<String>();
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Delete Volume Start - Array:%s", storageSystem.getLabel()));
            MultiVolumeTaskCompleter multiVolumeTaskCompleter = (MultiVolumeTaskCompleter) taskCompleter;
            for (Volume volume : volumes) {
                logMsgBuilder.append(String.format("%nVolume:%s",
                        volume.getLabel()));

                if (volume.getConsistencyGroup() != null) {
                    BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());
                    if (cg != null && cg.getTypes() != null && cg.getTypes().contains(BlockConsistencyGroup.Types.LOCAL.name())) {
                        removeVolumeFromConsistencyGroup(storageSystem, cg, volume);
                    }
                }

                CIMInstance volumeInstance = _helper.checkExists(storageSystem,
                        volume, false, false);
                if (volumeInstance == null) {
                    // related volume state (if any) has been deleted. skip
                    // processing, if already
                    // deleted from array.
                    _log.info(String.format("Volume %s already deleted: ",
                            volume.getNativeId()));
                    volume.setInactive(true);
                    _dbClient.persistObject(volume);
                    VolumeTaskCompleter deleteTaskCompleter = multiVolumeTaskCompleter
                            .skipTaskCompleter(volume.getId());
                    deleteTaskCompleter.ready(_dbClient);
                    continue;
                }
                // Compare the volume labels of the to-be-deleted and existing
                // volumes
                /**
                 * This will fail in the case when the user just changes the
                 * label of the volume...till we subscribe to indications from
                 * the provider, we will live with that.
                 */
                String volToDeleteLabel = volume.getDeviceLabel();
                String volInstanceLabel = CIMPropertyFactory.getPropertyValue(
                        volumeInstance, SmisConstants.CP_ELEMENT_NAME);
                if (volToDeleteLabel != null && volInstanceLabel != null
                        && !volToDeleteLabel.equals(volInstanceLabel)) {
                    // related volume state (if any) has been deleted. skip
                    // processing, if already
                    // deleted from array.
                    _log.info("VolToDeleteLabel {} : volInstancelabel {}",
                            volToDeleteLabel, volInstanceLabel);
                    _log.info(String.format("Volume %s already deleted: ",
                            volume.getNativeId()));
                    volume.setInactive(true);
                    // clear the associated consistency groups from the volume
                    volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());

                    // TODO - is this needed, or just persistObject? use bulk
                    // update
                    _dbClient.updateAndReindexObject(volume);
                    VolumeTaskCompleter deleteTaskCompleter = multiVolumeTaskCompleter
                            .skipTaskCompleter(volume.getId());
                    deleteTaskCompleter.ready(_dbClient);
                    continue;
                }
                volumeNativeIds.add(volume.getNativeId());
            }

            _log.info(logMsgBuilder.toString());
            // execute SMI-S Call , only if any Volumes left for deletion.
            if (!multiVolumeTaskCompleter.isVolumeTaskCompletersEmpty()) {
                CIMObjectPath configSvcPath = _cimPath
                        .getConfigSvcPath(storageSystem);
                CIMArgument[] inArgs = _helper.getDeleteVolumesInputArguments(
                        storageSystem, volumeNativeIds.toArray(new String[volumeNativeIds.size()]));
                CIMArgument[] outArgs = new CIMArgument[5];
                _helper.invokeMethod(storageSystem, configSvcPath,
                        SmisConstants.RETURN_ELEMENTS_TO_STORAGE_POOL, inArgs,
                        outArgs);
                _smisStorageDevicePostProcessor.processVolumeDeletion(
                        storageSystem, volumes, outArgs,
                        multiVolumeTaskCompleter);
            }

            // If we are here, there are no more volumes to delete, we have
            // invoked ready() for the VolumeDeleteCompleter, and told
            // the multiVolumeTaskCompleter to skip these completers.
            // In this case, the multiVolumeTaskCompleter complete()
            // method will not be invoked and the result is that the
            // workflow that initiated this delete request will never
            // be updated. So, here we just call complete() on the
            // multiVolumeTaskCompleter to ensure the workflow status is
            // updated.
            multiVolumeTaskCompleter.ready(_dbClient);
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis
                    .unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (DeviceControllerException e) {
            taskCompleter.error(_dbClient, e);
        } catch (Exception e) {
            _log.error("Problem in doDeleteVolume: ", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "doDeleteVolume", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        StringBuilder logMsgBuilder = new StringBuilder(String.format(
                "Delete Volume End - Array: %s", storageSystem.getLabel()));
        for (Volume volume : volumes) {
            logMsgBuilder
                    .append(String.format("%nVolume:%s", volume.getLabel()));
        }
        _log.info(logMsgBuilder.toString());
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
     * 
     * @param targets not used
     */
    @Override
    public void doExportGroupCreate(final StorageSystem storage, final ExportMask exportMask,
            final Map<URI, Integer> volumeMap, final List<Initiator> initiators,
            final List<URI> targets, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportGroupCreate START ...", storage.getLabel());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumeMap, _dbClient);
        _exportMaskOperationsHelper.createExportMask(storage, exportMask.getId(), volumeLunArray,
                targets, initiators, taskCompleter);
        _log.info("{} doExportGroupCreate END ...", storage.getLabel());
    }

    @Override
    public void doExportGroupDelete(final StorageSystem storage, final ExportMask exportMask,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportGroupDelete START ...", storage.getLabel());
        _exportMaskOperationsHelper.deleteExportMask(storage, exportMask.getId(),
                new ArrayList<URI>(), new ArrayList<URI>(), new ArrayList<Initiator>(),
                taskCompleter);
        _log.info("{} doExportGroupDelete END ...", storage.getLabel());
    }

    @Override
    public void doExportAddVolume(final StorageSystem storage, final ExportMask exportMask,
            final URI volume, final Integer lun, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddVolume START ...", storage.getLabel());
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        map.put(volume, lun);
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), map, _dbClient);
        _exportMaskOperationsHelper.addVolume(storage, exportMask.getId(), volumeLunArray,
                taskCompleter);
        _log.info("{} doExportAddVolume END ...", storage.getLabel());
    }

    @Override
    public void doExportAddVolumes(final StorageSystem storage, final ExportMask exportMask,
            final Map<URI, Integer> volumes, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddVolume START ...", storage.getLabel());
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), volumes, _dbClient);
        _exportMaskOperationsHelper.addVolume(storage, exportMask.getId(), volumeLunArray,
                taskCompleter);
        _log.info("{} doExportAddVolume END ...", storage.getLabel());
    }

    @Override
    public void doExportRemoveVolume(final StorageSystem storage, final ExportMask exportMask,
            final URI volume, final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveVolume START ...", storage.getLabel());
        _exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(),
                Arrays.asList(volume), taskCompleter);
        _log.info("{} doExportRemoveVolume END ...", storage.getLabel());
    }

    @Override
    public void doExportRemoveVolumes(final StorageSystem storage, final ExportMask exportMask,
            final List<URI> volumes, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportRemoveVolume START ...", storage.getLabel());
        _exportMaskOperationsHelper.removeVolume(storage, exportMask.getId(), volumes,
                taskCompleter);
        _log.info("{} doExportRemoveVolume END ...", storage.getLabel());
    }

    @Override
    public void doExportAddInitiator(final StorageSystem storage, final ExportMask exportMask,
            final Initiator initiator, final List<URI> targets, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportAddInitiator START ...", storage.getLabel());
        _exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(),
                Arrays.asList(initiator), targets, taskCompleter);
        _log.info("{} doExportAddInitiator END ...", storage.getLabel());
    }

    @Override
    public void doExportAddInitiators(final StorageSystem storage, final ExportMask exportMask,
            final List<Initiator> initiators, final List<URI> targets,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportAddInitiator START ...", storage.getLabel());
        _exportMaskOperationsHelper.addInitiator(storage, exportMask.getId(), initiators, targets,
                taskCompleter);
        _log.info("{} doExportAddInitiator END ...", storage.getLabel());
    }

    @Override
    public void doExportRemoveInitiator(final StorageSystem storage, final ExportMask exportMask,
            final Initiator initiator, final List<URI> targets, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiator START ...", storage.getLabel());
        _exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(),
                Arrays.asList(initiator), targets, taskCompleter);
        _log.info("{} doExportRemoveInitiator END ...", storage.getLabel());
    }

    @Override
    public void doExportRemoveInitiators(final StorageSystem storage, final ExportMask exportMask,
            final List<Initiator> initiators, final List<URI> targets,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} doExportRemoveInitiator START ...", storage.getLabel());
        _exportMaskOperationsHelper.removeInitiator(storage, exportMask.getId(), initiators,
                targets, taskCompleter);
        _log.info("{} doExportRemoveInitiator END ...", storage.getLabel());
    }

    /**
     * Return a mapping of the port name to the URI of the ExportMask in which
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
    public boolean validateStorageProviderConnection(String ipAddress,
            Integer portNumber) {
        return _helper.validateStorageProviderConnection(ipAddress, portNumber);
    }

    @Override
    public void doConnect(final StorageSystem storage) {
        try {
            _helper.getConnection(storage);
        } catch (Exception e) {
            throw new IllegalStateException("No cim connection for "
                    + storage.getIpAddress(), e);
        }
    }

    @Override
    public void doDisconnect(final StorageSystem storage) {
    }

    @Override
    public void doCreateSnapshot(final StorageSystem storage,
            final List<URI> snapshotList, final Boolean createInactive,
            Boolean readOnly, final TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(
                    BlockSnapshot.class, snapshotList);
            if (inReplicationGroup(snapshots)) {
                _snapshotOperations.createGroupSnapshots(storage, snapshotList,
                        createInactive, readOnly, taskCompleter);
            } else {
                URI snapshot = snapshots.get(0).getId();
                _snapshotOperations.createSingleVolumeSnapshot(storage,
                        snapshot, createInactive, readOnly, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String
                    .format("IO exception when trying to create snapshot(s) on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "doCreateSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doRestoreFromSnapshot(final StorageSystem storage,
            final URI volume, final URI snapshot,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(
                    BlockSnapshot.class, Arrays.asList(snapshot));
            if (inReplicationGroup(snapshots)) {
                _snapshotOperations.restoreGroupSnapshots(storage, volume,
                        snapshot, taskCompleter);
            } else {
                _snapshotOperations.restoreSingleVolumeSnapshot(storage,
                        volume, snapshot, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String
                    .format("IO exception when trying to restore snapshot(s) on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "doRestoreFromSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doDeleteSnapshot(final StorageSystem storage,
            final URI snapshot, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(
                    BlockSnapshot.class, Arrays.asList(snapshot));
            if (inReplicationGroup(snapshots)) {
                _snapshotOperations.deleteGroupSnapshots(storage, snapshot,
                        taskCompleter);
            } else {
                _snapshotOperations.deleteSingleVolumeSnapshot(storage,
                        snapshot, taskCompleter);
            }
        } catch (DatabaseException e) {
            String message = String
                    .format("IO exception when trying to delete snapshot(s) on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "doDeleteSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doCreateClone(final StorageSystem storage,
            final URI sourceVolume, final URI cloneVolume,
            final Boolean createInactive, final TaskCompleter taskCompleter) {
        _cloneOperations.createSingleClone(storage, sourceVolume, cloneVolume,
                createInactive, taskCompleter);
    }

    @Override
    public void doActivateFullCopy(final StorageSystem storageSystem,
            final URI fullCopy, final TaskCompleter completer) {
        _cloneOperations
                .activateSingleClone(storageSystem, fullCopy, completer);
    }

    @Override
    public void doDetachClone(final StorageSystem storage,
            final URI cloneVolume, final TaskCompleter taskCompleter) {
        _cloneOperations.detachSingleClone(storage, cloneVolume, taskCompleter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.volumecontroller.AbstractBlockStorageDevice#doCreateConsistencyGroup(com.emc.storageos.db.client.model.StorageSystem
     * , java.net.URI, com.emc.storageos.volumecontroller.TaskCompleter)
     * 
     * Note: this won't create CG on array side, it just associate CG with array
     * CG will be created on array side when adding first volumes to
     */
    @Override
    public void doCreateConsistencyGroup(final StorageSystem storage,
            final URI consistencyGroupId, String replicationGroupName, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // cannot create CG here, as there is no way to specify storage pool
        // need to create CG with member volumes in addVolumesToCG
        try {
            BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(
                    BlockConsistencyGroup.class, consistencyGroupId);
            // just try to find out if there CG name is already used on array, so to fail the CG creation here
            // rather than after volumes are created
            String label = consistencyGroup.getLabel();
            String query = String.format(
                    "Select * From %s Where ElementName=\"%s\"",
                    IBMSmisConstants.CP_CONSISTENCY_GROUP, label);
            CIMObjectPath cgPath = CimObjectPathCreator.createInstance(
                    IBMSmisConstants.CP_CONSISTENCY_GROUP,
                    Constants.IBM_NAMESPACE, null);
            List<CIMInstance> cgInstances = _helper.executeQuery(storage,
                    cgPath, query, "WQL");
            if (!cgInstances.isEmpty()) {
                _log.error("Failed to create consistency group: " + IBMSmisConstants.DUPLICATED_CG_NAME_ERROR);
                ServiceError error = DeviceControllerErrors.smis.methodFailed(
                        "doCreateConsistencyGroup", IBMSmisConstants.DUPLICATED_CG_NAME_ERROR);
                taskCompleter.error(_dbClient, error);
                return;
            }

            consistencyGroup.addSystemConsistencyGroup(storage.getId().toString(), EMTPY_CG_NAME);
            consistencyGroup.setStorageController(storage.getId());
            consistencyGroup.addConsistencyGroupTypes(Types.LOCAL.name());
            _dbClient.persistObject(consistencyGroup);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Failed to create consistency group: " + e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "doCreateConsistencyGroup", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doDeleteConsistencyGroup(final StorageSystem storage,
            final URI consistencyGroupId, String replicationGroupName, Boolean keepRGName, Boolean markInactive, final TaskCompleter taskCompleter)
            throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(
                BlockConsistencyGroup.class, consistencyGroupId);
        try {
            // Check if the consistency group does exist
            String groupName = _helper
                    .getConsistencyGroupName(consistencyGroup, storage);
            if (!groupName.equals(EMTPY_CG_NAME)) {
                CIMObjectPath cgPath = _cimPath.getConsistencyGroupPath(
                        storage, groupName);
                CIMInstance cgPathInstance = _helper.checkExists(storage,
                        cgPath, false, false);
                if (cgPathInstance != null) {
                    @SuppressWarnings("rawtypes")
                    CIMArgument[] inArgs = _helper
                            .getDeleteReplicationGroupInputArguments(storage,
                                    cgPath);
                    _helper.callReplicationSvc(storage,
                            IBMSmisConstants.DELETE_GROUP, inArgs,
                            new CIMArgument[5]);
                }
            }
            // Set the consistency group to inactive
            consistencyGroup.removeSystemConsistencyGroup(storage.getId().toString(), groupName);
            if (markInactive) {
                consistencyGroup.setInactive(true);
            }
            _dbClient.persistObject(consistencyGroup);
            // Set task to ready
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.info("Failed to delete consistency group: " + e);
            // Set task to error
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "doDeleteConsistencyGroup", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }
    
    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage, final URI consistencyGroupId,
            String replicationGroupName, Boolean keepRGName, Boolean markInactive, 
            String sourceReplicationGroup, final TaskCompleter taskCompleter) throws DeviceControllerException {
        doDeleteConsistencyGroup(storage, consistencyGroupId, replicationGroupName, keepRGName, markInactive, taskCompleter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.volumecontroller.AbstractBlockStorageDevice#doAddToConsistencyGroup(com.emc.storageos.db.client.model.StorageSystem
     * , java.net.URI, java.util.List, com.emc.storageos.volumecontroller.TaskCompleter)
     * Note - all block objects should be on the same storage pool
     */
    @Override
    public void doAddToConsistencyGroup(final StorageSystem storage,
            final URI consistencyGroupId, String replicationGroupName, final List<URI> blockObjects,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(
                BlockConsistencyGroup.class, consistencyGroupId);
        try {
            addVolumesToCG(storage, consistencyGroupId, blockObjects, false);
            List<BlockObject> objectsToSave = new ArrayList<BlockObject>();
            for (URI blockObjectURI : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(_dbClient,
                        blockObjectURI);
                if (blockObject != null) {
                    blockObject.setConsistencyGroup(consistencyGroupId);
                    objectsToSave.add(blockObject);
                }
            }

            if (!objectsToSave.isEmpty()) {
                _dbClient.updateAndReindexObject(objectsToSave);
            }

            taskCompleter.ready(_dbClient);
        } catch (DeviceControllerException e) {
            // if there is no consistency group with the given name, set the
            // operation to error
            taskCompleter.error(_dbClient, e);
        } catch (Exception e) {
            // remove any references to the consistency group
            List<BlockObject> objectsToSave = new ArrayList<BlockObject>();
            for (URI blockObjectURI : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(_dbClient,
                        blockObjectURI);
                if (blockObject != null) {
                    if (blockObject.getConsistencyGroup() != null) {
                        blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    }

                    objectsToSave.add(blockObject);
                }
            }

            if (!objectsToSave.isEmpty()) {
                _dbClient.persistObject(objectsToSave);
            }

            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .failedToAddMembersToConsistencyGroup(
                            consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId()), e.getMessage()));
        }
    }

    @Override
    public void doRemoveFromConsistencyGroup(final StorageSystem storage,
            final URI consistencyGroupId, final List<URI> blockObjects,
            final TaskCompleter taskCompleter) throws DeviceControllerException {
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(
                BlockConsistencyGroup.class, consistencyGroupId);
        try {
            // check if the consistency group already exists
            String groupName = _helper
                    .getConsistencyGroupName(consistencyGroup, storage);
            CIMObjectPath cgPath = _cimPath.getConsistencyGroupPath(storage,
                    groupName);
            CIMInstance cgPathInstance = _helper.checkExists(storage, cgPath,
                    false, false);

            if (cgPathInstance != null) {
                String[] blockObjectIds = _helper.getBlockObjectNativeIds(blockObjects);
                removeVolumesFromCG(storage, consistencyGroup, cgPath, blockObjectIds);
            }

            // remove any references to the consistency group
            List<BlockObject> objectsToSave = new ArrayList<BlockObject>();
            for (URI blockObjectURI : blockObjects) {
                BlockObject blockObject = BlockObject.fetch(_dbClient,
                        blockObjectURI);
                if (blockObject != null) {
                    if (blockObject.getConsistencyGroup() != null) {
                        blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    }
                }

                objectsToSave.add(blockObject);
            }

            _dbClient.persistObject(objectsToSave);
            taskCompleter.ready(_dbClient);
        } catch (DeviceControllerException e) {
            taskCompleter.error(_dbClient, e);
        } catch (Exception e) {
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions
                    .failedToRemoveMembersToConsistencyGroup(
                            consistencyGroup.getLabel(),
                            consistencyGroup.getCgNameOnStorageSystem(storage.getId()), e.getMessage()));
        }
    }

    @Override
    public void doWaitForSynchronized(final Class<? extends BlockObject> clazz,
            final StorageSystem storageObj, final URI target,
            final TaskCompleter completer) {
        _log.info("START waitForSynchronized for {}", target);
        CIMObjectPath path = IBMSmisConstants.NULL_IBM_CIM_OBJECT_PATH;
        try {
            if (!clazz.equals(Volume.class)) {
                BlockObject targetObj = _dbClient.queryObject(clazz, target);
                path = _cimPath.getBlockObjectPath(storageObj,
                        targetObj);
            }
            ControllerServiceImpl.enqueueJob(new QueueJob(
                    new SmisWaitForSynchronizedJob(clazz, path, storageObj
                            .getId(), completer)));
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: " + e);
            ServiceError serviceError = DeviceControllerException.errors
                    .jobFailed(e);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void doWaitForGroupSynchronized(StorageSystem storageObj, List<URI> target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public Integer checkSyncProgress(final URI storage, final URI source,
            final URI target) throws DeviceControllerException {
        _log.info("checkSyncProgress for source: {} target: {}", source, target);
        String percentSyncValue = null;
        try {
            StorageSystem storageSystem = _dbClient.queryObject(
                    StorageSystem.class, storage);
            BlockObject targetObject = BlockObject.fetch(_dbClient, target);
            CIMObjectPath syncObject = _cimPath.getSyncObject(storageSystem,
                    targetObject);
            CIMInstance syncInstance = _helper.getInstance(storageSystem,
                    syncObject, false, false, SmisConstants.PS_PERCENT_SYNCED);
            percentSyncValue = CIMPropertyFactory.getPropertyValue(
                    syncInstance, SmisConstants.CP_PERCENT_SYNCED);
            if (_log.isDebugEnabled()) {
                _log.debug("Got progress {}", percentSyncValue);
            }

            return Integer.parseInt(percentSyncValue);
        } catch (Exception e) {
            String msg = String.format(
                    "Failed to check synchronization progress for %s (%s)",
                    target, percentSyncValue);
            _log.error(msg, e);
        }
        return null;
    }

    /**
     * Given a list of BlockSnapshot objects, determine if they were created as
     * part of a consistency group.
     *
     * @param snapshotList
     *            [required] - List of BlockSnapshot objects
     * @return true if the BlockSnapshots were created as part of volume
     *         consistency group.
     */
    private boolean inReplicationGroup(final List<BlockSnapshot> snapshotList) {
        boolean isCgCreate = false;
        if (snapshotList.size() == 1) {
            BlockSnapshot snapshot = snapshotList.get(0);
            URI cgUri = snapshot.getConsistencyGroup();
            if (cgUri != null) {
                final BlockConsistencyGroup group = _dbClient.queryObject(
                        BlockConsistencyGroup.class, cgUri);
                isCgCreate = group != null;
            }
        } else if (snapshotList.size() > 1) {
            isCgCreate = true;
        }
        return isCgCreate;
    }

    /**
     * Method will remove the volume from the consistency group to which it
     * currently belongs.
     *
     * @param storage
     *            [required] - StorageSystem object
     * @param volume
     *            [required] - Volume object
     */
    private void removeVolumeFromConsistencyGroup(final StorageSystem storage, BlockConsistencyGroup cg,
            final Volume volume) throws Exception {
        CloseableIterator<CIMObjectPath> assocVolNamesIter = null;
        try {
            String groupName = _helper.getConsistencyGroupName(volume, storage);
            CIMObjectPath cgPath = _cimPath.getConsistencyGroupPath(storage,
                    groupName);
            CIMInstance cgInstance = _helper.checkExists(storage, cgPath,
                    false, false);
            if (cgInstance != null) {
                boolean volumeIsInGroup = false;
                assocVolNamesIter = _helper.getAssociatorNames(storage, cgPath,
                        null, SmisConstants.CIM_STORAGE_VOLUME, null, null); // TODO add association name
                while (assocVolNamesIter.hasNext()) {
                    CIMObjectPath assocVolPath = assocVolNamesIter.next();
                    String deviceId = assocVolPath
                            .getKey(SmisConstants.CP_DEVICE_ID).getValue()
                            .toString();
                    if (deviceId.equalsIgnoreCase(volume.getNativeId())) {
                        volumeIsInGroup = true;
                        break;
                    }
                }

                if (volumeIsInGroup) {
                    removeVolumesFromCG(storage, cg, cgPath, new String[] { volume.getNativeId() });
                } else {
                    _log.info(
                            "Volume {} is no longer in the replication group {}",
                            volume.getNativeId(), cgPath.toString());
                }
            } else {
                _log.warn(
                        "The Consistency Group {} does not exist on the array.",
                        groupName);
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

    private void addVolumesToCG(StorageSystem storageSystem,
            URI consistencyGroupId, List<URI> volumeURIs, boolean isVolumeCreation) throws Exception {
        BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(
                BlockConsistencyGroup.class, consistencyGroupId);

        // if no cg, OR cg is not of type LOCAL, the volumes are not part of a
        // consistency group, just return
        if (consistencyGroup == null
                || !consistencyGroup.created()
                || (consistencyGroup.getTypes() != null && !consistencyGroup
                        .getTypes().contains(
                                BlockConsistencyGroup.Types.LOCAL.name()))) {
            if (!isVolumeCreation) {
                throw DeviceControllerException.exceptions
                        .consistencyGroupNotFound(consistencyGroup.getLabel(),
                                consistencyGroup.getCgNameOnStorageSystem(storageSystem
                                        .getId()));
            }
            else {
                _log.info("Skipping addVolumesToCG: Volumes are not part of a consistency group");
                return;
            }
        }

        String groupName = _helper.getConsistencyGroupName(consistencyGroup,
                storageSystem);
        if (groupName.equals(EMTPY_CG_NAME)) { // may also check if CG
                                               // instance
            // exists on array, or not, if
            // not, re-create it here
            // need to create CG group here with member volumes
            // this will ensure the new CG is associated to right pool
            // without member volumes, there is no way to control which pool the
            // CG will be associated to
            CIMArgument[] inArgs = _helper
                    .getCreateReplicationGroupInputArguments(storageSystem,
                            consistencyGroup.getLabel(), volumeURIs);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.callReplicationSvc(storageSystem,
                    SmisConstants.CREATE_GROUP, inArgs, outArgs);
            // grab the CG name from the instance ID and store it in the db
            final CIMObjectPath cgPath = _cimPath
                    .getCimObjectPathFromOutputArgs(outArgs,
                            SmisConstants.CP_REPLICATION_GROUP);
            final String deviceName = _helper.getReplicationGroupName(cgPath);
            // the order of adding and removing system consistency group makes different
            // somehow, removing before adding won't work
            consistencyGroup.addSystemConsistencyGroup(storageSystem.getId().toString(), deviceName);
            consistencyGroup.removeSystemConsistencyGroup(storageSystem.getId()
                    .toString(), EMTPY_CG_NAME);
            _dbClient.persistObject(consistencyGroup);
        } else {
            // existing CG, add volumes to the CG
            CIMObjectPath cgPath = _cimPath.getConsistencyGroupPath(
                    storageSystem, groupName);
            CIMInstance cgPathInstance = _helper.checkExists(storageSystem,
                    cgPath, false, false);
            // if there is no consistency group with the given name, set the
            // operation to error
            if (cgPathInstance == null) {
                throw DeviceControllerException.exceptions
                        .consistencyGroupNotFound(consistencyGroup.getLabel(),
                                consistencyGroup.getCgNameOnStorageSystem(storageSystem
                                        .getId()));
            }

            _helper.addVolumesToConsistencyGroup(storageSystem,
                    new ArrayList<URI>(volumeURIs), cgPath);
        }
    }

    private void removeVolumesFromCG(StorageSystem storage, BlockConsistencyGroup cg,
            CIMObjectPath cgPath, String[] blockObjectIds) throws Exception {
        CIMObjectPath[] volumePaths = _cimPath.getVolumePaths(storage, blockObjectIds);
        @SuppressWarnings("rawtypes")
        CIMArgument[] inArgs = null;
        // get all the snapshot groups
        CIMObjectPath[] syncObjectPaths = _helper.getGroupSyncObjectPaths(
                storage, cgPath);
        if (syncObjectPaths.length > 0) {
            if (isForceSnapshotGroupRemoval) {
                inArgs = _helper.getDeleteListSynchronizationInputArguments(
                        storage, syncObjectPaths);
                // delete snapshot groups
                _helper.callReplicationSvc(storage,
                        IBMSmisConstants.MODIFY_LIST_SYNCHRONIZATION, inArgs,
                        new CIMArgument[5]);
            } else {
                String instanceId = (String) cgPath.getKey(
                        SmisConstants.CP_INSTANCE_ID).getValue();
                String cgName = _helper.getReplicationGroupName(cgPath);
                throw DeviceControllerException.exceptions
                        .failedToRemoveMembersToConsistencyGroup(cgName,
                                instanceId, "Consistency group has snapshot(s)");
            }
        }

        inArgs = _helper.getRemoveMembersInputArguments(cgPath, volumePaths);
        Exception removeMembersException = null;
        try {
            // remove from consistency group
            _helper.callReplicationSvc(storage, SmisConstants.REMOVE_MEMBERS,
                    inArgs, new CIMArgument[5]);
        } catch (Exception e) {
            _log.info("Exception on removeVolumesFromConsistencyGroup: " + e.getMessage());
            removeMembersException = e;
        }

        Set<String> volumeSet = new HashSet<String>(Arrays.asList(blockObjectIds));
        Set<String> members = null;
        try {
            members = _helper.getCGMembers(storage, cgPath, volumeSet);
        } catch (Exception e) {
            _log.info("Exception on getCGMembers: " + e.getMessage());
            if (removeMembersException != null) {
                throw removeMembersException;
            }
            else {
                throw e;
            }
        }

        String groupName = _helper.getConsistencyGroupName(cg, storage);
        boolean cgExists = true;
        if (members == null) {
            // no CG member on array side, it should have already been deleted
            cgExists = false;
        } else if (members.isEmpty()) {
            // this shouldn't happen, delete CG
            _log.info("Delete CG " + groupName);
            inArgs = _helper.getDeleteReplicationGroupInputArguments(storage, cgPath);
            _helper.callReplicationSvc(storage, IBMSmisConstants.DELETE_GROUP, inArgs, new CIMArgument[5]);
            cgExists = false;
        }

        if (!cgExists) {
            // now remove array association of the CG in ViPR
            // the intention is that the CG can be re-used, and not restricted by previous pool
            _log.info("CG is empty on array. Remove array association from the CG");
            cg.removeSystemConsistencyGroup(storage.getId().toString(), groupName);
            // clear the LOCAL type
            StringSet types = cg.getTypes();
            if (types != null) {
                types.remove(Types.LOCAL.name());
                cg.setTypes(types);
            }

            _dbClient.persistObject(cg);
        }
    }

    @Override
    public ExportMaskPolicy getExportMaskPolicy(StorageSystem storage,
            ExportMask mask) {
        // TODO Auto-generated method stub
        return null;
    }

}
