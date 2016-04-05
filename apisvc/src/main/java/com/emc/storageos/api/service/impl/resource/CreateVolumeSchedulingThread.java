/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * Background thread that runs the placement, scheduling, and controller dispatching of a create volume
 * request. This allows the API to return a Task object quickly.
 */
class CreateVolumeSchedulingThread implements Runnable {

    static final Logger _log = LoggerFactory.getLogger(CreateVolumeSchedulingThread.class);

    private final BlockService blockService;
    private VirtualArray varray;
    private Project project;
    private VirtualPool vpool;
    private VirtualPoolCapabilityValuesWrapper capabilities;
    private TaskList taskList;
    private String task;
    private BlockConsistencyGroup consistencyGroup;
    private ArrayList<String> requestedTypes;
    private VolumeCreate param;
    private BlockServiceApi blockServiceImpl;

    public CreateVolumeSchedulingThread(BlockService blockService, VirtualArray varray, Project project,
            VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities,
            TaskList taskList, String task, BlockConsistencyGroup consistencyGroup, ArrayList<String> requestedTypes,
            VolumeCreate param,
            BlockServiceApi blockServiceImpl) {
        this.blockService = blockService;
        this.varray = varray;
        this.project = project;
        this.vpool = vpool;
        this.capabilities = capabilities;
        this.taskList = taskList;
        this.task = task;
        this.consistencyGroup = consistencyGroup;
        this.requestedTypes = requestedTypes;
        this.param = param;
        this.blockServiceImpl = blockServiceImpl;
    }

    

	public List<VolumeRecommendation> bypassRecommendationsForResources(VolumeCreate param) {

		//_log.debug("Schedule storage for {} resource(s) of size {}.",capabilities.getResourceCount(), capabilities.getSize());
		List<VolumeRecommendation> volumeRecommendations = new ArrayList<VolumeRecommendation>();
		try {

			// Initialize a list of recommendations to be returned.
			List<Recommendation> recommendations = new ArrayList<Recommendation>();

			Map<String, String> passThruParam = param.getPassThrouhParams();

			String storageSystemId = passThruParam.get("storage-system");
			String storagePoolId = passThruParam.get("storage-pool");

			// StorageSystem
			// storageSystem=this.blockService._dbClient.queryObject(StorageSystem.class,
			// new URI(storageSystemId));
			// StoragePool
			// storagePool=this.blockService._dbClient.queryObject(StoragePool.class,
			// new URI(storagePoolId));

			// create list of VolumeRecommendation(s) for volumes
			int count = param.getCount();
			while (count > 0) {
				VolumeRecommendation volumeRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_VOLUME,	SizeUtil.translateSize(param.getSize()), null, null);

				volumeRecommendation.addStoragePool(new URI(storagePoolId));

				volumeRecommendation.addStorageSystem(new URI(storageSystemId));
				volumeRecommendations.add(volumeRecommendation);

				count--;
			}
		} catch (URISyntaxException e) {

			e.printStackTrace();
		}

