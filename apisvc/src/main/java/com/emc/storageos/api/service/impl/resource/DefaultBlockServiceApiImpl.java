/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toCompletedTask;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getBlockSnapshotByConsistencyGroup;
import static java.text.MessageFormat.format;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam.VolumeGroupVolumeList;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.vpool.VirtualPoolChangeList;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ApplicationAddVolumeList;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
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
        // Call super first.
        super.cleanupForViPROnlyDelete(volumeDescriptors);

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
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void updateVolumesInVolumeGroup(VolumeGroupVolumeList addVolumes, 
                                           List<Volume>removeVolumes, 
                                           URI volumeGroupId,
                                           String taskId) {
        VolumeGroup volumeGroup = _dbClient.queryObject(VolumeGroup.class, volumeGroupId);
        ApplicationAddVolumeList addVolumesNotInCG = null;
        List<URI> removeVolumesURI = null;
        BlockController controller = null;
        URI systemURI = null;
        Volume firstVolume = null;
        if (addVolumes != null && addVolumes.getVolumes() != null && !addVolumes.getVolumes().isEmpty()) {
            addVolumesNotInCG = addVolumesToApplication(addVolumes, volumeGroup, taskId);
            if (firstVolume == null) {
                List<URI> vols = addVolumesNotInCG.getVolumes();
                if (vols != null && !vols.isEmpty()) {
                    URI voluri = vols.get(0);
                    firstVolume = _dbClient.queryObject(Volume.class, voluri);
                }
            }
        }

        if (removeVolumes != null && !removeVolumes.isEmpty()) {
            removeVolumesURI = new ArrayList<URI> ();
            for (Volume vol : removeVolumes) {
                removeVolumesURI.add(vol.getId());
            }
            if (firstVolume == null) {
                firstVolume = removeVolumes.get(0);
            }
               
        } 
        if ((addVolumesNotInCG != null && !addVolumesNotInCG.getVolumes().isEmpty()) ||
                (removeVolumesURI != null && !removeVolumesURI.isEmpty())){
            systemURI = firstVolume.getStorageController();
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
            controller = getController(BlockController.class, system.getSystemType());
            controller.updateApplication(systemURI, addVolumesNotInCG, removeVolumesURI, volumeGroup.getId(), taskId);
        } else {
            // No need to call to controller. update the application task
            Operation op = volumeGroup.getOpStatus().get(taskId);
            op.ready();
            volumeGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(volumeGroup);
        }
    }

    /**
     * Update volumes with volumeGroup Id, if the volumes are in the CG
     * @param volumesList The add volume list
     * @param application The application that the volumes are added to
     * @return ApplicationVolumeList The volumes that are in the add volume list, but not in any consistency group yet,
     *          or VNX CG volumes.
     */
    private ApplicationAddVolumeList addVolumesToApplication(VolumeGroupVolumeList volumeList, VolumeGroup application, String taskId) {
        ApplicationAddVolumeList addVolumeList = new ApplicationAddVolumeList() ;

        Map<URI, List<URI>> addCGVolsMap = new HashMap<URI, List<URI>>();
        for (URI voluri : volumeList.getVolumes()) {
            Volume volume = _dbClient.queryObject(Volume.class, voluri);
            if (volume == null || volume.getInactive()) {
                _log.info(String.format("The volume %s does not exist or has been deleted", voluri));
                continue;
            }
            URI cgUri = volume.getConsistencyGroup();
            if (!NullColumnValueGetter.isNullURI(cgUri)) {
                List<URI> vols = addCGVolsMap.get(cgUri);
                if (vols == null) {
                    vols = new ArrayList<URI>();
                }
                vols.add(voluri);
                addCGVolsMap.put(cgUri, vols);
            } else {
                // The volume is not in CG
                URI addingCgURI = volumeList.getConsistencyGroup();
                
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, addingCgURI);
                if (cg == null || cg.getInactive()) {
                    throw APIException.badRequests.volumeGroupCantBeUpdated(application.getLabel(), 
                            String.format("The specified consistency group %s is not valid", addingCgURI.toString()));
                }
                // Check if the volume is from the same storage system as others.
                List<URI> checkedVolumes = addVolumeList.getVolumes();
                if (!checkedVolumes.isEmpty()) {
                    Volume firstVol = _dbClient.queryObject(Volume.class, checkedVolumes.get(0));
                    if (!volume.getStorageController().toString().equals(firstVol.getStorageController().toString())) {
                        throw APIException.badRequests.volumeGroupCantBeUpdated(application.getLabel(),
                                "the volumes in the add volume list is not from the same storage system");
                    }
                }
                // Check if the volume has any replica
                List<BlockSnapshot> snapshots = getSnapshots(volume);
                StringSet mirrors = volume.getMirrors();
                StringSet fullCopyIds = volume.getFullCopies();
                if ((snapshots != null && !snapshots.isEmpty()) ||
                        (mirrors != null && !mirrors.isEmpty()) ||
                        (fullCopyIds != null && !fullCopyIds.isEmpty())) {
                    throw APIException.badRequests.volumeGroupCantBeUpdated(application.getLabel(),
                            String.format("the volumes %s has replica. please remove all replicas from the volume", volume.getLabel()));
                }
                checkedVolumes.add(voluri);
                if (addVolumeList.getConsistencyGroup()== null) {
                    addVolumeList.setConsistencyGroup(addingCgURI);
                }
                addVolumeList.setReplicationGroupName(volumeList.getReplicationGroupName());
            }
        }
        
        Set<URI> vnxCGVols = new HashSet<URI>();
        for (Map.Entry<URI, List<URI>> entry : addCGVolsMap.entrySet()) {
            URI cgUri = entry.getKey();
            List<URI> cgVolsToAdd = entry.getValue();

            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
            List<Volume> cgVolumes = getActiveCGVolumes(cg);
            Volume firstVolume = cgVolumes.get(0);

            Set<URI> cgVolumeURIs = new HashSet<URI>();
            for (Volume cgVol : cgVolumes) {
                cgVolumeURIs.add(cgVol.getId());
            }

            // Check if all CG volumes are adding into the application
            if(!cgVolsToAdd.isEmpty() && !cgVolumeURIs.containsAll(cgVolsToAdd) || cgVolsToAdd.size() != cgVolumeURIs.size()) {
                throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(firstVolume.getLabel(),
                        "not all volumes in consistency group are in the add volume list");
            }

            if (ControllerUtils.isVnxVolume(firstVolume, _dbClient) && !ControllerUtils.isInVNXVirtualRG(firstVolume, _dbClient)) {
                // VNX CG cannot have snapshots, user has to remove the snapshots first in order to add the CG to an application
                URIQueryResultList cgSnapshotsResults = new URIQueryResultList();
                _dbClient.queryByConstraint(getBlockSnapshotByConsistencyGroup(cgUri), cgSnapshotsResults);
                Iterator<URI> cgSnapshotsIter = cgSnapshotsResults.iterator();
                while (cgSnapshotsIter.hasNext()) {
                    BlockSnapshot cgSnapshot = _dbClient.queryObject(BlockSnapshot.class, cgSnapshotsIter.next());
                    if ((cgSnapshot != null) && (!cgSnapshot.getInactive())) {
                        throw APIException.badRequests.notAllowedWhenVNXCGHasSnapshot();
                    }
                }

                vnxCGVols.addAll(cgVolumeURIs);
            } else {
                for (Volume cgVol : cgVolumes) {
                    StringSet applications = cgVol.getVolumeGroupIds();
                    if (applications == null) {
                        applications = new StringSet();
                    }
                    applications.add(application.getId().toString());
                    cgVol.setVolumeGroupIds(applications);
                    Operation op = cgVol.getOpStatus().get(taskId);
                    op.ready();
                    cgVol.getOpStatus().updateTaskStatus(taskId, op);
                }
                _dbClient.updateObject(cgVolumes);
            }

            addVolumeList.getVolumes().addAll(vnxCGVols);
        }

        _log.info("Added volumes in CG to the application" );
        return addVolumeList;
    }
    
    /**
     * Creates tasks against consistency group associated with a request and adds them to the given task list.
     *
     * @param group
     * @param taskList
     * @param taskId
     * @param operationTypeEnum
     */
    protected void addConsistencyGroupTask(URI groupUri, TaskList taskList,
            String taskId,
            ResourceOperationTypeEnum operationTypeEnum) {
        BlockConsistencyGroup group = _dbClient.queryObject(BlockConsistencyGroup.class, groupUri);
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, group.getId(), taskId,
                operationTypeEnum);
        taskList.getTaskList().add(TaskMapper.toTask(group, taskId, op));
    }
    
    /**
     * Creates tasks against consistency groups associated with a request and adds them to the given task list.
     *
     * @param group
     * @param taskList
     * @param taskId
     * @param operationTypeEnum
     */
    protected void addVolumeTask(Volume volume, TaskList taskList,
            String taskId,
            ResourceOperationTypeEnum operationTypeEnum) {
        Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId,
                operationTypeEnum);
        taskList.getTaskList().add(TaskMapper.toTask(volume, taskId, op));
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.BlockServiceApi#getReplicationGroupNames(com.emc.storageos.db.client.model.VolumeGroup)
     */
    @Override
    public Collection<? extends String> getReplicationGroupNames(VolumeGroup group) {
        List<String> groupNames = new ArrayList<String>();
        final List<Volume> volumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(_dbClient, Volume.class,
                        AlternateIdConstraint.Factory.getVolumesByVolumeGroupId(group.getId().toString()));
        for (Volume volume : volumes) {
            if (volume.getReplicationGroupInstance() != null) {
                groupNames.add(volume.getReplicationGroupInstance());
            }
        }
        return groupNames;
    }
    
}
