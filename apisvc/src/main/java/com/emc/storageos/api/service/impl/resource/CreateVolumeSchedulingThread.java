/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.SRDFScheduler;
import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Joiner;

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

    @Override
    public void run() {
        _log.info("Starting scheduling/placement thread...");
        // Call out placementManager to get the recommendation for placement.
        try {
        	Map<Integer, VirtualPool> vPoolBucketsByOrder = new HashMap<Integer, VirtualPool>();
        	vPoolBucketsByOrder.put(0, vpool);
        	
        	this.blockService._placementManager.groupMasterVirtualPoolIntoChildBuckets(vpool, 1, vPoolBucketsByOrder);
        	
        	
        	for (int i = 0; i< vPoolBucketsByOrder.size(); i++) {
        		Set<Entry<Integer, VirtualPool>> bucketIterator1 = vPoolBucketsByOrder.entrySet();
        		Entry<Integer, VirtualPool>  entryBucket= (Entry<Integer, VirtualPool>) bucketIterator1.toArray()[i];
        		VirtualPool vPoolChild = entryBucket.getValue();
        		Volume volume = null;
        		if (i ==0) {
        		
        		//fill in the cascaded capabilities
            	this.blockService._placementManager.buildCascadedCapabilities(vPoolChild, capabilities,project);
            	//Get Recommendations for root level virtual pool
                List rootRecommendations = this.blockService._placementManager.getRecommendationsForVolumeCreateRequest(
                        varray, project, vPoolChild, capabilities);
                _log.info("Root Recommendations : ",Joiner.on("@@@@#####").join(rootRecommendations));
                
                
                List<VolumeDescriptor> volDescriptors =  blockServiceImpl.createVolumeDescriptors(param, project, varray, vPoolChild, rootRecommendations,
                		taskList, task, capabilities);
                
                volume = (Volume) this.blockService._dbClient.queryObject(volDescriptors.get(0).getVolumeURI());
                
        		} else {
        			//do change vpool
        			Scheduler scheduler = this.blockService._placementManager.getBlockServiceImpl(vPoolChild);
        		    //Build Capabilities if necessary
        			List childRecommendations = scheduler.scheduleStorageForCosChangeUnprotected(volume, vPoolChild, 
        					SRDFScheduler.getTargetVirtualArraysForVirtualPool(project, vPoolChild, this.blockService._dbClient,
        		    		this.blockService._permissionsHelper), null);
        			 
        			  _log.info("Child Recommendations : ",Joiner.on("@@@@#####").join(childRecommendations));
        		}
        		
        	}
        	
        	return;
        	//fill in the cascaded capabilities
       /* 	this.blockService._placementManager.buildCascadedCapabilities(vpool, capabilities);
        	//Get Recommendations for root level virtual pool
            List recommendations = this.blockService._placementManager.getRecommendationsForVolumeCreateRequest(
                    varray, project, vpool, capabilities);
            
            if (recommendations.isEmpty()) {
                throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getId(), varray.getId());
            }

            // At this point we are committed to initiating the request.
            if (consistencyGroup != null) {
                consistencyGroup.addRequestedTypes(requestedTypes);
                this.blockService._dbClient.updateAndReindexObject(consistencyGroup);
            }

            // Call out to the respective block service implementation to prepare
            // and create the volumes based on the recommendations.*/
       //     blockServiceImpl.createVolumes(param, project, varray, vpool, recommendations, taskList, task, capabilities); 
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
}