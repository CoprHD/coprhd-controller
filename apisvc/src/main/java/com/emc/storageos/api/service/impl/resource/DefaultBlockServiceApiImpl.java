/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toCompletedTask;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static java.text.MessageFormat.format;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.vpool.VirtualPoolChangeList;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * Block Service subtask (parts of larger operations) default implementation.
 */
public class DefaultBlockServiceApiImpl extends AbstractBlockServiceApiImpl<StorageScheduler> {
    private static final Logger _log = LoggerFactory.getLogger(DefaultBlockServiceApiImpl.class);

    public DefaultBlockServiceApiImpl() {
        super(null);
    }

    private List<VolumeDescriptor> prepareVolumeDescriptors(List<Volume> volumes, VirtualPoolCapabilityValuesWrapper cosCapabilities) {

        // Build up a list of VolumeDescriptors based on the volumes
        final List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        for (Volume volume : volumes) {
            VolumeDescriptor desc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                    volume.getStorageController(), volume.getId(),
                    volume.getPool(), volume.getConsistencyGroup(), cosCapabilities);
            volumeDescriptors.add(desc);
        }

        return volumeDescriptors;
    }

    @Override
    public TaskList createVolumes(VolumeCreate param, Project project, VirtualArray neighborhood,
            VirtualPool cos, List<Recommendation> recommendations, TaskList taskList,
            String task, VirtualPoolCapabilityValuesWrapper cosCapabilities) throws InternalException {
        // Prepare the Bourne Volumes to be created and associated
        // with the actual storage system volumes created. Also create
        // a BlockTaskList containing the list of task resources to be
        // returned for the purpose of monitoring the volume creation
        // operation for each volume to be created.
        int volumeCounter = 0;
        String volumeLabel = param.getName();
        List<Volume> preparedVolumes = new ArrayList<Volume>();
        final BlockConsistencyGroup consistencyGroup = cosCapabilities.getBlockConsistencyGroup() == null ? null : _dbClient
                .queryObject(BlockConsistencyGroup.class, cosCapabilities.getBlockConsistencyGroup());

        // Prepare the volumes
        _scheduler.prepareRecommendedVolumes(param, task, taskList, project,
                neighborhood, cos, cosCapabilities.getResourceCount(), recommendations,
                consistencyGroup, volumeCounter, volumeLabel, preparedVolumes, cosCapabilities, false);

        // Prepare the volume descriptors based on the recommendations
        final List<VolumeDescriptor> volumeDescriptors = prepareVolumeDescriptors(preparedVolumes, cosCapabilities);

        // Log volume descriptor information
        logVolumeDescriptorPrecreateInfo(volumeDescriptors, task);

        final BlockOrchestrationController controller = getController(BlockOrchestrationController.class,
                BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);

        try {
            // Execute the volume creations requests
            controller.createVolumes(volumeDescriptors, task);
        } catch (InternalException e) {
            _log.error("Controller error when creating volumes", e);
            failVolumeCreateRequest(task, taskList, preparedVolumes, e.getMessage());
            throw e;
        } catch (Exception e) {
            _log.error("Controller error when creating volumes", e);
            failVolumeCreateRequest(task, taskList, preparedVolumes, e.getMessage());
            throw e;
        }

        return taskList;
    }

    private void failVolumeCreateRequest(String task, TaskList taskList, List<Volume> preparedVolumes, String errorMsg) {
        String errorMessage = String.format("Controller error: %s", errorMsg);
        for (TaskResourceRep volumeTask : taskList.getTaskList()) {
            volumeTask.setState(Operation.Status.error.name());
            volumeTask.setMessage(errorMessage);
            Operation statusUpdate = new Operation(Operation.Status.error.name(), errorMessage);
            _dbClient.updateTaskOpStatus(Volume.class, volumeTask.getResource()
                    .getId(), task, statusUpdate);
        }
        for (Volume volume : preparedVolumes) {
            volume.setInactive(true);
            _dbClient.persistObject(volume);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void deleteVolumes(final URI systemURI, final List<URI> volumeURIs,
            final String deletionType, final String task) throws InternalException {
        _log.info("Request to delete {} volume(s)", volumeURIs.size());
        super.deleteVolumes(systemURI, volumeURIs, deletionType, task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void cleanupForViPROnlyDelete(List<VolumeDescriptor> volumeDescriptors) {
        // Clean up the relationship between volumes that are full
        // copies and and their source volumes.
        BlockFullCopyManager.cleanUpFullCopyAssociations(volumeDescriptors, _dbClient);
    }

    // Connectivity for a VNX/VMAX array is determined by the various protection systems.
    // This method calls all the known protection systems to find out which are covering
    // this StorageArray.
    @Override
    public StorageSystemConnectivityList getStorageSystemConnectivity(StorageSystem storageSystem) {
        Map<String, AbstractBlockServiceApiImpl> apiMap = AbstractBlockServiceApiImpl.getProtectionImplementations();
        StorageSystemConnectivityList result = new StorageSystemConnectivityList();
        for (AbstractBlockServiceApiImpl impl : apiMap.values()) {
            if (impl == this)
            {
                continue;     // no infinite recursion
            }
            StorageSystemConnectivityList list = impl.getStorageSystemConnectivity(storageSystem);
            result.getConnections().addAll(list.getConnections());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualPoolChangeList getVirtualPoolForVirtualPoolChange(Volume volume) {

        // VirtualPool change is only potentially supported for VNX and VMAX block.
        // So, in this case we throw a bad request exception.
        URI volumeSystemURI = volume.getStorageController();
        StorageSystem volumeSystem = _dbClient.queryObject(StorageSystem.class,
                volumeSystemURI);
        String systemType = volumeSystem.getSystemType();
        if (!DiscoveredDataObject.Type.vmax.name().equals(systemType)
                && !DiscoveredDataObject.Type.vnxblock.name().equals(systemType)
                && !DiscoveredDataObject.Type.hds.name().equals(systemType)
                && !DiscoveredDataObject.Type.xtremio.name().equals(systemType)) {
            throw APIException.badRequests.changesNotSupportedFor("VirtualPool",
                    format("volumes on storage systems of type {0}", systemType));
        }

        return getVirtualPoolChangeListForVolume(volume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperations(Volume volume, VirtualPool volumeVirtualPool,
            VirtualPool newVirtualPool, StringBuffer notSuppReasonBuff) {
        List<VirtualPoolChangeOperationEnum> allowedOperations = new ArrayList<VirtualPoolChangeOperationEnum>();

        if (VirtualPool.vPoolSpecifiesHighAvailability(newVirtualPool) &&
                VirtualPoolChangeAnalyzer.isVPlexImport(volume, volumeVirtualPool, newVirtualPool, notSuppReasonBuff) &&
                VirtualPoolChangeAnalyzer.doesVplexVpoolContainVolumeStoragePool(volume, newVirtualPool, notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.NON_VPLEX_TO_VPLEX);
        }

        if (VirtualPool.vPoolSpecifiesProtection(newVirtualPool) &&
                VirtualPoolChangeAnalyzer.isSupportedRPVolumeVirtualPoolChange(volume,
                        volumeVirtualPool, newVirtualPool, _dbClient, notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.RP_PROTECTED);
        }

        if (VirtualPool.vPoolSpecifiesSRDF(newVirtualPool) &&
                VirtualPoolChangeAnalyzer.isSupportedSRDFVolumeVirtualPoolChange(volume,
                        volumeVirtualPool, newVirtualPool, _dbClient, notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.SRDF_PROTECED);
        }

        if (VirtualPool.vPoolSpecifiesMirrors(newVirtualPool, _dbClient)
                &&
                VirtualPoolChangeAnalyzer.isSupportedAddMirrorsVirtualPoolChange(volume, volumeVirtualPool, newVirtualPool, _dbClient,
                        notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.ADD_MIRRORS);
        }

        return allowedOperations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskResourceRep deleteConsistencyGroup(StorageSystem device,
            BlockConsistencyGroup consistencyGroup, String task) throws ControllerException {

        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, consistencyGroup.getId(),
                task, ResourceOperationTypeEnum.DELETE_CONSISTENCY_GROUP);

        BlockController controller = getController(BlockController.class,
                device.getSystemType());
        controller.deleteConsistencyGroup(device.getId(), consistencyGroup.getId(), Boolean.TRUE, task);

        return toTask(consistencyGroup, task, op);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskResourceRep updateConsistencyGroup(StorageSystem device,
            List<Volume> cgVolumes, BlockConsistencyGroup consistencyGroup,
            List<URI> addVolumesList, List<URI> removeVolumesList, String task)
            throws ControllerException {

        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class,
                consistencyGroup.getId(), task,
                ResourceOperationTypeEnum.UPDATE_CONSISTENCY_GROUP);

        if (!device.getSystemType().equals(DiscoveredDataObject.Type.scaleio.name())) {
            BlockController controller = getController(BlockController.class,
                    device.getSystemType());
            controller.updateConsistencyGroup(device.getId(), consistencyGroup.getId(),
                    addVolumesList, removeVolumesList, task);
            return toTask(consistencyGroup, task, op);
        } else {
            // ScaleIO does not have explicit CGs, so we can just update the database and complete
            List<Volume> addVolumes = _dbClient.queryObject(Volume.class, addVolumesList, true);
            for (Volume volume : addVolumes) {
                volume.setConsistencyGroup(consistencyGroup.getId());
            }

            List<Volume> removeVolumes = _dbClient.queryObject(Volume.class, removeVolumesList, true);
            for (Volume volume : removeVolumes) {
                volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
            }

            _dbClient.updateAndReindexObject(addVolumes);
            _dbClient.updateAndReindexObject(removeVolumes);
            return toCompletedTask(consistencyGroup, task, op);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI,
            List<URI> volumeURIs, String deletionType) {
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        for (URI volumeURI : volumeURIs) {
            VolumeDescriptor desc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                    systemURI, volumeURI, null, null);
            volumeDescriptors.add(desc);
        }
        return volumeDescriptors;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws ControllerException
     */
    @Override
    public TaskResourceRep establishVolumeAndSnapshotGroupRelation(
            StorageSystem storageSystem, Volume sourceVolume,
            BlockSnapshot snapshot, String taskId) throws ControllerException {

        _log.info("START establish Volume and Snapshot group relation");
        // Create the task on the block snapshot
        Operation op = _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(),
                taskId, ResourceOperationTypeEnum.ESTABLISH_VOLUME_SNAPSHOT);
        snapshot.getOpStatus().put(taskId, op);

        try {
            BlockController controller = getController(BlockController.class,
                    storageSystem.getSystemType());
            controller.establishVolumeAndSnapshotGroupRelation(storageSystem.getId(),
                    sourceVolume.getId(), snapshot.getId(), taskId);
        } catch (ControllerException e) {
            String errorMsg = String.format(
                    "Failed to establish group relation between volume group and snapshot group."
                            + "Source volume: %s, Snapshot: %s",
                    sourceVolume.getId(), snapshot.getId());
            _log.error(errorMsg, e);
            _dbClient.error(BlockSnapshot.class, snapshot.getId(), taskId, e);
        }

        return toTask(snapshot, taskId, op);
    }
}