		return volumeRecommendations;
	}

	
	 // @Override
	  public TaskList createVolumes(VolumeCreate param,  List<Recommendation> recommendations, TaskList taskList,
	            String task, VirtualPoolCapabilityValuesWrapper cosCapabilities) throws InternalException {
	        // Prepare the Bourne Volumes to be created and associated
	        // with the actual storage system volumes created. Also create
	        // a BlockTaskList containing the list of task resources to be
	        // returned for the purpose of monitoring the volume creation
	        // operation for each volume to be created.
	        int volumeCounter = 0;
	        String volumeLabel = param.getName();
	        List<Volume> preparedVolumes = new ArrayList<Volume>();

	        // Prepare the volumes
	        this.prepareRecommendedVolumes(param, task, taskList,  recommendations,
	                 volumeCounter, volumeLabel, preparedVolumes);

	        // Prepare the volume descriptors based on the recommendations
	        final List<VolumeDescriptor> volumeDescriptors = prepareVolumeDescriptors(preparedVolumes);
	        


	        final BlockOrchestrationController controller = blockService.getController(BlockOrchestrationController.class, BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);


	        try {
	            // Execute the volume creations requests
	            controller.createVolumes(volumeDescriptors, task);
	        } catch (InternalException e) {
	            _log.error("Controller error when creating volumes", e);

	            throw e;
	        } catch (Exception e) {
	            _log.error("Controller error when creating volumes", e);
	            throw e;
	        }

	        return taskList;
	    }
	
	    private List<VolumeDescriptor> prepareVolumeDescriptors(List<Volume> volumes) {

	        // Build up a list of VolumeDescriptors based on the volumes
	        final List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
	        for (Volume volume : volumes) {
	            VolumeDescriptor desc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
	                    volume.getStorageController(), volume.getId(),
	                    volume.getPool(), null, new VirtualPoolCapabilityValuesWrapper());
	            volumeDescriptors.add(desc);
	        }

	        return volumeDescriptors;
	    }
	  
	    public void prepareRecommendedVolumes(VolumeCreate param, String task, TaskList taskList,
	            List<Recommendation> recommendations,  int volumeCounter,
	            String volumeLabel, List<Volume> preparedVolumes) {
	        Iterator<Recommendation> recommendationsIter = recommendations.iterator();
	        while (recommendationsIter.hasNext()) {
	            VolumeRecommendation recommendation = (VolumeRecommendation) recommendationsIter.next();
	            // if id is already set in recommendation, do not prepare the volume (volume already exists)
	            if (recommendation.getId() != null) {
	                continue;
	            }
	            // prepare block volume
	            if (recommendation.getType().toString().equals(VolumeRecommendation.VolumeType.BLOCK_VOLUME.toString())) {
	                String newVolumeLabel = AbstractBlockServiceApiImpl.generateDefaultVolumeLabel(volumeLabel, volumeCounter++, param.getCount());

	                // Grab the existing volume and task object from the incoming task list
	                Volume volume = getPrecreatedVolume(this.blockService._dbClient, taskList, newVolumeLabel);
	                boolean volumePrecreated = false;
	                if (volume != null) {
	                    volumePrecreated = true;
	                }

	                long size = SizeUtil.translateSize(param.getSize());
	                long thinVolumePreAllocationSize = 0;


	                volume = prepareVolume(this.blockService._dbClient, volume, size,  recommendation, newVolumeLabel);
	                // set volume id in recommendation
	                recommendation.setId(volume.getId());
	                // add volume to reserved capacity map of storage pool
	               StorageScheduler.addVolumeCapacityToReservedCapacityMap(this.blockService._dbClient, volume);

	                preparedVolumes.add(volume);

	                if (!volumePrecreated) {
	                    Operation op = this.blockService._dbClient.createTaskOpStatus(Volume.class, volume.getId(),
	                            task, ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
	                    volume.getOpStatus().put(task, op);
	                    TaskResourceRep volumeTask = toTask(volume, task, op);
	                    // This task addition is inconsequential since we've already returned the source volume tasks.
	                    // It is good to continue to have a task associated with this volume AND store its status in the volume.
	                    taskList.getTaskList().add(volumeTask);
	                }

	            } 
	        }
	    }
	    
	    public static Volume getPrecreatedVolume(DbClient dbClient, TaskList taskList, String label) {
	        // The label we've been given has already been appended with the appropriate volume number
	        String volumeLabel = AbstractBlockServiceApiImpl.generateDefaultVolumeLabel(label, 0, 1);
	        if (taskList == null) {
	            return null;
	        }

	        for (TaskResourceRep task : taskList.getTaskList()) {
	            Volume volume = dbClient.queryObject(Volume.class, task.getResource().getId());
	            if (volume.getLabel().equalsIgnoreCase(volumeLabel)) {
	                return volume;
	            }
	        }
	        return null;
	    }
	    
	    public static Volume prepareVolume(DbClient dbClient, Volume volume, long size,    VolumeRecommendation placement, String label) {


	        boolean newVolume = false;
	        if (volume == null) {
	            newVolume = true;
	            volume = new Volume();
	            volume.setId(URIUtil.createId(Volume.class));
	            volume.setOpStatus(new OpStatusMap());
	        } else {
	            // Reload volume object from DB
	            volume = dbClient.queryObject(Volume.class, volume.getId());
	        }

	        volume.setSyncActive(!Boolean.valueOf(false));
	        volume.setLabel(label);
	        volume.setCapacity(size);

	        volume.setThinlyProvisioned(false);
	        volume.setVirtualPool(null);
	        volume.setProject(null);
	        volume.setTenant(null);
	        volume.setVirtualArray(null);
	        URI poolId = placement.getCandidatePools().get(0);

	        volume.setStorageController(placement.getCandidateSystems().get(0));
	        volume.setPool(poolId);


	        if (newVolume) {
	            dbClient.createObject(volume);
	        } else {
	            dbClient.updateAndReindexObject(volume);
	        }

	        return volume;
	    }
    @Override
    public void run() {
        _log.info("Starting scheduling/placement thread...");
        // Call out placementManager to get the recommendation for placement.
        try {
        	List recommendations = null;
        	if (param.getPassThrouhParams() != null && !param.getPassThrouhParams().isEmpty()){
        		recommendations = this.bypassRecommendationsForResources(param);
        		this.createVolumes(param,  recommendations, taskList, task, capabilities);
                        
        	} else {
            recommendations = this.blockService._placementManager.getRecommendationsForVolumeCreateRequest(
                    varray, project, vpool, capabilities);
        	}

            if (recommendations.isEmpty()) {
                throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getLabel(), varray.getLabel());
            }

            // At this point we are committed to initiating the request.
            if (consistencyGroup != null) {
                consistencyGroup.addRequestedTypes(requestedTypes);
                this.blockService._dbClient.updateAndReindexObject(consistencyGroup);
            }

            // Call out to the respective block service implementation to prepare
            // and create the volumes based on the recommendations.
            blockServiceImpl.createVolumes(param, project, varray, vpool, recommendations, taskList, task, capabilities);
        } catch (Exception ex) {
            for (TaskResourceRep taskObj : taskList.getTaskList()) {
                if (ex instanceof ServiceCoded) {
                    this.blockService._dbClient.error(Volume.class, taskObj.getResource().getId(), taskObj.getOpId(), (ServiceCoded) ex);
                } else {
                    this.blockService._dbClient.error(Volume.class, taskObj.getResource().getId(), taskObj.getOpId(),
                            InternalServerErrorException.internalServerErrors
                                    .unexpectedErrorVolumePlacement(ex));
                }
                _log.error(ex.getMessage(), ex);
                taskObj.setMessage(ex.getMessage());
                // Set the volumes to inactive
                Volume volume = this.blockService._dbClient.queryObject(Volume.class, taskObj.getResource().getId());
                volume.setInactive(true);
                this.blockService._dbClient.updateAndReindexObject(volume);
            }
        }
        _log.info("Ending scheduling/placement thread...");
    }

    /**
     * Static method to execute the API task in the background to create an export group.
     * 
     * @param blockService block service ("this" from caller)
     * @param executorService executor service that manages the thread pool
     * @param dbClient db client
     * @param varray virtual array
     * @param project project
     * @param vpool virtual pool
     * @param capabilities capabilities object
     * @param taskList list of tasks
     * @param task task ID
     * @param consistencyGroup consistency group
     * @param requestedTypes requested types
     * @param param volume creation request params
     * @param blockServiceImpl block service impl to call
     */
    public static void executeApiTask(BlockService blockService, ExecutorService executorService, DbClient dbClient, VirtualArray varray,
            Project project,
            VirtualPool vpool, VirtualPoolCapabilityValuesWrapper capabilities,
            TaskList taskList, String task, BlockConsistencyGroup consistencyGroup, ArrayList<String> requestedTypes,
            VolumeCreate param,
            BlockServiceApi blockServiceImpl) {

        CreateVolumeSchedulingThread schedulingThread = new CreateVolumeSchedulingThread(blockService, varray,
                project, vpool,
                capabilities, taskList, task, consistencyGroup, requestedTypes, param, blockServiceImpl);
        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            for (TaskResourceRep taskObj : taskList.getTaskList()) {
                String message = "Failed to execute volume creation API task for resource " + taskObj.getResource().getId();
                _log.error(message);
                taskObj.setMessage(message);
                // Set the volumes to inactive
                Volume volume = dbClient.queryObject(Volume.class, taskObj.getResource().getId());
                volume.setInactive(true);
                dbClient.updateAndReindexObject(volume);
            }
        }
    }

	public static void executeSkinyApiTask(BlockService blockService,ExecutorService executorService, DbClient dbClient, TaskList taskList, String task, ArrayList<String> requestedTypes, VolumeCreate param, BlockServiceApi blockServiceImpl) {
		
        CreateVolumeSchedulingThread schedulingThread = new CreateVolumeSchedulingThread(blockService, null,null,null,null, taskList, task, null, requestedTypes, param, blockServiceImpl);
        
        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            for (TaskResourceRep taskObj : taskList.getTaskList()) {
                String message = "Failed to execute volume creation API task for resource " + taskObj.getResource().getId();
                _log.error(message);
                taskObj.setMessage(message);
                // Set the volumes to inactive
                Volume volume = dbClient.queryObject(Volume.class, taskObj.getResource().getId());
                volume.setInactive(true);
                dbClient.updateAndReindexObject(volume);
            }
        }
		
	}
}
