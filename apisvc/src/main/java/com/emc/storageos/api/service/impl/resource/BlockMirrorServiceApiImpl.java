/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_MIRROR_TO_URI;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_STRING_TO_URI;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_VOLUME_URI_TO_STR;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Iterables.removeIf;
import static java.lang.String.format;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockMirror.SynchronizationState;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.NativeContinuousCopyCreate;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class BlockMirrorServiceApiImpl extends AbstractBlockServiceApiImpl<StorageScheduler> {
    private static final Logger _log = LoggerFactory.getLogger(BlockMirrorServiceApiImpl.class);

    public BlockMirrorServiceApiImpl() {
        super(null);
    }

    private DefaultBlockServiceApiImpl _defaultBlockServiceApi;

    public void setDefaultBlockServiceApi(DefaultBlockServiceApiImpl defaultBlockServiceApi) {
        _defaultBlockServiceApi = defaultBlockServiceApi;
    }

    /**
     * {@inheritDoc}
     *
     * @throws ControllerException
     */
    @Override
    public TaskList createVolumes(VolumeCreate param, Project project, VirtualArray neighborhood, VirtualPool cos,
                                  List<Recommendation> volRecommendations, String task, VirtualPoolCapabilityValuesWrapper cosCapabilities) throws ControllerException {

        return _defaultBlockServiceApi.createVolumes(param, project, neighborhood, cos, volRecommendations, task,
                cosCapabilities);
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void deleteVolumes(final URI systemURI, final List<URI> volumeURIs,
        final String deletionType, final String task) throws InternalException {
        _log.info("Request to delete {} volume(s) with Mirror Protection", volumeURIs.size());
        super.deleteVolumes(systemURI, volumeURIs, deletionType, task);
    }

    @Override
    public <T extends DataObject> String checkForDelete(T object) {
        return null;
    }

    @Override
    public TaskList startNativeContinuousCopies(StorageSystem storageSystem, Volume sourceVolume,
                                                VirtualPool sourceVirtualPool, VirtualPoolCapabilityValuesWrapper capabilities,
                                                NativeContinuousCopyCreate param, String taskId)
            throws ControllerException {

        validateNotAConsistencyGroupVolume(sourceVolume, sourceVirtualPool);

        TaskList taskList = new TaskList();
        // Currently, this will create a single mirror and add it to the source volume
        // Two steps: first place the mirror and then prepare the mirror.

        // Get recommendation for mirror
        VirtualPool mirrorVPool = sourceVirtualPool;
        if (!isNullOrEmpty(sourceVirtualPool.getMirrorVirtualPool())) {
            URI mirrorPoolUri = URI.create(sourceVirtualPool.getMirrorVirtualPool());
            if (!URIUtil.isNull(mirrorPoolUri)) {
                mirrorVPool = _dbClient.queryObject(VirtualPool.class, mirrorPoolUri);
            }
        }
        List<Recommendation> volumeRecommendations = new ArrayList<Recommendation>();

        for (int i = 0; i < capabilities.getResourceCount(); i++) {
            VolumeRecommendation volumeRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_VOLUME,
                    sourceVolume.getCapacity(), sourceVirtualPool, sourceVolume.getVirtualArray());
            volumeRecommendation.setId(sourceVolume.getId());
            volumeRecommendation.addStoragePool(sourceVolume.getPool());
            volumeRecommendations.add(volumeRecommendation);
        }
        VirtualArray neighborhood = _dbClient.queryObject(VirtualArray.class, sourceVolume.getVirtualArray());
        _scheduler.getRecommendationsForMirrors(neighborhood, mirrorVPool, capabilities, volumeRecommendations);

        // Prepare mirror.
        int volumeCounter = 1;
        int volumeCount = capabilities.getResourceCount();
        String volumeLabel = param.getName();
        List<Volume> preparedVolumes = new ArrayList<Volume>();
        // only mirror will be prepared (the source already exist)
        _scheduler.prepareRecommendedVolumes(null, taskId, taskList, null,
                null, sourceVirtualPool, volumeCount, volumeRecommendations,
                null, volumeCounter, volumeLabel,
                preparedVolumes, capabilities, false);

        for (Volume volume:preparedVolumes){
			Operation op = _dbClient.createTaskOpStatus(BlockMirror.class, volume.getId(), 
					taskId, ResourceOperationTypeEnum.ATTACH_BLOCK_MIRROR);
			volume.getOpStatus().put(taskId,op);
			TaskResourceRep volumeTask = toTask(volume, taskId, op);
            taskList.getTaskList().add(volumeTask);
		}

        BlockController controller = getController(BlockController.class, storageSystem.getSystemType());

        try {
            controller.attachNativeContinuousCopies(storageSystem.getId(), sourceVolume.getId(), taskId);
        } catch (ControllerException ce) {
            String errorMsg = format("Failed to start continuous copies on volume %s: %s",
                    sourceVolume.getId(), ce.getMessage());

            _log.error(errorMsg, ce);
            for (TaskResourceRep taskResourceRep : taskList.getTaskList()) {
                taskResourceRep.setState(Operation.Status.error.name());
                taskResourceRep.setMessage(errorMsg);
                Operation statusUpdate = new Operation(Operation.Status.error.name(), errorMsg);
                _dbClient.updateTaskOpStatus(Volume.class, taskResourceRep.getResource().getId(), taskId, statusUpdate);
            }
            throw ce;
        }

        return taskList;
    }

    @Override
    public TaskList stopNativeContinuousCopies(StorageSystem storageSystem, Volume sourceVolume,
                                               List<URI> mirrors,
                                               String taskId) throws ControllerException {
        TaskList taskList = new TaskList();
        List<BlockMirror> blockMirrors = null;
        if(mirrors != null){
            blockMirrors = new ArrayList<BlockMirror>();
            for(URI mirrorURI : mirrors){
                BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, mirrorURI);
                blockMirrors.add(blockMirror);
            }
        }
        List<URI> copiesToStop = getCopiesToStop(blockMirrors, sourceVolume);
        // Ensure we don't attempt to stop any lingering inactive copies
        removeIf(copiesToStop, isMirrorInactivePredicate());

        String mirrorTargetCommaDelimList = Joiner.on(',').join(copiesToStop);
        Operation op = _dbClient.createTaskOpStatus(Volume.class, sourceVolume.getId(), taskId, 
        		ResourceOperationTypeEnum.DETACH_BLOCK_MIRROR, mirrorTargetCommaDelimList); 

        List<BlockMirror> copies =
                _dbClient.queryObject(BlockMirror.class, copiesToStop);
        // Stopped copies will be promoted to regular block volumes
        List<URI> promotees = preparePromotedVolumes(copies, taskList, taskId);

        taskList.getTaskList().add(toTask(sourceVolume, copies, taskId, op));

        BlockController controller = getController(BlockController.class, storageSystem.getSystemType());
        try {
            controller.detachNativeContinuousCopies(storageSystem.getId(), copiesToStop, promotees, taskId);
        } catch (ControllerException ce) {
            String errorMsg = format("Failed to stop continuous copies for volume %s: %s",
                    sourceVolume.getId(), ce.getMessage());

            List<Volume> volumes = _dbClient.queryObject(Volume.class, promotees);
            for (Volume volume : volumes) {
                volume.setInactive(true);
            }
            _dbClient.persistObject(volumes);

            _log.error(errorMsg, ce);
            for (TaskResourceRep taskResourceRep : taskList.getTaskList()) {
                taskResourceRep.setState(Operation.Status.error.name());
                taskResourceRep.setMessage(errorMsg);
                _dbClient.error(Volume.class, taskResourceRep.getResource().getId(), taskId, ce);
            }
            throw ce;
        }
        return taskList;
    }

    private List<URI> preparePromotedVolumes(List<BlockMirror> copiesToStop, TaskList taskList, String opId) {
        List<URI> promotedVolumes = new ArrayList<URI>();
        for (BlockMirror copy : copiesToStop) {
            Volume v = new Volume();
            v.setId(URIUtil.createId(Volume.class));
            v.setLabel(copy.getLabel());
            v.setProject(new NamedURI(copy.getProject().getURI(), copy.getProject().getName()));
            v.setTenant(new NamedURI(copy.getTenant().getURI(), copy.getTenant().getName()));
            _dbClient.createObject(v);
            Operation op = _dbClient.createTaskOpStatus(Volume.class, v.getId(), opId, 
            		ResourceOperationTypeEnum.PROMOTE_COPY_TO_VOLUME, copy.getId().toString());
            taskList.getTaskList().add(toTask(v, Arrays.asList(copy), opId, op));
            promotedVolumes.add(v.getId());
        }
        return promotedVolumes;
    }

    /**
     * {@inheritDoc}
     * @throws ControllerException
     */
    @Override
    public TaskResourceRep pauseNativeContinuousCopies(StorageSystem storageSystem, Volume sourceVolume,
                                                       List<BlockMirror> blockMirrors, Boolean sync,
                                                       String taskId) throws ControllerException {
        Operation op = null;
        List<URI> mirrorUris = new ArrayList<URI>();

        // Assume all continuous copies are to be paused
        if (blockMirrors == null) {
            blockMirrors = new ArrayList<BlockMirror>();
            for (String uriStr : sourceVolume.getMirrors()) {
                BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, URI.create(uriStr));
                blockMirrors.add(mirror);
            }
        }

        List<BlockMirror> pausedMirrors = new ArrayList<BlockMirror>();
        for (BlockMirror mirror : blockMirrors) {        	

            if (mirrorIsResumable(mirror)) {
                // extract mirrors that are in "paused" state
                pausedMirrors.add(mirror);
            } else if (!mirrorIsPausable(mirror)) {
                //  if there is a mirror is not in paused state, and not pausable, throw exception
                throw APIException.badRequests.cannotPauseContinuousCopyWithSyncState(mirror.getId(),mirror.getSyncState(),sourceVolume.getId());
            } else if (mirrorIsResynchronizing(mirror)) {
                throw APIException.badRequests.cannotPauseContinuousCopyWhileResynchronizing(mirror.getId(),mirror.getSyncState(),sourceVolume.getId());                
            } else {
                // otherwise, place mirror a list... get ready to pause
                mirrorUris.add(mirror.getId());
            }
        }

        /*
         * if all mirrors are paused, then there is no task to do.
         * Return a successful task
         */
        if (!pausedMirrors.isEmpty() && mirrorUris.isEmpty()) {
            // If the mirrors is already paused, there would be no need to queue another request to activate it again.
        	op = new Operation();
        	op.ready();
        	op.setResourceType(ResourceOperationTypeEnum.FRACTURE_VOLUME_MIRROR);
        	op.setMessage("The continuous copies are already paused");
            _dbClient.createTaskOpStatus(Volume.class, sourceVolume.getId(), taskId, op);
            return toTask(sourceVolume, taskId, op);
        } else {
            Collection<String> mirrorTargetIds =
                    Collections2.transform(blockMirrors, FCTN_VOLUME_URI_TO_STR);
            String mirrorTargetCommaDelimList = Joiner.on(',').join(mirrorTargetIds);
            op = _dbClient.createTaskOpStatus(Volume.class, sourceVolume.getId(), taskId,
                       ResourceOperationTypeEnum.FRACTURE_VOLUME_MIRROR, mirrorTargetCommaDelimList);

            try {
                BlockController controller = getController(BlockController.class, storageSystem.getSystemType());
                controller.pauseNativeContinuousCopies(storageSystem.getId(), mirrorUris, sync, taskId);
            } catch (ControllerException e) {
                String errorMsg = format("Failed to pause continuous copies for source volume %s", sourceVolume.getId());
                _log.error(errorMsg, e);
                _dbClient.error(Volume.class, sourceVolume.getId(), taskId, e);
            }

            return toTask(sourceVolume, blockMirrors,taskId, op);
        	
        }
        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskResourceRep resumeNativeContinuousCopies(StorageSystem storageSystem, Volume sourceVolume,
                                                        List<BlockMirror> blockMirrors,
                                                        String taskId) throws ControllerException {
        _log.info("START resume native continuous copies");
        Operation op = null;

        // Assume all continuous copies are to be resumed
        if (blockMirrors == null) {
            blockMirrors = new ArrayList<BlockMirror>();
            for (String uriStr : sourceVolume.getMirrors()) {
                BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, URI.create(uriStr));
                blockMirrors.add(mirror);
            }
        }
        
        List<URI> resumedMirrors = new ArrayList<URI>();
        List<URI> mirrorURIs = new ArrayList<URI>();
        for (BlockMirror mirror : blockMirrors) {
            if (mirrorIsPausable(mirror) || mirrorIsResynchronizing(mirror)) {
                // extract mirrors that are in resume state or resynchronizing
            	resumedMirrors.add(mirror.getId());
            } else if (!mirrorIsResumable(mirror)) {
            	throw APIException.badRequests.cannotResumeContinuousCopyWithSyncState(mirror.getId(), mirror.getSyncState(), sourceVolume.getId());
            } else {
                mirrorURIs.add(mirror.getId());
            }
        }
        /*
         * if all mirrors are resumed/resynchronizing, then there is no task to do.
         * Return a successful task
         */
        if (!resumedMirrors.isEmpty() && mirrorURIs.isEmpty()) {
            // If the mirrors is already resumed or resynchronizing, there would be no need to queue another request to resume it again.
        	op = new Operation();
        	op.setResourceType(ResourceOperationTypeEnum.RESUME_VOLUME_MIRROR);
        	op.setAssociatedResourcesField(Joiner.on(',').join(resumedMirrors));
        	op.ready("The continuous copies are already resumed or resynchronizing");
            _dbClient.createTaskOpStatus(Volume.class, sourceVolume.getId(), taskId, op);            		         

            return toTask(sourceVolume, taskId, op);
        } else {
	        Collection<String> mirrorTargetIds = Collections2.transform(blockMirrors, FCTN_VOLUME_URI_TO_STR);
	        String mirrorTargetCommaDelimList = Joiner.on(',').join(mirrorTargetIds);
	        op = _dbClient.createTaskOpStatus(Volume.class, sourceVolume.getId(), taskId,
	                         ResourceOperationTypeEnum.RESUME_VOLUME_MIRROR, mirrorTargetCommaDelimList);
	
	        try {
	            BlockController controller = getController(BlockController.class, storageSystem.getSystemType());
	            controller.resumeNativeContinuousCopies(storageSystem.getId(), mirrorURIs, taskId);
	        } catch (ControllerException e) {
	            String errorMsg = format("Failed to resume continuous copies for source volume %s", sourceVolume.getId());
	            _log.error(errorMsg, e);
	            _dbClient.error(Volume.class, sourceVolume.getId(), taskId, e);
	        }
        }

        return toTask(sourceVolume, blockMirrors, taskId, op);
    }

    /**
     * {@inheritDoc}
     * @throws ControllerException
     */
    @Override
    public TaskResourceRep deactivateMirror(StorageSystem storageSystem, URI mirrorURI, String taskId) throws ControllerException {
        _log.info("START: deactivate mirror");

        BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrorURI);
        Volume sourceVolume = _dbClient.queryObject(Volume.class, mirror.getSource().getURI());
        Operation op = _dbClient.createTaskOpStatus(Volume.class, sourceVolume.getId(), taskId,
                	ResourceOperationTypeEnum.DEACTIVATE_VOLUME_MIRROR, mirror.getId().toString());
        try {
            BlockController controller = getController(BlockController.class, storageSystem.getSystemType());
            controller.deactivateMirror(storageSystem.getId(), mirror.getId(), taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to deactivate continuous copy %s", mirror.getId().toString());
            _log.error(errorMsg, e);
            _dbClient.error(Volume.class, mirror.getSource().getURI(), taskId, e);
        }

        return toTask(sourceVolume, Arrays.asList(mirror), taskId, op);
    }


    @Override
    public void changeVolumeVirtualPool(URI systemURI, Volume volume, VirtualPool virtualPool,
                                        VirtualPoolChangeParam cosChangeParam,
                                        String taskId) throws ControllerException {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
        String systemType = storageSystem.getSystemType();

        List<Volume> volumes = new ArrayList<Volume>();
        volumes.add(volume);
        if (checkCommonVpoolUpdates(volumes, virtualPool, taskId)) {
            return;
        }

        if (DiscoveredDataObject.Type.vnxblock.name().equals(systemType) ||
                DiscoveredDataObject.Type.vmax.name().equals(systemType)) {
            URI original = volume.getVirtualPool();
            // Update the volume with the new virtual pool
            volume.setVirtualPool(virtualPool.getId());
            _dbClient.persistObject(volume);
            // Update the task
            String msg = format("VirtualPool changed from %s to %s for Volume %s",
                    original, virtualPool.getId(), volume.getId());
            Operation opStatus = new Operation(Operation.Status.ready.name(), msg);
            _dbClient.updateTaskOpStatus(Volume.class, volume.getId(), taskId, opStatus);
        } else {
            throw APIException.badRequests.unsupportedSystemType(systemType);
        }
    }

    @Override
    public void changeVolumeVirtualPool(List<Volume> volumes, VirtualPool vpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {

        // Check for common Vpool updates handled by generic code. It returns true if handled.
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return;
        }

        for (Volume volume : volumes) {
            changeVolumeVirtualPool(volume.getStorageController(), volume, vpool, vpoolChangeParam, taskId);
        }
    }

    private Predicate<URI> isMirrorInactivePredicate() {
        return new Predicate<URI>() {

            @Override
            public boolean apply(URI uri) {
                BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, uri);
                return mirror == null || mirror.getInactive();
            }
        };
    }

    private List<URI> getCopiesToStop(List<BlockMirror> blockMirrors, Volume sourceVolume) {
        List<URI> copiesToStop = new ArrayList<URI>();
        if (blockMirrors == null || blockMirrors.isEmpty()) {
            copiesToStop.addAll(transform(sourceVolume.getMirrors(), FCTN_STRING_TO_URI));
        } else {
            copiesToStop.addAll(transform(blockMirrors, FCTN_MIRROR_TO_URI));
        }
        return copiesToStop;
    }

    private boolean mirrorIsPausable(BlockMirror mirror) {
        return mirror.getInactive() == false &&
                !SynchronizationState.FRACTURED.toString().equals(mirror.getSyncState());
    }

    private boolean mirrorIsResumable(BlockMirror mirror) {
        return !mirror.getInactive() &&
                SynchronizationState.FRACTURED.toString().equals(mirror.getSyncState());
    }
    
    private boolean mirrorIsResynchronizing(BlockMirror mirror) {
        return !mirror.getInactive() &&
                SynchronizationState.RESYNCHRONIZING.toString().equals(mirror.getSyncState());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI,
        List<URI> volumeURIs) {
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        for (URI volumeURI : volumeURIs) {
            VolumeDescriptor desc = new VolumeDescriptor(
                    VolumeDescriptor.Type.BLOCK_DATA, systemURI, volumeURI, null, null);
            volumeDescriptors.add(desc);
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            addDescriptorsForMirrors(volumeDescriptors, volume);
        }
        return volumeDescriptors;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperations(Volume volume, VirtualPool volumeVirtualPool,
                                                          VirtualPool newVirtualPool, StringBuffer notSuppReasonBuff) {                 
        return _defaultBlockServiceApi.getVirtualPoolChangeAllowedOperations(volume, volumeVirtualPool, newVirtualPool, notSuppReasonBuff);
    }

}
