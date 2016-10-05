/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation.VolumeType;
import com.emc.storageos.api.service.impl.placement.VPlexScheduler;
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
import com.emc.storageos.db.client.model.StorageProtocol;
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
import com.emc.storageos.volumecontroller.VPlexRecommendation;
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

    public List<Recommendation> bypassRecommendationsForVplexResources(VolumeCreate Vplexparam){
    	List<Recommendation> volumeRecommendations = new ArrayList<Recommendation>();
    	
    	List<VPlexRecommendation> VPlexRecommendations = new ArrayList<VPlexRecommendation>();
    	VPlexRecommendation vplexRecommendation = new VPlexRecommendation();
    	
    	Map<String,String> VplexpassThroughParam = Vplexparam.getPassThroughParams();
    	
    	URI storageSystemId = null;
    	URI storagePoolId = null;
	URI backendNetworkURI_1 = null;
    	URI backendNetworkURI_2 = null;
    	URI HAstorageSystemId = null;
    	URI HAstoragePoolId = null;
    	URI vplexId = null;
 	URI haBackendNetworkURI_1 = null;
    	URI haBackendNetworkURI_2 = null;

    	//Integer count = new Integer(VplexpassThroughParam.get("count"));
    	boolean isVplexHA = VplexpassThroughParam.get("isVplexHA") != null;
    	
		try {
			storageSystemId = new URI(VplexpassThroughParam.get("storage-system"));
			storagePoolId = new URI(VplexpassThroughParam.get("storage-pool"));
			vplexId = new URI(VplexpassThroughParam.get("VPlex-Id"));
			backendNetworkURI_1 = new URI(VplexpassThroughParam.get("backend-network-1"));
			backendNetworkURI_2 = new URI(VplexpassThroughParam.get("backend-network-2"));			
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
    	//Create the recommendations for source and target array volumes.
		//create the recommendations for the vplex volume - source and target
		
		//volumeRecommendations.addAll(createVolumeRecommendations(storageSystemId,storagePoolId,param.getCount()));
		
		volumeRecommendations.addAll(this.createVplexRecommendations(storageSystemId,storagePoolId,vplexId,backendNetworkURI_1,backendNetworkURI_2,param.getCount()));
		if (isVplexHA){
			_log.debug("isVplexHA");
			try{
				HAstorageSystemId = new URI(VplexpassThroughParam.get("HA-storage-system"));
				HAstoragePoolId =new URI(VplexpassThroughParam.get("HA-storage-pool"));
				haBackendNetworkURI_1 = new URI(VplexpassThroughParam.get("HA-backend-network-1"));
				haBackendNetworkURI_2 = new URI(VplexpassThroughParam.get("HA-backend-network-2"));
			} catch (URISyntaxException e){
				e.printStackTrace();
			}
			volumeRecommendations.addAll(this.createVplexRecommendations(HAstorageSystemId,HAstoragePoolId,vplexId,haBackendNetworkURI_1,backendNetworkURI_2,param.getCount()));
		}
	_log.info("No of Recommendations created: {} ", volumeRecommendations.size());    	
    	return volumeRecommendations;
    }
    
    public List<VPlexRecommendation> createVplexRecommendations(URI storageSystemId, URI storagePoolId, URI vplexId, URI backendNetworkURI_1, URI backendNetworkURI_2, int count ){
    	List<VPlexRecommendation> vplexRecommendations = new ArrayList<VPlexRecommendation>();
		while (count > 0) {
			VPlexRecommendation vplexRecommendation = new VPlexRecommendation();

			vplexRecommendation.setSourceStorageSystem(storageSystemId);
		    vplexRecommendation.setSourceStoragePool(storagePoolId);
		    vplexRecommendation.setBackendNetworkList(backendNetworkURI_1,backendNetworkURI_2);
		    //vplexRecommendation.setBackendNetworkList(backendNetworkURI_2);
		    vplexRecommendation.setResourceCount(count);
		    //vplexRecommendation.setSourceDevice(URI.create(vplexStorageSystemId));
		    vplexRecommendation.setVPlexStorageSystem(vplexId);
		    vplexRecommendation.setVirtualArray(null);
		    vplexRecommendation.setVirtualPool(null);
			vplexRecommendations.add(vplexRecommendation);
			_log.info("VPlex recommendation {}   {}", storageSystemId.toString(), storagePoolId.toString());

			count--;
		}

		return vplexRecommendations;
    	
    }

        public List<VPlexRecommendation> createVplexRecommendations(URI volumeId, URI vplexId, URI backendNetworkURI_1, URI backendNetworkURI_2, int count ){
    	List<VPlexRecommendation> vplexRecommendations = new ArrayList<VPlexRecommendation>();
		while (count > 0) {
			VPlexRecommendation vplexRecommendation = new VPlexRecommendation();
			vplexRecommendation.setSourceVolume(volumeId);
                         Volume backendVolume = this.blockService._dbClient.queryObject(Volume.class, volumeId);
			if (backendVolume == null){
				 _log.info("Bad volume Id sent in payload");
				 
			}else {
				vplexRecommendation.setSourceStorageSystem(backendVolume.getStorageController());
			}
		    vplexRecommendation.setBackendNetworkList(backendNetworkURI_1, backendNetworkURI_2);
			vplexRecommendation.setResourceCount(count);
			vplexRecommendation.setVPlexStorageSystem(vplexId);
			vplexRecommendation.setVirtualArray(null);
			vplexRecommendation.setVirtualPool(null);
			vplexRecommendations.add(vplexRecommendation);
			_log.info("VPlex recommendation {}   {}", volumeId.toString());

			count--;
		}

		return vplexRecommendations;
    	
    }

    public List<VolumeRecommendation> createVolumeRecommendations(URI storageSystemId, URI storagePoolId, int count ){
    	List<VolumeRecommendation> volumeRecommendations = new ArrayList<VolumeRecommendation>();
		while (count > 0) {
			VolumeRecommendation volumeRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_VOLUME,	SizeUtil.translateSize(param.getSize()), null, null);

			volumeRecommendation.addStoragePool(storagePoolId);

			volumeRecommendation.addStorageSystem(storageSystemId);
			volumeRecommendations.add(volumeRecommendation);

			count--;
		}

		return volumeRecommendations;
    	
    }
   public List<Recommendation> bypassRecommendationsForVplexVolumeResources(VolumeCreate Vplexparam){
    	List<Recommendation> volumeRecommendations = new ArrayList<Recommendation>();
    	
    	List<VPlexRecommendation> VPlexRecommendations = new ArrayList<VPlexRecommendation>();
    	VPlexRecommendation vplexRecommendation = new VPlexRecommendation();
    	
    	Map<String,String> VplexpassThroughParam = Vplexparam.getPassThroughParams();

    	URI volumeId = null;    	
	    URI backendNetworkURI_1 = null;
    	URI backendNetworkURI_2 = null;
    	    	URI volumeForHAId = null;
    	URI vplexId = null;
 	URI haBackendNetworkURI_1 = null;
    	URI haBackendNetworkURI_2 = null;

    	//Integer count = new Integer(VplexVolumepassThroughParam.get("count"));
    	boolean isVplexHA = VplexpassThroughParam.get("isVplexHA") != null;
    	
		try {
			volumeId = new URI(VplexpassThroughParam.get("volumeId"));
			vplexId = new URI(VplexpassThroughParam.get("VPlex-Id"));
			if(VplexpassThroughParam.get("backend-network-1") != null && VplexpassThroughParam.get("backend-network-2") != null)
			{	
				backendNetworkURI_1 = new URI(VplexpassThroughParam.get("backend-network-1"));
				backendNetworkURI_2 = new URI(VplexpassThroughParam.get("backend-network-2"));	
			}		
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
    	//Create the recommendations for source and target array volumes.
		//create the recommendations for the vplex volume - source and target
		
		//volumeRecommendations.addAll(createVolumeRecommendations(volumeId,param.getCount()));
		
		volumeRecommendations.addAll(this.createVplexRecommendations(volumeId,vplexId,backendNetworkURI_1,backendNetworkURI_2,param.getCount()));
		if (isVplexHA){
			_log.debug("isVplexHA");
			try{
				volumeForHAId = new URI(VplexpassThroughParam.get("volume-for-HA"));
				if(VplexpassThroughParam.get("HA-backend-network-1") != null && VplexpassThroughParam.get("HA-backend-network-2") != null)
				{
					haBackendNetworkURI_1 = new URI(VplexpassThroughParam.get("HA-backend-network-1"));
					haBackendNetworkURI_2 = new URI(VplexpassThroughParam.get("HA-backend-network-2"));
				}
			} catch (URISyntaxException e){
				e.printStackTrace();
			}
			volumeRecommendations.addAll(this.createVplexRecommendations(volumeForHAId,vplexId, haBackendNetworkURI_1, haBackendNetworkURI_2, param.getCount()));
		}
	_log.info("No of Recommendations created: {} ", volumeRecommendations.size());    	
    	return volumeRecommendations;
    }
    
    
	public List<Recommendation> bypassRecommendationsForResources(VolumeCreate param) {

		//_log.debug("Schedule storage for {} resource(s) of size {}.",capabilities.getResourceCount(), capabilities.getSize());
		List<Recommendation> volumeRecommendations = new ArrayList<Recommendation>();
		try {

			// Initialize a list of recommendations to be returned.
			List<Recommendation> recommendations = new ArrayList<Recommendation>();

			Map<String, String> passThroughParam = param.getPassThroughParams();

			String storageSystemId = passThroughParam.get("storage-system");
			String storagePoolId = passThroughParam.get("storage-pool");
			
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
	        List<VolumeDescriptor> descriptors = null;

	        if (recommendations==null || recommendations.isEmpty()){
	        	return taskList;
	        }else if (recommendations.get(0) instanceof VPlexRecommendation){
        		descriptors = this.prepareRecommendedVolumesForVPlex(param, task, taskList,  recommendations,
	   	                 volumeCounter, volumeLabel, preparedVolumes);
	        } else {
	        	descriptors = this.prepareRecommendedVolumes(param, task, taskList,  recommendations,
	   	                 volumeCounter, volumeLabel, preparedVolumes);
	        }
	        // Prepare the volumes
	        final List<VolumeDescriptor> volumeDescriptors = descriptors;

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
	  
	  	private VolumeDescriptor prepareVolumeDescriptor(Volume volume) {

	            VolumeDescriptor volumeDescriptor =  new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
	                    volume.getStorageController(), volume.getId(),
	                    volume.getPool(), null, new VirtualPoolCapabilityValuesWrapper(), volume.getCapacity());
	            
	        

	        return volumeDescriptor;
	    }
	
	    private List<VolumeDescriptor> prepareVolumeDescriptors(List<Volume> volumes) {

	        // Build up a list of VolumeDescriptors based on the volumes
	        final List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
	        for (Volume volume : volumes) {
	            VolumeDescriptor desc = prepareVolumeDescriptor(volume);
	            volumeDescriptors.add(desc);
	        }

	        return volumeDescriptors;
	    }
	    
	    private String generateBackendVolumeLabel(String baseVolumeLabel, int arrayIndex) {
	        StringBuilder volumeLabelBuilder = new StringBuilder(baseVolumeLabel);
	        volumeLabelBuilder.append("-").append(arrayIndex);
	        
	        return volumeLabelBuilder.toString();
	    }
	    
	    public Map<String,List<VPlexRecommendation>> sortRecommendations(List<Recommendation> recommendations){
	    	Map<String, List<VPlexRecommendation>> arrayRecommendationsMap = new HashMap<String, List<VPlexRecommendation>>();
	    	for (Recommendation recommendation : recommendations) {
	    		VPlexRecommendation vplexRecommendation = (VPlexRecommendation) recommendation;
	    		String storageId = vplexRecommendation.getSourceStorageSystem().toString();
            	
	    		if (!arrayRecommendationsMap.containsKey(storageId)) {
	    			List<VPlexRecommendation> arrayRecommendations = new ArrayList<VPlexRecommendation>();
	    			arrayRecommendations.add(vplexRecommendation);
	    			arrayRecommendationsMap.put(storageId, arrayRecommendations);
	    		} 
	    		else {
	    				List<VPlexRecommendation> arrayRecommendations = arrayRecommendationsMap.get(storageId);
	    				arrayRecommendations.add(vplexRecommendation);
	    			}
	    	}
	    	return arrayRecommendationsMap;
	    }
	    
	    public List<VolumeDescriptor> prepareRecommendedVolumesForVPlex(VolumeCreate param, String task, TaskList taskList,
	            List<Recommendation> recommendations,  int volumeCounter,
	            String volumeLabel, List<Volume> preparedVolumes) {
	    	
	    	List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
	    	//build a map of array to recommendations
	    	URI vplexId = null;
	    	Map<String,List<VPlexRecommendation>> arrayRecommendationsMap = sortRecommendations(recommendations);
	    	
		_log.info("inside prepareRVforVplex, param.getCount = {} ",param.getCount());
	    	URI[][] arrayVolumeURIs = new URI[2][param.getCount()];
		_log.info("Initialized  arrayVolumeURIs : {}" ,arrayVolumeURIs.length);
	    	int arrayCounter = 0;
	    	Map<URI,List<URI>> storageSystemNetworkMap = new HashMap<URI,List<URI>>();
	        for (String array : arrayRecommendationsMap.keySet()){
	        	List<VPlexRecommendation> arrayRecommendations = arrayRecommendationsMap.get(array);
	        	_log.info("IN CVST, prepareRecoVolumesfor Vplex, arrayRecommendations: " + arrayRecommendations.size());
	        	for (VPlexRecommendation recommendation : arrayRecommendations){
			  if(recommendation.getSourceVolume() != null){
		        	storageSystemNetworkMap.put(recommendation.getSourceStorageSystem(), recommendation.getBackendNetworkList());
				_log.info("Setting arrayVolumeURIs, volumeId :  {}",recommendation.getSourceVolume());
	                	arrayVolumeURIs[arrayCounter][volumeCounter] = recommendation.getSourceVolume();
 				vplexId = recommendation.getVPlexStorageSystem();

	        	  }else{
		        	storageSystemNetworkMap.put(recommendation.getSourceStorageSystem(), recommendation.getBackendNetworkList());
		            vplexId = recommendation.getVPlexStorageSystem();
	        		String newVolumeLabel = AbstractBlockServiceApiImpl.generateDefaultVolumeLabel(param.getName(), volumeCounter, param.getCount());
	        		newVolumeLabel = generateBackendVolumeLabel(newVolumeLabel,arrayCounter);
	        	_log.info(" newVolumeLabel {} ",newVolumeLabel);
		
	                // Grab the existing volume and task object from the incoming task list      
 			Volume volume = getPrecreatedVolume(this.blockService._dbClient, taskList, newVolumeLabel);
			//_log.info("Done checking PrecreatedVolume");
	                boolean volumePrecreated = false;
	                if (volume != null) {
	                    volumePrecreated = true;
	                }
			

	                long size = SizeUtil.translateSize(param.getSize());
	                _log.info("Size inside prepareRecommendedVolumesForVPlex" + size);
			long thinVolumePreAllocationSize = 0;
	                
	                //Build a temp volume recommendation to pass to prepareVolume method
	                VolumeRecommendation tmpRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_VOLUME,	size, null, null);

			tmpRecommendation.addStoragePool(recommendation.getSourceStoragePool());

			tmpRecommendation.addStorageSystem(recommendation.getSourceStorageSystem());
		
	                volume = prepareVolume(this.blockService._dbClient, volume, size,  tmpRecommendation, newVolumeLabel);
			_log.info("1 Done Preparing volumes inside prepareRecommendedVolumesForVPlex {}" , volume);
			_log.info("2 Prepared volume Id: {} ",volume.getId());
			_log.info("3 volume:  {}",volume.toString() );
	                _log.info("4 volume: {}" , volume.forDisplay() );
			_log.info("5 volume : {}" +volume.forDisplay() );
			_log.info("6 volume:  {}"+volume.toString() );
			_log.info("11 blockService.dbClient : {}" , this.blockService._dbClient.toString());
	                
	                // add volume to reserved capacity map of storage pool
	              	_log.info("Inside prepareRecommendedVolumesForVPlex, adding volume to reserved capacity map of storage pool");
			StorageScheduler.addVolumeCapacityToReservedCapacityMap(this.blockService._dbClient, volume);
			_log.info("Inside prepareRecommendedVolumesForVPlex, DONE adding volume to reserved capacity map of storage pool");

	                preparedVolumes.add(volume);
			_log.info("Setting arrayVolumeURIs, volume.getId : {}",volume.getId());
	                arrayVolumeURIs[arrayCounter][volumeCounter] = volume.getId();
			if (!volumePrecreated) {
	                    Operation op = this.blockService._dbClient.createTaskOpStatus(Volume.class, volume.getId(),
	                            task, ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
	                    volume.getOpStatus().put(task, op);
	                    TaskResourceRep volumeTask = toTask(volume, task, op);
	                    // This task addition is inconsequential since we've already returned the source volume tasks.
	                    // It is good to continue to have a task associated with this volume AND store its status in the volume.
	                    taskList.getTaskList().add(volumeTask);
	                }

	                VolumeDescriptor descriptor = prepareVolumeDescriptor(volume);
	                descriptors.add(descriptor);
			}
			volumeCounter++;
	        	}
	        	arrayCounter++;
	        } 
	        
	        for (int i=0;i<param.getCount();i++){

			 
	        	long size = SizeUtil.translateSize(param.getSize());
	        	String vplexVolumeLabel = AbstractBlockServiceApiImpl.generateDefaultVolumeLabel(param.getName(), volumeCounter++, param.getCount());
	 		// Grab the existing volume and task object from the incoming task list
	                Volume virtualVolume = getPrecreatedVolume(this.blockService._dbClient, taskList, vplexVolumeLabel);
			//_log.info("Done checking PrecreatedVolume");
	                boolean volumePrecreated = false;
	                if (virtualVolume != null) {
	                    volumePrecreated = true;
	                  }
                        URI beNetworkURI_1= null;
                        URI beNetworkURI_2= null;
		        if(param.getPassThroughParams().containsKey("isVplexHA")){
				if(param.getPassThroughParams().get("HA-backend-network-1") != null && param.getPassThroughParams().get("HA-backend-network-1") !=null)
				{
		            	  String HA_backend_network_1 = param.getPassThroughParams().get("HA-backend-network-1");
		            	  String HA_backend_network_2 = param.getPassThroughParams().get("HA-backend-network-1");
		            	  beNetworkURI_1 = URI.create(HA_backend_network_1);
		            	  beNetworkURI_2 = URI.create(HA_backend_network_2);
				}
		        }else {
		            	if(param.getPassThroughParams().get("backend-network-1") != null 
	            			&& param.getPassThroughParams().get("backend-network-2") !=null)
				{
		            		String backendNetwork_1 = param.getPassThroughParams().get("backend-network-1");
		            		String backendNetwork_2 = param.getPassThroughParams().get("backend-network-2");
		            		beNetworkURI_1 = URI.create(backendNetwork_1);
		            		beNetworkURI_2 = URI.create(backendNetwork_2);
				}
		        }
            	virtualVolume = prepareVplexVolume(this.blockService._dbClient,null, size, vplexId,vplexVolumeLabel);
            	// set the associated volumes here
            	StringSet associatedVolumes = new StringSet();
                associatedVolumes.add(arrayVolumeURIs[0][i].toString());
                _log.info("Associating volume {}", arrayVolumeURIs[0][i].toString());
                // If these are HA virtual volumes there are two volumes
                // associated with the virtual volume.
                if (arrayCounter > 1) {
                    associatedVolumes.add(arrayVolumeURIs[1][i].toString());
                    _log.info("Associating volume {}", arrayVolumeURIs[1][i].toString());
                }
                virtualVolume.setAssociatedVolumes(associatedVolumes);
               _log.info("2Associated Volume {}", associatedVolumes); 
                _log.info("22Associated Volume {}", virtualVolume.forDisplay());
		 this.blockService._dbClient.updateObject(virtualVolume);
            	 preparedVolumes.add(virtualVolume);
                _log.info("23preparedVolumes {}", preparedVolumes.toString());
	                
                 Operation op = this.blockService._dbClient.createTaskOpStatus(Volume.class, virtualVolume.getId(),
                         task, ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
                 virtualVolume.getOpStatus().put(task, op);
                 TaskResourceRep volumeTask = toTask(virtualVolume, task, op);
                 // This task addition is inconsequential since we've already returned the source volume tasks.
                 // It is good to continue to have a task associated with this volume AND store its status in the volume.
                 taskList.getTaskList().add(volumeTask);
                 
                 VolumeDescriptor descriptor = new VolumeDescriptor(
                         VolumeDescriptor.Type.VPLEX_VIRT_VOLUME, vplexId, virtualVolume.getId(),
                         null, consistencyGroup == null ? null : consistencyGroup.getId(),
                      		 new VirtualPoolCapabilityValuesWrapper(), virtualVolume.getCapacity());
		descriptor.getParameters().put(VolumeDescriptor.PARAM_VPLEX_BE_NETWORKS, storageSystemNetworkMap);             
		_log.info("VPLEX Descriptor {}", descriptor.toString() );
                 descriptors.add(descriptor);
	        }
	        return descriptors;
	    	
	    }
	  
	    public List<VolumeDescriptor> prepareRecommendedVolumes(VolumeCreate param, String task, TaskList taskList,
	            List<Recommendation> recommendations,  int volumeCounter,
	            String volumeLabel, List<Volume> preparedVolumes) {
	        Iterator<Recommendation> recommendationsIter = recommendations.iterator();
	        while (recommendationsIter.hasNext()) {
	            VolumeRecommendation recommendation =  (VolumeRecommendation)recommendationsIter.next();
	            // if id is already set in recommendation, do not prepare the volume (volume already exists)
	            if (recommendation.getId() != null) {
	                continue;
	            }
	           
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

	            } else  if (recommendation.getType().toString().equals(VolumeRecommendation.VolumeType.VPLEX_VIRTUAL_VOLUME.toString())) {
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
	        List<VolumeDescriptor> descriptors = prepareVolumeDescriptors(preparedVolumes);
	        return descriptors;
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
	    
	/* public static void prepareVolumesForVplex(){
	    	while (recommendationsIter.hasNext()) {
                VPlexRecommendation recommendation = recommendationsIter.next();
                URI storageDeviceURI = recommendation.getSourceStorageSystem();
                URI storagePoolURI = recommendation.getSourceStoragePool();
                String newVolumeLabel = generateVolumeLabel(volumeLabel, varrayCount, volumeCounter, resourceCount);
                long thinVolumePreAllocationSize = 0;
                Volume volume = prepareVolume(VolumeType.BLOCK_VOLUME, null,
                     size, thinVolumePreAllocationSize, vplexProject,
                     null, null, storageDeviceURI,
                     storagePoolURI, newVolumeLabel, null, null);*/
	 
	    public static Volume prepareVplexVolume(DbClient dbClient, Volume volume, long size,  URI vplexId, String label) {
	    	
		VolumeCreate param = null;
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
//TODO: Is a volume type to be set anywhere?
	        volume.setSyncActive(!Boolean.valueOf(false));
	        volume.setLabel(label);
	        volume.setCapacity(size);

	        volume.setThinlyProvisioned(false);
	        volume.setVirtualPool(null);
	        volume.setProject(null);
	        volume.setTenant(null);
	        volume.setVirtualArray(null);
	        URI poolId = null;

	        volume.setStorageController(vplexId);
	        volume.setPool(poolId);
	        StringSet protocols = new StringSet();
	        protocols.add(StorageProtocol.Block.FC.name());
	        volume.setProtocol(protocols);

	        if (newVolume) {
	            dbClient.createObject(volume);
	        } else {
	            dbClient.updateAndReindexObject(volume);
	        }

	        return volume;
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
                  _log.info("created volume");
	            dbClient.createObject(volume);
	        } else {
                  _log.info("updated volume");
	            dbClient.updateAndReindexObject(volume);
	        }

	        return volume;
	    }
    @Override
    public void run() {
        _log.info("Starting scheduling/placement thread...");
        // Call out placementManager to get the recommendation for placement.
        try {
        	List<Recommendation> recommendations = new ArrayList<Recommendation>();
		if ( param.getPassThroughParams() != null && !param.getPassThroughParams().isEmpty()
                   && param.getPassThroughParams().containsKey("VPlex-Id")  
			&& param.getPassThroughParams().containsKey("volumeId")){
        		_log.info("calling method bypassRecommendationsForVplexVolumeResources");
        		recommendations = this.bypassRecommendationsForVplexVolumeResources(param);
			    _log.info("Done with recommendations {} ",recommendations);
        		_log.info("Initiating descriptors, calling createVolumes");
        		this.createVolumes(param,  recommendations, taskList, task, capabilities);  
        		//this.preparedVolumes(param, recommendations, taskList, task, capabilities);
        		return;
        	}
        	else if ( param.getPassThroughParams() != null && !param.getPassThroughParams().isEmpty() && param.getPassThroughParams().containsKey("VPlex-Id")  ){
        		_log.info("calling method bypassRecommendationsForVplexResources");
        		recommendations = this.bypassRecommendationsForVplexResources(param);
			_log.info("Done with recommendations {} ",recommendations);
        		_log.info("Initiating descriptors, calling createVolumes");
        		this.createVolumes(param,  recommendations, taskList, task, capabilities);  
        		//this.preparedVolumes(param, recommendations, taskList, task, capabilities);
        		return;
        	}
        	
        	else if (param.getPassThroughParams() != null && !param.getPassThroughParams().isEmpty()){
        		recommendations = this.bypassRecommendationsForResources(param);
        		this.createVolumes(param,  recommendations, taskList, task, capabilities);                        
        		return;
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
                this.blockService._dbClient.updateObject(volume);
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
