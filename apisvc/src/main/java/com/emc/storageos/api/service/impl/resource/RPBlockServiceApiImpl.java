/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.Controller;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.RecoverPointScheduler;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.CapacityCalculatorFactory;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RPSiteArray;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.systems.StorageSystemConnectivityRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.protectioncontroller.RPController;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.util.ConnectivityUtil.StorageSystemType;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Protection;
import com.emc.storageos.volumecontroller.RPProtectionRecommendation;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.VPlexProtectionRecommendation;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * Block Service subtask (parts of larger operations) RecoverPoint implementation.
 */
public class RPBlockServiceApiImpl extends AbstractBlockServiceApiImpl<RecoverPointScheduler> {
    private static final String VOLUME_TYPE_TARGET_JOURNAL = "-target-journal-";
	private static final String VOLUME_TYPE_TARGET = "-target-";
	private static final Logger _log = LoggerFactory.getLogger(RPBlockServiceApiImpl.class);
    public RPBlockServiceApiImpl() { super(DiscoveredDataObject.Type.rp.name()); }

    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";
    
    private static final String VIRTUAL_ARRAYS_CONSTRAINT = "virtualArrays";
    private static final String UNKNOWN_VOL_TYPE = "unknown";

    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    @Autowired
    protected DependencyChecker _dependencyChecker;

    @Autowired
    protected CapacityCalculatorFactory capacityCalculatorFactory;

    private RPHelper _rpHelper;

    public void setRpHelper(RPHelper rpHelper) {
        _rpHelper = rpHelper;
    }
    
    VPlexBlockServiceApiImpl vplexBlockServiceApiImpl;

    public VPlexBlockServiceApiImpl getVplexBlockServiceApiImpl() {
        return vplexBlockServiceApiImpl;
    }

    public void setVplexBlockServiceApiImpl(
            VPlexBlockServiceApiImpl vplexBlockServiceApiImpl) {
        this.vplexBlockServiceApiImpl = vplexBlockServiceApiImpl;
    }

    protected CoordinatorClient _coordinator;
    @Override
    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    /**
     * Looks up controller dependency for given hardware
     *
     * @param clazz controller interface
     * @param hw hardware name
     * @param <T>
     * @return
     */
    @Override
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
              return _coordinator.locateService(
                       clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    private List<Recommendation> getRecommendationsForVirtualPoolChangeRequest(Volume volume, VirtualPool newVpool, VirtualPoolChangeParam cosChangeParam) {
        Project project = _dbClient.queryObject(Project.class, volume.getProject());

        // Protection volume placement is requested.
        return getBlockScheduler().scheduleStorageForVpoolChangeUnprotected(
                volume, newVpool,
                RecoverPointScheduler.getProtectionVirtualArraysForVirtualPool(project, newVpool, _dbClient, _permissionsHelper), cosChangeParam);
    }
    
    /**
     * Prepare Recommended Volumes for Protected scenarios only.
     *
     * This method is responsible for acting the same as the unprotected "prepareRecommendedVolumes" call,
     * however it needs to create multiple volumes per single volume requests in order to generate protection.
     *
     * Those most typical scenario is, that for any one volume requested in a CRR configuration, we create:
     * 1. One Source Volume
     * 2. One Source Journal Volume (minimum 10GB, otherwise 2.5X source size)
     * 3. One Target Volume on protection varray
     * 4. One Target Journal Volume on protection varray
     *
     * In a CLR configuration, there are additional volumes created for the Local Target and Local Target Journal.
     *
     * This method will assemble a ProtectionSet object in Cassandra that will describe the Protection that
     * will be created on the Protection System.
     *
     * When other protection mechanisms come on board, the RP-ness of this method will need to be pulled out.
     *
     * @param param volume create request
     * @param task task from request or generated
     * @param taskList task list
     * @param project project from request
     * @param varray varray from request
     * @param vpool vpool from request
     * @param numberOfVolumesInRequest volume count from the request
     * @param recommendations list of resulting recommendations from placement
     * @param consistencyGroup consistency group ID
     * @return list of volume URIs created
     */
    private List<URI> prepareRecommendedVolumes(VolumeCreate param, String task, TaskList taskList,
			            Project project, VirtualArray varray, VirtualPool vpool, Integer numberOfVolumesInRequest,
			            List<Recommendation> recommendations, BlockConsistencyGroup consistencyGroup,
			            String volumeLabel) {
    	List<URI> volumeURIs = new ArrayList<URI>();
    	Volume sourceJournal = null;
    	boolean createTargetJournal = false;
    	
    	// If the CG already contains RP volumes, then we need to check if new/additional journal volumes need to be created, based on the 
    	// journal policy specified. 
    	List<Volume> cgSourceVolumes = _rpHelper.getCgVolumes(consistencyGroup.getId(), Volume.PersonalityTypes.SOURCE.toString());
    	if (!cgSourceVolumes.isEmpty()) { 
        	String sourceInternalSiteName = cgSourceVolumes.get(0).getInternalSiteName();
    		if (!_rpHelper.isAdditionalJournalRequiredForCG(vpool.getJournalSize(), consistencyGroup, param.getSize(), 
    							numberOfVolumesInRequest, Volume.PersonalityTypes.SOURCE.toString(), sourceInternalSiteName)){
    			// if the CG contains volumes already and no new additional journals are provisioned, 
    			// then we simply update the reference on the source for the journal volume.
    			sourceJournal = _rpHelper.selectExistingJournalForSourceVolume(cgSourceVolumes, false);
    		}
    	}

        // Create an entire Protection object for each recommendation result.
        Iterator<Recommendation> recommendationsIter = recommendations.iterator();   
        boolean volumeVirtualPoolChange = false;
        
        while (recommendationsIter.hasNext()) {
            RPProtectionRecommendation recommendation = (RPProtectionRecommendation)recommendationsIter.next();
            if (recommendation.getVpoolChangeVolume() != null) {
                volumeVirtualPoolChange = true;
                break;
            }
        }
        
        // Only validate the volume labels if we are creating a new source volume.
        if (!volumeVirtualPoolChange) {
            // validate generated volume labels before creating the volumes
            validateDefaultVolumeLabels(param.getName(), numberOfVolumesInRequest, project);
        }
        
        recommendationsIter = recommendations.iterator(); 
        
        while (recommendationsIter.hasNext()) {
            RPProtectionRecommendation recommendation = (RPProtectionRecommendation)recommendationsIter.next();

            Map<URI, URI> protectionVarrayTgtJournal = new HashMap<URI, URI>();
            
            // Prepare the Bourne Volumes to be created and associated
            // with the actual storage system volumes created. Also create
            // a BlockTaskList containing the list of task resources to be
            // returned for the purpose of monitoring the volume creation
            // operation for each volume to be created.
            for (int volumeCount = 0; volumeCount < numberOfVolumesInRequest; volumeCount++) {
                String newVolumeLabel = generateDefaultVolumeLabel(param.getName(), volumeCount, numberOfVolumesInRequest);

                // Assemble a Replication Set; A Collection of volumes.  One production, and any number of targets.
                String rsetName = null;
                if (numberOfVolumesInRequest > 1) {
                    rsetName = "RSet-" + param.getName() + "-" + (volumeCount+1);
                } else {
                    rsetName = "RSet-" + param.getName();
                }

                String srcCopyName = varray.getLabel() + " - Original Production";

                Volume srcVolume = null;
                if (recommendation.getVpoolChangeVolume() == null) {
                    srcVolume = prepareVolume(project, varray, vpool, param.getSize(), recommendation,
                            newVolumeLabel, consistencyGroup, task, false, recommendation.getProtectionDevice(),
                            Volume.PersonalityTypes.SOURCE, rsetName, recommendation.getSourceInternalSiteName(), srcCopyName, null, null);
                    volumeURIs.add(srcVolume.getId());
					taskList.getTaskList().add(toTask(srcVolume, task));
                } else {
                    srcVolume = _dbClient.queryObject(Volume.class, recommendation.getVpoolChangeVolume());
                    Operation op = _dbClient.createTaskOpStatus(Volume.class, srcVolume.getId(), task,
                            ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
                    // Fill in additional information that prepare would've filled in that's specific to RP.
                    // Best to only fill in information here that isn't harmful if a rollback occurred
                    // and the protection never got set up.
                    srcVolume.setRSetName(rsetName);
                    srcVolume.setRpCopyName(srcCopyName);
                    srcVolume.setInternalSiteName(recommendation.getSourceInternalSiteName());
                    srcVolume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
                    _dbClient.persistObject(srcVolume);
                    volumeURIs.add(srcVolume.getId());
					taskList.getTaskList().add(toTask(srcVolume, task, op));
                }
                
                // If there's no Journal volumes created yet, let's do that now.  We'll likely make this more configurable.
                // Source Journal volume (default for now).  It's ready for multiple volumes, too.
                if (sourceJournal == null) {
	                String size = String.valueOf(RPHelper.getJournalSizeGivenPolicy(param.getSize(), vpool.getJournalSize(), numberOfVolumesInRequest));
	                VirtualArray journalVarray = varray;
	                if (vpool.getJournalVarray() != null) {
	                	journalVarray = _dbClient.queryObject(VirtualArray.class, URI.create(vpool.getJournalVarray()));
	                }
	                VirtualPool journalVpool = vpool; 
	                if (vpool.getJournalVpool() != null){
	                	journalVpool = _dbClient.queryObject(VirtualPool.class,URI.create(vpool.getJournalVpool()));
	                }
	                sourceJournal = prepareJournalVolume(project, journalVarray, journalVpool, size, recommendation,
	                        recommendation.getSourceJournalStoragePool(), new StringBuilder(newVolumeLabel).append("-journal-prod").toString(),
	                        consistencyGroup, task, false,
	                        recommendation.getProtectionDevice(), Volume.PersonalityTypes.METADATA,
	                        null, recommendation.getSourceInternalSiteName(), srcCopyName, null);
	                volumeURIs.add(sourceJournal.getId());
                } 

                // Set the source volume journal reference
                srcVolume.setRpJournalVolume(sourceJournal.getId());
                _dbClient.persistObject(srcVolume);
                
                for (VirtualArray protectionVirtualArray : RecoverPointScheduler.getProtectionVirtualArraysForVirtualPool(project, vpool, _dbClient,
                                                                                                   _permissionsHelper)) {
                    VpoolProtectionVarraySettings settings = _rpHelper.getProtectionSettings(vpool, protectionVirtualArray);  
                    String targetInternalSiteName = recommendation.getVirtualArrayProtectionMap().get(protectionVirtualArray.getId()).getTargetInternalSiteName();
                    // whether additional journals are required computation needs to happen only once per copy, irrespective of number of volumes requested
                    if (volumeCount == 0 && (cgSourceVolumes.isEmpty() || _rpHelper.isAdditionalJournalRequiredForCG(settings.getJournalSize(), 
                    							consistencyGroup, param.getSize(), numberOfVolumesInRequest, Volume.PersonalityTypes.TARGET.toString(), targetInternalSiteName))) {
                    	createTargetJournal = true; 
                    }

                    // Prepare and populate CG request for the RP targets and Journals
                    List<URI> targetVolumes = prepareTargetVolumes(param, project, vpool, recommendation, new StringBuilder(newVolumeLabel), rsetName,
                            protectionVirtualArray,	consistencyGroup, settings, srcVolume, createTargetJournal, task, numberOfVolumesInRequest, taskList);
                    
                    if (createTargetJournal) {
                        // If we are creating a journal volume for a protection varray target volume, we
                        // need to keep track of it.  If the volume count is > 0 we will share the target
                        // journals.  End result is a single journal for each protection varray that will
                        // be shared by each of the corresponding protection varray targets.
                        Volume targetVolume = null;
                        for (URI targetVolumeUri : targetVolumes) {
                            Volume targetVol = _dbClient.queryObject(Volume.class, targetVolumeUri);
                            if (targetVol.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.METADATA.toString())) {
                                protectionVarrayTgtJournal.put(protectionVirtualArray.getId(), targetVol.getId());
                                break;
                            } else if (targetVol.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString())) {
                                targetVolume = targetVol;
                            }
                        } 
                        
                        if (targetVolume != null) {
                            URI journalVolumeUri = protectionVarrayTgtJournal.get(protectionVirtualArray.getId());
                            setJournalVolumeReference(targetVolume, journalVolumeUri);
                        }
                        
                    } else {
                        // If we aren't creating target journals we will only have 1 volume (the target) in targetVolumes.
                        // Set the target volume journal reference.
                    	
                    	Volume journalVolume = _rpHelper.selectExistingJournalForTargetVolume(_rpHelper.getCgVolumes(consistencyGroup.getId(), 
                    								Volume.PersonalityTypes.TARGET.toString()), protectionVarrayTgtJournal, protectionVirtualArray.getId(), targetInternalSiteName);
                    	for(URI targetVolumeURI : targetVolumes) {
                    		Volume targetVolume = _dbClient.queryObject(Volume.class, targetVolumeURI);
                    		if (targetVolume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString())) {
                    			 setJournalVolumeReference(targetVolume, journalVolume.getId());
                    		}
                    	}                       
                    }
                    
                    volumeURIs.addAll(targetVolumes);
                    createTargetJournal = false;
                }                                
            }
        }

        return volumeURIs;
    }


    /**
     * Convenience method to set the journal volume reference.
     * @param volume
     * @param journalVolumeUri
     */
    private void setJournalVolumeReference(Volume volume, URI journalVolumeUri) {
        volume.setRpJournalVolume(journalVolumeUri);
        _dbClient.persistObject(volume);
    }
    
    /**
     * Convenience method to set the journal volume reference.
     * @param volumeUri
     * @param journalVolumeUri
     */
    private void setJournalVolumeReference(URI volumeUri, URI journalVolumeUri) {
        Volume volume = _dbClient.queryObject(Volume.class, volumeUri);
        setJournalVolumeReference(volume, journalVolumeUri);
    }
    
    /**
     * Prep work to call the orchestrator to create the volume descriptors
     * @param recommendation recommendation object from RPRecommendation
     * @param volumeURIs volumes already prepared
     * @param capabilities vpool capabilities
     * @return list of volume descriptors
     * @throws ControllerException
     */
    private List<VolumeDescriptor> createVolumeDescriptors(RPProtectionRecommendation recommendation, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        List<Volume> preparedVolumes = _dbClient.queryObject(Volume.class, volumeURIs);

        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
        
        // Package up the Volume descriptors
        for (Volume volume : preparedVolumes) {
        	VolumeDescriptor.Type volumeType = VolumeDescriptor.Type.RP_SOURCE;

        	// Vpool Change flow, mark the production volume as already existing, so it doesn't get created
        	if (recommendation != null && recommendation.getVpoolChangeVolume() != null &&
        		recommendation.getVpoolChangeVolume().equals(volume.getId())) {
        	    if (recommendation.isVpoolChangeProtectionAlreadyExists()) {
                    volumeType = VolumeDescriptor.Type.RP_EXISTING_PROTECTED_SOURCE;
                }
                else {
                    volumeType = VolumeDescriptor.Type.RP_EXISTING_SOURCE;
                }
            	VolumeDescriptor desc = new VolumeDescriptor(
            			volumeType, volume.getStorageController(), volume.getId(), volume.getPool(), null, capabilities, volume.getCapacity());
            	Map<String, Object> volumeParams = new HashMap<String, Object>();
            	volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VOLUME_ID, recommendation.getVpoolChangeVolume());
            	volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID, recommendation.getVpoolChangeVpool());
            	volumeParams.put(VolumeDescriptor.PARAM_VPOOL_OLD_VPOOL_ID, volume.getVirtualPool());            	            	
            	
            	desc.setParameters(volumeParams);
            	descriptors.add(desc);

            	_log.info("Adding Existing Source Volume Descriptor for: " + desc.toString());
        	} else {
        		// Normal create-from-scratch flow
        		if (volume.getPersonality() == null) {
        			throw APIException.badRequests.missingPersonalityAttribute(String.valueOf(volume.getId()));
        		}
        		
        		if (volume.getPersonality().equals(Volume.PersonalityTypes.TARGET.toString())) {
        			volumeType = VolumeDescriptor.Type.RP_TARGET;
        		} else if (volume.getPersonality().equals(Volume.PersonalityTypes.METADATA.toString())) {
        			volumeType = VolumeDescriptor.Type.RP_JOURNAL;
        		}
        		
        		VolumeDescriptor desc = new VolumeDescriptor(
        				volumeType, volume.getStorageController(), volume.getId(), volume.getPool(), null, capabilities, volume.getCapacity());
        		descriptors.add(desc);

        		_log.info("Adding Volume Descriptor for: " + desc.toString());
        	}
        }

        return descriptors;
    } 
    
    /**
     * Adds a Volume to StorageSystem mapping entry in the provided Map. 
     * 
     * @param volumeStorageSystems the Map in which we want to add an entry
     * @param volume the Volume the Volume referencing the StorageSystem we want to Map
     */
    protected void addVolumeStorageSystem(Map<URI, StorageSystem> volumeStorageSystems, Volume volume) {
    	if (volumeStorageSystems == null) {
    		volumeStorageSystems = new HashMap<URI, StorageSystem>();
    	}
    	
    	StorageSystem volumeStorageSystem = volumeStorageSystems.get(volume.getStorageController());
    	if (volumeStorageSystem == null) {
    	    volumeStorageSystem = 
    	    		_dbClient.queryObject(StorageSystem.class, volume.getStorageController());
    	    volumeStorageSystems.put(volumeStorageSystem.getId(), volumeStorageSystem);
    	}
    }
 
    /**
     * This method computes a matching volume allocation capacity across all protection 
     * arrays. Some storage systems will allocate a slightly larger capacity than
     * requested so volume sizes can become inconsistent between source and target.
     * <p>
     * If we are protecting between different array types, we need to determine the
     * actual allocation size on each array.  Set the capacity of the source and
     * target volumes to be the larger of the actual allocation sizes.  This is done
     * to ensure the size of the source and target volumes are identical so  RP can
     * create the CG properly.
     * 
     * This method returns the size of the volume to be created taking into account the 
     * above considerations.
     *     
     * @param volumeURIs 
     * @param requestedSize Request size of the volume to be expanded
     * @param isExpand Expand or Create volume operation
     * @return the final capacity used
     */
    protected Long computeProtectionCapacity(List<URI> volumeURIs, Long requestedSize, boolean isExpand, boolean isChangeVpool, List<VolumeDescriptor> volumeDescriptors) {    
        List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeURIs);
        _log.info("Performing checks to see if all volumes are of the same System Type and capacity for Protection.");
        Map<URI, StorageSystem> volumeStorageSystems = new HashMap<URI, StorageSystem>();
        List<Volume> allVolumesToCompare = new ArrayList<Volume>();
        List<Volume> allVolumesToUpdateCapacity = new ArrayList<Volume>();
        List<Long> currentVolumeSizes = new ArrayList<Long>();
        Map<URI, String> associatedVolumePersonalityMap = new HashMap<URI, String>();
        Long capacity = 0L;

        // We could be performing a change vpool for RP+VPLEX / MetroPoint. This means
        // we could potentially have migrations that need to be done on the backend
        // volumes. If migration info exists we need to collect that ahead of time.
        List<VolumeDescriptor> migrateDescriptors = null;
        List<Migration> migrations = null; 
        if (volumeDescriptors != null) {        
            migrateDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_MIGRATE_VOLUME }, null );
            
            if (migrateDescriptors != null && !migrateDescriptors.isEmpty()) {
                _log.info("Data Migration detected, this is due to a change virtual pool operation on RP+VPLEX or MetroPoint.");
                // Load the migration objects for use later
                migrations = new ArrayList<Migration>();            
                Iterator<VolumeDescriptor> migrationIter = migrateDescriptors.iterator();                
                while (migrationIter.hasNext()) {
                    Migration migration = _dbClient.queryObject(Migration.class, migrationIter.next().getMigrationId());
                    migrations.add(migration);
                }
            }
        }                        
                       
        for (Volume volume : volumes) {
            // Find the source/target volumes and cache their storage systems.
            // Since these will be VPLEX virtual volumes, we need to grab the associated
            // volumes as those are the real backing volumes.
            if (volume.getPersonality() != null) {
                if (volume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())
                            || volume.getPersonality().equals(Volume.PersonalityTypes.TARGET.toString())) {
                    
                    allVolumesToUpdateCapacity.add(volume);                   
                    _log.info("Adding Volume [{}] to potentially have capacity adjusted.", volume.getLabel());
                      
                    // If there are associated volumes, this must be a Virtual Volume for RP+VPLEX 
                    // and we also need to load those associated/backend volumes for comparison 
                    StringSet associatedVolumes = volume.getAssociatedVolumes();
                    if (associatedVolumes != null && !associatedVolumes.isEmpty()) {   
                        _log.info("Volume [{}] is a VPLEX virtual volume.", volume.getLabel());                                                                       
                        Iterator<String> it = associatedVolumes.iterator();
                        while (it.hasNext()) {
                            URI associatedVolumeURI = URI.create(it.next());
                            Volume associatedVolume = _dbClient.queryObject(Volume.class, associatedVolumeURI);
                            
                            // Check to see if there is a migration for this backend volume
                            if (migrations != null && !migrations.isEmpty()) {        
                                for (Migration migration : migrations) {                                
                                    if (migration.getSource().equals(associatedVolume.getId())) {
                                        _log.info("VPLEX backing volume [{}] has a migration, using migration volume instead.", associatedVolume.getLabel());
                                        // Use the migration volume instead for the capacity adjustment check
                                        associatedVolume = _dbClient.queryObject(Volume.class, migration.getTarget());
                                        break;
                                    }
                                }
                            }                            
                            // Check sizes, if provisioned is greater than 0 use that to compare sizes
                            // otherwise use the requested capacity
                            if (associatedVolume.getProvisionedCapacity().longValue() > 0) {
                                currentVolumeSizes.add(associatedVolume.getProvisionedCapacity());
                            }
                            else {
                                currentVolumeSizes.add(associatedVolume.getCapacity());
                            }
                            
                            addVolumeStorageSystem(volumeStorageSystems, associatedVolume);
                            allVolumesToCompare.add(associatedVolume);
                            allVolumesToUpdateCapacity.add(associatedVolume);
                            associatedVolumePersonalityMap.put(associatedVolume.getId(), volume.getPersonality());
                            _log.info("Adding Volume [{}] to potentially have capacity adjusted.", associatedVolume.getLabel());                            
                        }
                    }
                    else {
                        // Not a VPLEX Virtual Volume, the volume itself can be used.
                        _log.info("Volume [{}] is not VPLEX virtual volume.", volume.getLabel());                        
                        
                        // Check sizes, if provisioned is greater than 0 use that to compare sizes
                        // otherwise use the requested capacity
                        if (volume.getProvisionedCapacity().longValue() > 0) {
                            currentVolumeSizes.add(volume.getProvisionedCapacity());
                        }
                        else {
                            currentVolumeSizes.add(volume.getCapacity());
                        }
                        
                        addVolumeStorageSystem(volumeStorageSystems, volume);
                        allVolumesToCompare.add(volume);
                    }                
                }
            }
            else {
                _log.warn("Volume [{}] does not have PERSONALITY set. We will not be able to compare this volume.", volume.getLabel());
            }
        }
        
        // There should be at least 2 volumes to compare, Source and Target (if not more)
        if (!allVolumesToCompare.isEmpty() && (allVolumesToCompare.size() >= 2)) {
            StorageSystem storageSystem = null;
            StorageSystem storageSystemToCompare = null;
            boolean storageSystemsMatch = true;
                                    
            // Determine if all the source/target storage systems involved are the same based
            // on type, model, and firmware version.
            for (URI storageSystemKey : volumeStorageSystems.keySet()) {
                if (storageSystemToCompare == null) {
                    // Find a base for the comparison, the first element will do.
                    storageSystemToCompare = volumeStorageSystems.get(storageSystemKey);
                    // set storageSystem to first element if there is only one
                    if (volumeStorageSystems.size() == 1) {
                        storageSystem = volumeStorageSystems.get(storageSystemKey);
                    }
                    continue;
                }

                storageSystem = volumeStorageSystems.get(storageSystemKey);

                if (!storageSystemToCompare.getSystemType().equals(storageSystem.getSystemType())) {
                    // The storage systems do not all match so we need to determine the allocated 
                    // capacity on each system.                 
                    storageSystemsMatch = false;
                    break;
                }
            }
                        
            // If the storage systems do not all match we need to figure out matching volume
            // allocation sizes for all storage systems. CG creation will likely fail if
            // we don't do this.
            if (!storageSystemsMatch || !allVolumeSizesMatch(currentVolumeSizes)) {
                // The storage systems are not all the same so now we must find a volume allocation size
                // that matches between all arrays.
                _log.warn("The storage systems for all volumes do not match or all volume sizes do not match. " +
                            "This could cause RP CG creation to fail. " + 
                            "Potentially need to adjust capacity size of volumes to be consistent across all source/targets.");                             
                
                List<Volume> tempVolumesList = new ArrayList<Volume>();
                Long currentVolumeCapacity = 0L;
                Long volumeToCompareCapacity = 0L;
                boolean matched = true;
                
                Long capacityToUseInCalculation = Collections.max(currentVolumeSizes);
                if (isExpand) {
                    capacityToUseInCalculation = requestedSize;
                } 
                
                _log.info(String.format("The capacity to match to is [%s]", capacityToUseInCalculation.toString()));
                
                // Determine if the provisioning request requires storage systems 
                // which cannot allocate storage at the exact same amount
                if (!capacitiesCanMatch(volumeStorageSystems)) {
                	setUnMatchedCapacities(allVolumesToUpdateCapacity, associatedVolumePersonalityMap, isExpand, capacityToUseInCalculation);
                } else {
                	// Storage systems in the provisioning request can allocate storage capacity in equal amounts
                	
                	// Compare the actual capacity of each volume against the calculated capacity of 
                	// each of the other volumes. If we end up matching on the same capacity across 
                	// all volumes for all storage systems, we have found a winner.  If we can't match
                	// across all the storage systems, we just use the original requested volume capacity.  
                	// If the capacity of the source volume ends up being bigger than that of 
                	// the target, creating the CG will fail.
                	// 
                	// TODO: A more complex hand-shaking algorithm should be introduced here that can converge 
                	// on appropriate matching volume capacities across all storage systems.     
                	
                	for (int index = 0; index < allVolumesToCompare.size(); index++) {
                		matched = true;
                		tempVolumesList.clear();
                		tempVolumesList.addAll(allVolumesToCompare);
                		// Remove the current volume from the list and get a handle on it
                		Volume currentVolume = tempVolumesList.remove(index);

                		// Get the System Type for the current volume
                		String currentVolumeSystemType = volumeStorageSystems.get(currentVolume.getStorageController()).getSystemType();
                		// Calculate the capacity for the current volume based on the Storage System type to see if it can be adjusted
                		currentVolumeCapacity = 
                				capacityCalculatorFactory.getCapacityCalculator(currentVolumeSystemType)                                    
                				.calculateAllocatedCapacity(capacityToUseInCalculation);

                		_log.info(String.format("Volume [%s] has a capacity of %s on storage system type %s. " +
                				"The calculated capacity for this volume is %s.", 
                				currentVolume.getLabel(), 
                				currentVolume.getCapacity(), 
                				currentVolumeSystemType, 
                				currentVolumeCapacity));

                		// Compare the volume's capacity to the other volumes to see if the 
                		// capacities will match.
                		for (Volume volumeToCompare : tempVolumesList) {                        
                			// Get the System Type for the volume to compare
                			String volumeToCompareSystemType = volumeStorageSystems.get(volumeToCompare.getStorageController()).getSystemType();
                			// Make sure the volume to compare is not the same storage system type as the one we used to calculate the
                			// currentVolumeCapacity above.  We have already used that storage system with the capacity calculator so
                			// we don't want to adjust the capacity again, so just skip it.
                			if (volumeToCompareSystemType.equalsIgnoreCase(currentVolumeSystemType)) {
                				continue;
                			}

                			// Calculate the capacity for the volume to compare based on the Storage System type to see if it can be adjusted
                			volumeToCompareCapacity = 
                					capacityCalculatorFactory.getCapacityCalculator(volumeToCompareSystemType)
                					.calculateAllocatedCapacity(currentVolumeCapacity);   

                			// Check to see if the capacities match
                			if (!currentVolumeCapacity.equals(volumeToCompareCapacity)) {
                				// If the capacities don't match, we can not use this capacity across all volumes
                				// so we will have to check the next volume. Break out of this loop and warn the user.                            
                				_log.warn(String.format("Storage System %s is not capable of allocating exactly %s bytes for volume [%s], keep trying...", 
                						volumeToCompareSystemType,
                						currentVolumeCapacity,
                						volumeToCompare.getLabel()));           
                				matched = false;
                				break;
                			}
                			else {
                				_log.info(String.format("Volume [%s] is capable of being provisioned at %s bytes on storage system of type %s, continue...", 
                						volumeToCompare.getLabel(),
                						currentVolumeCapacity,
                						volumeToCompareSystemType)); 
                			}
                		}

                		// If all volume capacities match, we have a winner.
                		if (matched) {
                			break;
                		}
                	}

                	if (matched) {
                		// We have found capacity that is consistent across all Storage Systems
                		capacity = currentVolumeCapacity;
                		_log.info("Found a capacity size that is consistent across all source/target(s) storage systems: " + capacity);
                		for (Volume volume : allVolumesToUpdateCapacity) { 
                		    if (isChangeVpool 
                		            && NullColumnValueGetter.isNotNullValue(volume.getPersonality())
                		            && volume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())) {
                		        // Don't update the existing source if this is a change vpool
                		        continue;
                		    }
                			updateVolumeCapacity(volume, capacity, isExpand);                			                			
                		}
                	} else {
                		// Unable to determine a matching allocation volume size across all source -> target storage systems.
                		// Circumvent CG creation, which would fail, by throwing an exception here.
                		throw APIException.internalServerErrors.noMatchingAllocationCapacityFound();                
                	}
                }
            } else {                               
                _log.info(String.format("All storage systems match and/or all volume sizes are consistent. No need for any capacity adjustments."));
                
                capacity = requestedSize;
            }
        } else {
            _log.error("There were no volumes found to compare capacities.");
        }

        return capacity;
    }

    /**
    * Prepare Volume for a RecoverPoint protected volume
    *
    * @param param volume request
    * @param project project requested
    * @param varray varray requested
    * @param vpool vpool requested
    * @param size size of the volume
    * @param recommendation recommendation for placement
    * @param label volume label
    * @param consistencyGroup consistency group
    * @param token task id
    * @param remote is this a target volume
    * @param protectionStorageSystem protection system protecting this volume
    * @param personality normal volume or metadata
    * @param rsetName replication set name
    * @param internalSiteName replication site ID
    * @param rpCopyName copy name on RP CG
    * @param srcVolumeId source volume ID; only for target volumes
    * @return a persisted volume
    */
    public Volume prepareVolume(Project project, VirtualArray varray,
			            VirtualPool vpool, String size, Recommendation recommendation, String label, BlockConsistencyGroup consistencyGroup,
			            String token, boolean remote, URI protectionStorageSystem,
			            Volume.PersonalityTypes personality, String rsetName, String internalSiteName, String rpCopyName, URI srcVolumeId, 
			            VpoolProtectionVarraySettings protectionSettings) {
        StoragePool pool = null;
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));

        if (personality.equals(Volume.PersonalityTypes.METADATA)) {
        	URI vpoolUri = (protectionSettings != null ? protectionSettings.getJournalVpool() : URI.create(vpool.getJournalVpool()));
        	vpool = _dbClient.queryObject(VirtualPool.class, vpoolUri);
        }
        volume.setLabel(label);
        volume.setCapacity(SizeUtil.translateSize(size));
        volume.setThinlyProvisioned(VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(vpool.getSupportedProvisioningType()));
        volume.setVirtualPool(vpool.getId());
        volume.setProject(new NamedURI(project.getId(), volume.getLabel()));
        volume.setTenant(new NamedURI(project.getTenantOrg().getURI(), volume.getLabel()));
        volume.setVirtualArray(varray.getId());
        if (null != recommendation.getSourcePool()) {
            pool = _dbClient.queryObject(StoragePool.class, recommendation.getSourcePool());
            if (null != pool) {
                volume.setProtocol(new StringSet());
                volume.getProtocol().addAll(
                        VirtualPoolUtil.getMatchingProtocols(vpool.getProtocols(), pool.getProtocols()));
            }
        }
        volume.setPersonality(personality.toString());
        
        // Set all Journal Volumes to have the INTERNAL_OBJECT flag.
        if (personality.equals(Volume.PersonalityTypes.METADATA)) {
        	volume.addInternalFlags(Flag.INTERNAL_OBJECT);
        	volume.addInternalFlags(Flag.SUPPORTS_FORCE);
            volume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
        } else if (personality.equals(Volume.PersonalityTypes.SOURCE)) {
            volume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
            volume.setLinkStatus(Volume.LinkStatus.OTHER.name());
        } else if (personality.equals(Volume.PersonalityTypes.TARGET)) {
            volume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
            volume.setLinkStatus(Volume.LinkStatus.OTHER.name());
        }
        
        volume.setProtectionController(protectionStorageSystem);
        volume.setRSetName(rsetName);
        volume.setInternalSiteName(internalSiteName);
        volume.setRpCopyName(rpCopyName);
        URI storagePoolUri = null;
        URI virtualArrayUri = varray.getId();
                            
        if (NullColumnValueGetter.isNotNullValue(personality.toString())) {
	        if (personality.equals(Volume.PersonalityTypes.SOURCE)) {
		        storagePoolUri = recommendation.getSourcePool();
	    	} else {    		
		        if (remote) {        
		        	Protection protectionInfo = getProtectionInfo(varray, recommendation);	  
		    		if (personality.equals(Volume.PersonalityTypes.METADATA)) {
		    			//remote copy journal
		    			storagePoolUri = protectionInfo.getTargetJournalStoragePool() ;    
		        		virtualArrayUri = protectionInfo.getTargetJournalVarray();    
		    		} else {
		    			//remote copy
		    			storagePoolUri = protectionInfo.getTargetStoragePool();     			
		    		}        		        	       
		        } else { 
		        	if (personality.equals(Volume.PersonalityTypes.METADATA)) {
		        		// protection settings is null only for production copy journal
		        		if (protectionSettings != null) {
		        			// local copy journal
		        			Protection protectionInfo = getProtectionInfo(varray, recommendation);	          
		        			storagePoolUri = protectionInfo.getTargetJournalStoragePool() ;    
		            		virtualArrayUri = protectionInfo.getTargetJournalVarray(); 
		        		} else {
		        			//production copy journal
		        			storagePoolUri = ((RPProtectionRecommendation)recommendation).getSourceJournalStoragePool();
		        			virtualArrayUri = ((RPProtectionRecommendation)recommendation).getSourceJournalVarray();
		        		}
		        	} else if (personality.equals(Volume.PersonalityTypes.TARGET)) {
		        		// local copy
		        		Protection protectionInfo = getProtectionInfo(varray, recommendation);	      
		        		storagePoolUri = protectionInfo.getTargetStoragePool();
		        	} 
		        }
			}
        }
   
        volume.setVirtualArray(virtualArrayUri);        
        volume.setPool(storagePoolUri);
        volume.setStorageController(_dbClient.queryObject(StoragePool.class, storagePoolUri).getStorageDevice());
        
        volume.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
        op.setStartTime(Calendar.getInstance());
        volume.getOpStatus().put(token, op);

        _dbClient.createObject(volume);

        // Keep track of target volumes associated with the source volume
        if (srcVolumeId != null) {
        	Volume srcVolume = _dbClient.queryObject(Volume.class, srcVolumeId);
        	if (srcVolume.getRpTargets() == null) {
        		srcVolume.setRpTargets(new StringSet());
        	}
        	srcVolume.getRpTargets().add(volume.getId().toString());
        	_dbClient.persistObject(srcVolume);
        }
               
        return volume;
    }

    /**
     * Determines if all the passed in volume sizes match
     * 
     * @param currentVolumeSizes all volume sizes
     * @return true if all sizes match, false otherwise
     */
    private boolean allVolumeSizesMatch(List<Long> currentVolumeSizes) {        
        // If the storage systems match then check all current sizes of the volumes too, any mismatch 
        // will lead to a calculation check
        List<Long> currentVolumeSizesCopy = new ArrayList<Long>();
        currentVolumeSizesCopy.addAll(currentVolumeSizes);
        for (Long currentSize : currentVolumeSizes) {                
            for (Long compareSize : currentVolumeSizesCopy) {
                if (currentSize.longValue() != compareSize.longValue()) {
                    return false;
                }
            }
        }
                
        _log.info("All volumes are of the same size. No need for capacity calculations.");
        return true;
    }
    
    /**
     * Given a recommendation object, return the protection info based on the site that is protecting to the given varray.
     * The protection info for that varray copy site could be a VPLEX or non-VPLEX protection info. 
     * @param varray
     * @param recommendation
     * @return
     */
    private Protection getProtectionInfo(VirtualArray varray, Recommendation recommendation) {
    	//Find the protection info for this varray, first check if the target is non-vplex by checking the varray protection map, then vplex protection map
    	Protection protectionInfo =  ((RPProtectionRecommendation)recommendation).getVirtualArrayProtectionMap().get(varray.getId());
    	if (protectionInfo == null) {
    		protectionInfo = ((VPlexProtectionRecommendation) recommendation).getVarrayVPlexProtection().get(varray.getId());
    	}
    	return protectionInfo;
    }
        
    /**
     * Prepare Volume for a RecoverPoint protected volume
     *
     * @param param volume request
     * @param project project requested
     * @param journalVarray varray requested
     * @param journalVpool vpool requested
     * @param size size of the volume
     * @param recommendation recommendation for placement
     * @param label volume label
     * @param consistencyGroup consistency group
     * @param token task id
     * @param remote is this a target volume
     * @param protectionStorageSystem protection system protecting this volume
     * @param personality normal volume or metadata
     * @param rsetName replication set name
     * @param internalSiteName replication site ID
     * @param rpCopyName copy name on RP CG
     * @param srcVolumeId source volume ID; only for target volumes
     * @return a persisted volume
     */
     public Volume prepareJournalVolume(Project project, VirtualArray journalVarray, VirtualPool journalVpool, String size, 
		     							Recommendation recommendation, URI storagePoolUri, String label, BlockConsistencyGroup consistencyGroup,
		 					            String token, boolean remote, URI protectionStorageSystem,
		 					            Volume.PersonalityTypes personality, String rsetName, String internalSiteName, String rpCopyName, URI srcVolumeId) {                     
         Volume volume = new Volume();    
         volume.setId(URIUtil.createId(Volume.class));
         volume.setLabel(label);
         volume.setCapacity(SizeUtil.translateSize(size));
         volume.setThinlyProvisioned(VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(journalVpool.getSupportedProvisioningType()));                                  
         volume.setVirtualPool(journalVpool.getId());
         volume.setProject(new NamedURI(project.getId(), volume.getLabel()));
         volume.setTenant(new NamedURI(project.getTenantOrg().getURI(), volume.getLabel()));
         
         if (null != recommendation.getSourcePool()) {
             StoragePool pool = _dbClient.queryObject(StoragePool.class, recommendation.getSourcePool());
             if (null != pool) {
                 volume.setProtocol(new StringSet());
                 volume.getProtocol().addAll(
                         VirtualPoolUtil.getMatchingProtocols(journalVpool.getProtocols(), pool.getProtocols()));
             }
         }
         
         volume.setPersonality(personality.toString());
         
         // Set all Journal Volumes to have the INTERNAL_OBJECT flag.
         if (personality.equals(Volume.PersonalityTypes.METADATA)) {
         	volume.addInternalFlags(Flag.INTERNAL_OBJECT);
         	volume.addInternalFlags(Flag.SUPPORTS_FORCE);
             volume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
         } else if (personality.equals(Volume.PersonalityTypes.SOURCE)) {
             volume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
             volume.setLinkStatus(Volume.LinkStatus.OTHER.name());
         } else if (personality.equals(Volume.PersonalityTypes.TARGET)) {
             volume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
             volume.setLinkStatus(Volume.LinkStatus.OTHER.name());
         }
         
         volume.setProtectionController(protectionStorageSystem);
         volume.setRSetName(rsetName);
         volume.setInternalSiteName(internalSiteName);
         volume.setRpCopyName(rpCopyName);         
         volume.setVirtualArray(journalVarray.getId());
         volume.setPool(storagePoolUri);
         volume.setStorageController(_dbClient.queryObject(StoragePool.class, storagePoolUri).getStorageDevice());
         
         if (null != journalVpool.getAutoTierPolicyName()) {
             URI autoTierPolicyUri = StorageScheduler.getAutoTierPolicy(volume.getPool(),
                    journalVpool.getAutoTierPolicyName(), _dbClient);
             if (null != autoTierPolicyUri) {
                 volume.setAutoTieringPolicyUri(autoTierPolicyUri);
             }
         }
         
         volume.setOpStatus(new OpStatusMap());
         Operation op = new Operation();
         op.setResourceType(ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
         op.setStartTime(Calendar.getInstance());
         volume.getOpStatus().put(token, op);

         _dbClient.createObject(volume);
            
         return volume;
     }

    /**
     * Populate and prepare all target volumes (targets and journals) associated with protection of a volume.
     *
     * @param param Volume creation request
     * @param project project requested
     * @param vpool class of service requested
     * @param recommendation recommendation placement object
     * @param volumeLabelBuilder label building to create volume labels
     * @param protectionVirtualArray protection varray we're playing with
     * @param consistencyGroup cg id
     * @param settings settings
     * @param task task id
     * @return list of volume IDs
     */
    private List<URI> prepareTargetVolumes(VolumeCreate param, Project project, VirtualPool vpool,
            RPProtectionRecommendation recommendation, StringBuilder volumeLabelBuilder, String rsetName,
            VirtualArray protectionVirtualArray, BlockConsistencyGroup consistencyGroup, VpoolProtectionVarraySettings settings,
            Volume srcVolume, boolean createTargetJournal, String task, Integer resourceCount, TaskList taskList) {
        Volume volume;
        Volume journalVolume;
        List<URI> volumeURIs = new ArrayList<URI>();
      
        // By default, the target VirtualPool is the source VirtualPool
        VirtualPool targetVpool = vpool;
        // If there's a VirtualPool in the protection settings that is different, use it instead.
        if (settings.getVirtualPool() != null) {
            targetVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
        }

        Protection pRec = recommendation.getVirtualArrayProtectionMap().get(protectionVirtualArray.getId());

        // Target volume in a varray
        String targetVolumeName =  new StringBuilder(volumeLabelBuilder.toString()).append(VOLUME_TYPE_TARGET + protectionVirtualArray.getLabel()).toString();
        volume = prepareVolume(project, protectionVirtualArray, targetVpool, param.getSize(), recommendation, targetVolumeName, 
        		consistencyGroup, task, true, recommendation.getProtectionDevice(), Volume.PersonalityTypes.TARGET,
                rsetName, pRec.getTargetInternalSiteName(), protectionVirtualArray.getLabel(), srcVolume.getId(), settings);
        		volumeURIs.add(volume.getId());
        
        if (recommendation.getVpoolChangeVolume() != null) {
        	 taskList.getTaskList().add(toTask(volume, task));
        }

        if (createTargetJournal) {
        	String journalVolumeName = new StringBuilder(volumeLabelBuilder.toString()).append(VOLUME_TYPE_TARGET_JOURNAL + protectionVirtualArray.getLabel()).toString();
 		    URI targetJournalStoragePoolUri = recommendation.getVirtualArrayProtectionMap().get(protectionVirtualArray.getId()).getTargetJournalStoragePool();
 		    
 		    VirtualArray targetJournalVarray = protectionVirtualArray ;
 		    if (settings.getJournalVarray() != null) {
 		    	targetJournalVarray = _dbClient.queryObject(VirtualArray.class, settings.getJournalVarray());
 		    }
 		    VirtualPool targetJournalVpool = vpool;
 		    if (settings.getJournalVpool() != null) {
 		       targetJournalVpool = _dbClient.queryObject(VirtualPool.class, settings.getJournalVpool());
 		    } else if (settings.getVirtualPool() != null){
                targetJournalVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
 		    }
		    String size = String.valueOf(RPHelper.getJournalSizeGivenPolicy(param.getSize(), settings != null ? settings.getJournalSize() : null, resourceCount));
		    journalVolume = prepareJournalVolume(project, targetJournalVarray, targetJournalVpool, size, recommendation, targetJournalStoragePoolUri,
		            journalVolumeName, consistencyGroup, task, true, recommendation.getProtectionDevice(), Volume.PersonalityTypes.METADATA,
		            null, pRec.getTargetInternalSiteName(), protectionVirtualArray.getLabel(), null);
		    volumeURIs.add(journalVolume.getId()); 
		    if (recommendation.getVpoolChangeVolume() != null) {
	        	 taskList.getTaskList().add(toTask(journalVolume, task));
	        }
        }
        
        return volumeURIs;
    }
    
    @Override
    public TaskList createVolumes(VolumeCreate param, Project project, VirtualArray varray,
            VirtualPool vpool, List<Recommendation> recommendations, String task,
            VirtualPoolCapabilityValuesWrapper capabilities) throws InternalException {

        // Prepare the Bourne Volumes to be created and associated
        // with the actual storage system volumes created. Also create
        // a BlockTaskList containing the list of task resources to be
        // returned for the purpose of monitoring the volume creation
        // operation for each volume to be created.
        String volumeLabel = param.getName();
        TaskList taskList = new TaskList();
        Iterator<Recommendation> recommendationsIter;
                
        final BlockConsistencyGroup consistencyGroup = capabilities.getBlockConsistencyGroup() == null ? null : _dbClient
				.queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
        
        // Store capabilities of the CG, so they make it down to the controller
        if (vpool.getRpCopyMode() != null) {
        	capabilities.put(VirtualPoolCapabilityValuesWrapper.RP_COPY_MODE, vpool.getRpCopyMode());
        }
        if (vpool.getRpRpoType() != null &&
                NullColumnValueGetter.isNotNullValue(vpool.getRpRpoType())) {
        	capabilities.put(VirtualPoolCapabilityValuesWrapper.RP_RPO_TYPE, vpool.getRpRpoType());
        }
        if (vpool.checkRpRpoValueSet()) {
        	capabilities.put(VirtualPoolCapabilityValuesWrapper.RP_RPO_VALUE, vpool.getRpRpoValue());
        }

        // prepare the volumes
        List<URI> volumeURIs = prepareRecommendedVolumes(param, task, taskList, project,
            varray, vpool, capabilities.getResourceCount(), recommendations,
        		consistencyGroup, volumeLabel);
        
        // Execute the volume creations requests for each recommendation.
        recommendationsIter = recommendations.iterator();
        while (recommendationsIter.hasNext()) {
        	Recommendation recommendation = recommendationsIter.next();
        	try {
        		List<VolumeDescriptor> volumeDescriptors = createVolumeDescriptors((RPProtectionRecommendation)recommendation, volumeURIs, capabilities);
                BlockOrchestrationController controller = getController(BlockOrchestrationController.class,
                        BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);
        		
                // Check to see if this is a regular volume create or change virtual pool(add RP protection)
                URI changeVirtualPoolVolumeURI = VolumeDescriptor.getVirtualPoolChangeVolume(volumeDescriptors);
                boolean isChangeVpool = (changeVirtualPoolVolumeURI != null);                
             
                // TODO might be able to use param.getSize() instead of the below code to find requestedVolumeCapactity
                Long requestedVolumeCapactity = 0L;
                for (URI volumeURI : volumeURIs) {
                    Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                    if (Volume.PersonalityTypes.SOURCE.name().equalsIgnoreCase(volume.getPersonality())) {
                        requestedVolumeCapactity = volume.getCapacity();
                        break;
                    }
                }
                
                computeProtectionCapacity(volumeURIs, requestedVolumeCapactity, false, isChangeVpool, null);
                
                if (isChangeVpool) {                    
                    _log.info("Add Recoverpoint Protection to existing volume");                                       
                    controller.changeVirtualPool(volumeDescriptors, task);
                }
                else {
                    _log.info("Create RP volumes");
                    controller.createVolumes(volumeDescriptors, task);
                }
        		
            } catch (InternalException e) {
                if (_log.isErrorEnabled()) {
            			_log.error("Controller error", e);
            		}

            		if (volumeURIs != null) {
            			for (URI volumeURI : volumeURIs) {
            				Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            				if (volume!=null) {
            			        Operation op = new Operation();
            			        op.setResourceType(ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
            			        _dbClient.createTaskOpStatus(Volume.class, volume.getId(), task, op);
            			        _dbClient.error(Volume.class, volume.getId(), task, e);
            					if (volume.getPersonality() != null && volume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())) {
            						taskList.getTaskList().add(toTask(volume, task, op));
            					}
            				}
            			}
                    throw e;
            		}

            		// If there was a controller error creating the volumes,
                    // throw an internal server error and include the task
                    // information in the response body, which will inform
                    // the user what succeeded and what failed.
                    throw new WebApplicationException(Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR).entity(taskList).build());
            	}
        }

        return taskList;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void deleteVolumes(final URI systemURI, final List<URI> volumeURIs,
    		final String deletionType, final String task) throws InternalException {
    	_log.info("Request to delete {} volume(s) with RP Protection", volumeURIs.size());
    	try {
    	    super.deleteVolumes(systemURI, volumeURIs, deletionType, task);
    	} catch (Exception e) {
    	    throw RecoverPointException.exceptions.deletingRPVolume(e);
    	}
    }

	/**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public <T extends DataObject> String checkForDelete(T object) throws InternalException {
        // The standard dependency checker really doesn't fly with RP because we need to determine if we can do
        // a tear-down of the volume, and that tear-down involved cleaning up dependent relationships.
        // So this will be a bit more manual and calculated.  In order to determine if we can delete this object,
        // we need to make sure:
        // 1. This device is a SOURCE device if the protection set is happy and healthy (this is done before we get here)
        // 2. This device and all of the other devices don't have any block snapshots
        // 3. If the device isn't part of a healthy protection set, then do a dependency check

        // Generate a list of dependencies, if there are any.
        Map<URI, URI> dependencies = new HashMap<URI, URI>();

        Volume sourceVolume = (Volume)object;
        // Get all of the volumes associated with the volume
        for (BlockSnapshot snapshot : this.getSnapshots(sourceVolume)) {
            if (snapshot != null && !snapshot.getInactive()) {
                dependencies.put(sourceVolume.getId(), snapshot.getId());
            }
        }
        
		if (!dependencies.isEmpty()) {
			throw APIException.badRequests.cannotDeleteVolumeBlockSnapShotExists(String.valueOf(dependencies));
		}
		
		List<URI> volumeIDs = _rpHelper.getReplicationSetVolumes((Volume) object);

        // Do a relatively "normal" check, as long as it's a "broken"
        // protection set.
        // It's considered a broken protection set if there's only one
        // volume returned from the getRPVolumes() call,
        // Because that can only have happened if the CG was already torn
        // down.
        if (volumeIDs.size() == 1) {
            String depMsg = _dependencyChecker.checkDependencies(object.getId(),
                    object.getClass(), true);
            if (depMsg != null) {
                return depMsg;
            }
            return object.canBeDeleted();
        }
        
        return null;
    }

    @Override
    public TaskResourceRep deactivateMirror(StorageSystem device, URI mirrorURI, String task) {
        // FIXME Should use relevant ServiceCodeException here
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageSystemConnectivityList getStorageSystemConnectivity(StorageSystem system) {
    	// Connectivity list to return
        StorageSystemConnectivityList connectivityList = new StorageSystemConnectivityList();
        // Set used to ensure unique values are added to the connectivity list
        Set<String> existing = new HashSet<String>();
        
        // Get all the RPSiteArrays that contain this Storage System
        List<RPSiteArray> rpSiteArrays = CustomQueryUtility
				.queryActiveResourcesByConstraint(_dbClient, RPSiteArray.class, 
						AlternateIdConstraint.Factory.getConstraint(RPSiteArray.class, 
																		"storageSystem", 
																		system.getId().toString()));
        
        // For each of the RPSiteArrays get the Protection System
        for (RPSiteArray rpSiteArray : rpSiteArrays) {
        	if ((rpSiteArray.getRpProtectionSystem() != null)) {
        		 ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, rpSiteArray.getRpProtectionSystem());
        		 // Loop through the associated Storage Systems for this Protection System to build the 
        		 // connectivity response list. Only store unique responses.
        		 for (String associatedStorageSystemStr : protectionSystem.getAssociatedStorageSystems()) {
        			 URI associatedStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystemStr), _dbClient, StorageSystemType.BLOCK);        			 
        			 StorageSystem associatedStorageSystem = _dbClient.queryObject(StorageSystem.class, associatedStorageSystemURI);
        			 
        			 if (associatedStorageSystem == null || associatedStorageSystem.getInactive()
                     		|| ConnectivityUtil.isAVPlex(associatedStorageSystem)) {
                     	continue;
                     }
        			 
        			 StorageSystemConnectivityRestRep connection = new StorageSystemConnectivityRestRep();
        			 connection.getConnectionTypes().add(ProtectionSystem._RP);
        			 connection.setProtectionSystem(toNamedRelatedResource(ResourceTypeEnum.PROTECTION_SYSTEM, 
        					 												rpSiteArray.getRpProtectionSystem(), 
        					 												protectionSystem.getLabel()));
        			 connection.setStorageSystem(toNamedRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, 
        					 												associatedStorageSystem.getId(), 
        					 												associatedStorageSystem.getSerialNumber()));

	                 // The key is a transient unique ID, since none of the actual fields guarantee uniqueness.
	                 // We use this to make sure we don't add the same storage system more than once for the same
	                 // protection system and connection type.
	                 String key = connection.getProtectionSystem().toString()+connection.getConnectionTypes()+connection.getStorageSystem().toString();
	                 if (!existing.contains(key)) {
	                     existing.add(key);
	                     connectivityList.getConnections().add(connection);
	                 }
        		 }
        	}
        }
        
        return connectivityList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
        VirtualArray newVarray) throws APIException {
        throw APIException.badRequests.changesNotSupportedFor("VirtualArray",
            "RP protected volumes");
    }    

    /**
 
    /**
     * Upgrade a local block volume to a protected RP volume
     * 
     * @param volume the existing volume being protected.
     * @param newVpool the requested virtual pool
     * @param taskId the task identifier
     * @throws InternalException
     */
    private void upgradeToProtectedVolume(Volume volume, VirtualPool newVpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        
        VirtualPool targetVpool = _dbClient.queryObject(VirtualPool.class, vpoolChangeParam.getVirtualPool());
        Project project = _dbClient.queryObject(Project.class, volume.getProject());
        
        // The volume can not already be in a CG for RP. This may change in the future, but
        // for now we can not allow it. Inform the user that they will first need to remove
        // the volume from the existing CG before they can proceed.
        if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {                
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());                
            throw APIException.badRequests.cannotCreateRPVolumesInCG(volume.getLabel(), cg.getLabel());
        }
        
        if (VirtualPool.vPoolSpecifiesProtection(targetVpool)) {
            // CTRL-2792: This is a workaround for the UI not providing a field for 
            // consistency group.  We are basing the CG name off the volume
            // name until this support is added.  In the future, the cg should
            // never be null.
            if (vpoolChangeParam.getConsistencyGroup() == null) {
                // create a consistency group corresponding to volume name
                BlockConsistencyGroup cg = new BlockConsistencyGroup();
                String modifiedCGName = volume.getLabel().replaceAll("\\s+", "").replaceAll("[^A-Za-z0-9]", "");
                
                cg.setProject(new NamedURI(project.getId(), modifiedCGName));
                cg.setTenant(new NamedURI(project.getTenantOrg().getURI(), modifiedCGName));
                cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
                cg.setLabel(modifiedCGName);

                _dbClient.createObject(cg);
                
                vpoolChangeParam.setConsistencyGroup(cg.getId());
            }
        }
        
        List<Recommendation> recommendations = 
        		getRecommendationsForVirtualPoolChangeRequest(volume, newVpool, vpoolChangeParam);

        if(recommendations.isEmpty()){
            throw APIException.badRequests.noStorageFoundForVolume();
        }

        // Call out to the respective block service implementation to prepare and create the 
        // volumes based on the recommendations.
        VirtualArray varray = _dbClient.queryObject(
        		VirtualArray.class, volume.getVirtualArray());

        // Generate a VolumeCreate object that contains the information that createVolumes likes to consume.
        VolumeCreate param = new VolumeCreate(
        		volume.getLabel(), String.valueOf(volume.getCapacity()), 1, newVpool.getId(),
        		volume.getVirtualArray(), volume.getProject().getURI());

        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, vpoolChangeParam.getConsistencyGroup());
        createVolumes(param, project, varray, newVpool, recommendations, taskId, capabilities);
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void changeVolumeVirtualPool(URI systemURI, Volume volume, VirtualPool newVpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        _log.debug("Volume {} VirtualPool change.", volume.getId());
        
        ArrayList<Volume> volumes = new ArrayList<Volume>();
        volumes.add(volume);
        
        // Check for common Vpool updates handled by generic code. It returns true if handled.
        if (checkCommonVpoolUpdates(volumes, newVpool, taskId)) {
            return;
        }
        
        // Let's set the new vpool on the volume.
        // The volume will not be persisted just yet but we need to have the new vpool to
        // properly make placement decisions and to add reference to the new vpool to the
        // recommendation objects that will be created.
        volume.setVirtualPool(newVpool.getId());

        // Get the storage system. This vmax, or vnxblock storage system.
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
        String systemType = storageSystem.getSystemType();
        if ((DiscoveredDataObject.Type.vmax.name().equals(systemType)) ||
            (DiscoveredDataObject.Type.vnxblock.name().equals(systemType))) {
            _log.debug("Protection VirtualPool change for vmax or vnx volume.");
            upgradeToProtectedVolume(volume,  newVpool, vpoolChangeParam, taskId);
        }
    }

    @Override
    public void changeVolumeVirtualPool(List<Volume> volumes, VirtualPool vpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        // For now we only support changing the virtual pool for a single volume at a time 
        // until CTRL-1347 and CTRL-5609 are fixed.
        if (volumes.size() == 1) {
            changeVolumeVirtualPool(volumes.get(0).getStorageController(), volumes.get(0), vpool, vpoolChangeParam, taskId);
        }
        else {
            throw APIException.methodNotAllowed.notSupportedWithReason("Multiple volume change virtual pool is currently not supported for RecoverPoint. Please select one volume at a time.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVolumeExpansionRequest(Volume volume, long newSize) {
        _log.debug("Verify if RP volume {} can be expanded", volume.getId());

        // Look at all source and target volumes and make sure they can all be expanded
        super.verifyVolumeExpansionRequest(volume, newSize);
        if (volume.getRpTargets() != null) {        	        
        	for (String volumeID : volume.getRpTargets()) {
        		try {
        			super.verifyVolumeExpansionRequest(_dbClient.queryObject(Volume.class, new URI(volumeID)), newSize);
        		} catch (URISyntaxException e) {
        			throw APIException.badRequests.invalidURI(volumeID, e);
        		}
        	}
        } else {
        	throw APIException.badRequests.notValidRPSourceVolume(volume.getLabel());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expandVolume(Volume volume, long requestedSize, String taskId)
        throws InternalException {    	
    	List<URI> volumeURIs = new ArrayList<URI>();    	
    	Long originalVolumeSize = volume.getCapacity(); 
    	
    	List<URI> replicationSetVolumes = _rpHelper.getReplicationSetVolumes(volume);
    	
    	//Step1 : Determine if either the RP source or target is on VMAX. If yes, then set the requested volume size to the size as determined by if the VMAX volume is meta volume or not.
    	//In the case of meta volumes, the actual provisioned volume size/capacity will be different than the requested size.  If either the RP source/target is on VMAX/VNX and the target/source is 
    	//on VNX/VMAX, then the size of the VNX volume must match the size of the VMAX volume after expand is done. 
    	//TODO: Move this segment of the code that computes if one of the volumes is VMAX and determines the potential provisioned capacity into 
    	//the computeProtectionAllocation Capacity. When moving make sure to implement based on whether there are VMAX volume backing VPLEX virtual volumes. 
    	
    	for (URI rpVolumeURI : replicationSetVolumes) {    	    		    		
    		Volume rpVolume = _dbClient.queryObject(Volume.class, rpVolumeURI);
    		StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, rpVolume.getStorageController());
    		if (storageSystem.getSystemType().equalsIgnoreCase(DiscoveredDataObject.Type.vmax.toString())) {
    			//Set the requested size to what the VMAX Meta utils determines will possibly be the provisioned capacity. 
    			//All the other volumes, will need to be the same size as well or else RP will have a fit.
    			requestedSize = _rpHelper.computeVmaxVolumeProvisionedCapacity(
						requestedSize, rpVolume, storageSystem);    			  	
    		}     		
    		volumeURIs.add(rpVolumeURI);    		
    	}
    	    	        	    
    	try {    		    	
	    	//Step 2: Just because we have a RP source/target on VMAX and have a size calculated doesnt mean VNX can honor it. 
    		//The trick is that the size of the volume must be a multiple of 512 for VNX and 520 for VMAX because of the different block sizes. 
    		//We will find a number (in bytes) that is greater than the requested size and meets the above criteria and use that our final expanded volume size. 
    		long normalizedRequestSize = computeProtectionCapacity(volumeURIs, requestedSize, true, false, null);     
   
	        //Step 3: Call the controller to do the expand. 
     		_log.info("Expand volume request size : {}", normalizedRequestSize);
     		List<VolumeDescriptor> volumeDescriptors = createVolumeDescriptors(null, volumeURIs, null);
     		
     		for (VolumeDescriptor volDesc : volumeDescriptors) {
				volDesc.setVolumeSize(normalizedRequestSize);
			}     		
     		
    		BlockOrchestrationController controller = getController(BlockOrchestrationController.class,
                    BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);    			      
	        controller.expandVolume(volumeDescriptors, taskId);
    	} catch (InternalException e) {
    		//Set the volume size back to original size before the expand request
    		for (URI volumeURI : replicationSetVolumes) {	    	
        		Volume rpVolume = _dbClient.queryObject(Volume.class, volumeURI);
        		rpVolume.setCapacity(originalVolumeSize);        	
        		_dbClient.persistObject(rpVolume);
    		}    		
			throw APIException.badRequests.volumeNotExpandable(volume.getLabel());
		}
    }

	@Override
	protected Set<URI> getConnectedVarrays(URI varrayUID) {
		
		Set<URI> varrays = new HashSet<URI>();

		List<ProtectionSystem> protectionSystems = CustomQueryUtility
				.queryActiveResourcesByConstraint(_dbClient, ProtectionSystem.class, 
						AlternateIdConstraint.Factory.getConstraint(ProtectionSystem.class, 
																		VIRTUAL_ARRAYS_CONSTRAINT, 
																		varrayUID.toString()));
		
	    // Create and return the result.
		for (ProtectionSystem protectionSystem :  protectionSystems) {
			if (protectionSystem.getVirtualArrays() != null) {
	        	for (String varrayURIStr : protectionSystem.getVirtualArrays()) {
	        		varrays.add(URI.create(varrayURIStr));
	    		}
			}
		}
	     
		return varrays;
	}
	
    /**
     * {@inheritDoc}
     */
    @Override
    public void validateCreateSnapshot(Volume reqVolume, List<Volume> volumesToSnap,
        String snapshotType, String snapshotName, BlockFullCopyManager fcManager) {
        boolean vplex = RPHelper.isVPlexVolume(reqVolume);
        
        // For RP snapshots, validate that the volume type is not a target,
        // metadata, or null - must be source.
        if (snapshotType.equalsIgnoreCase(BlockSnapshot.TechnologyType.RP.toString())
            && (reqVolume.getPersonality() == null || !reqVolume.getPersonality().equals(
                PersonalityTypes.SOURCE.toString()))) {
            String volumeType = reqVolume.getPersonality() != null ? reqVolume
                .getPersonality().toLowerCase() : UNKNOWN_VOL_TYPE;
            throw APIException.badRequests.notSupportedSnapshotVolumeType(volumeType);
        }
        
        if (vplex) {
            // If this is a local array snap of an RP+VPlex source volume, we need to check for mixed
            // backing array storage systems.  We cannot create local array snaps if the source volumes
            // in the CG don't all use the same backing arrays.
            if (snapshotType.equalsIgnoreCase(BlockSnapshot.TechnologyType.NATIVE.toString())
                    && NullColumnValueGetter.isNotNullValue(reqVolume.getPersonality()) && reqVolume.getPersonality().equals(
                        PersonalityTypes.SOURCE.toString())) {
                if (!NullColumnValueGetter.isNullURI(reqVolume.getConsistencyGroup())) {
                    BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, reqVolume.getConsistencyGroup());
                    if (containsMixedBackingArrays(cg)) {
                        throw APIException.badRequests.notSupportedSnapshotWithMixedArrays(cg.getId());
                    }
                }
            }
        }
        
        // If not an RP bookmark snapshot, then do what the base class does, else
        // we just need to verify the volumes and name.
        if (!snapshotType.equalsIgnoreCase(TechnologyType.RP.toString())) {
            List<Volume> baseVolumesToSnap = new ArrayList<Volume>();
            if (vplex) {
                for (Volume vplexVolume : volumesToSnap) {
                    if (vplexVolume.getPersonality().equals(Volume.PersonalityTypes.TARGET.toString()) || vplexVolume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())) {
                        baseVolumesToSnap.add(vplexBlockServiceApiImpl.getVPLEXSnapshotSourceVolume(vplexVolume));
                    }
                }
            }
            List<Volume> volumes = (vplex ? baseVolumesToSnap : volumesToSnap);
            super.validateCreateSnapshot(reqVolume, volumes, snapshotType, snapshotName, fcManager);
        } else {
            ArgValidator.checkFieldNotEmpty(volumesToSnap, "volumes");
            ArgValidator.checkFieldNotEmpty(snapshotName, "name");
            
            // Check if there are any snapshots of the same name for the volume(s)
            for (Volume volumeToSnap : volumesToSnap) {
                checkForDuplicatSnapshotName(snapshotName, volumeToSnap);
            }
        }
    }
    
    /**
     * Determines if the CG source volumes contain mixed backing arrays.
     * 
     * @param cg the consistency group to search.
     * @return true if the CG contains source volumes with mixed backing arrays, false otherwise.
     */
    private boolean containsMixedBackingArrays(BlockConsistencyGroup cg) {
        // Get the active RP SOURCE VPLEX volumes for the consistency group.
        List<Volume> vplexVolumes = BlockConsistencyGroupUtils.getActiveVplexVolumesInCG(
                cg, _dbClient, PersonalityTypes.SOURCE);

        if (vplexVolumes.size() > 1) {
            // Get the first volume's source side backing array.  Compare the backing arrays
            // from other source volume's in the CG to see if they are different.
            URI storageSystemToCompare = getSourceBackingVolumeStorageSystem(vplexVolumes.get(0));
            
            for (Volume volume : vplexVolumes) {
                URI storageSystem = getSourceBackingVolumeStorageSystem(volume);
                if (!storageSystem.equals(storageSystemToCompare)) {
                    // The backing storage systems for the source side are different between
                    // volumes in the CG.  
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Gets the non-HA side backing volume storage system for the given VPlex volume.
     * 
     * @param vplexVolume the VPlex volume. 
     * @return the storage system URI corresonding to the backing volume.
     */
    private URI getSourceBackingVolumeStorageSystem(Volume vplexVolume) {
        // Get the backing volume associated with the source side only
        Volume localVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume,
                true, _dbClient);
        
        return localVolume.getStorageController();
    }
    
    /**
     * Prepares the snapshots for a snapshot request.
     * 
     * @param volumes The volumes for which snapshots are to be created.
     * @param snapShotType The snapshot technology type.
     * @param snapshotName The snapshot name.
     * @param snapshotURIs [OUT] The URIs for the prepared snapshots.
     * @param taskId The unique task identifier
     * 
     * @return The list of snapshots
     */
    @Override
    public List<BlockSnapshot> prepareSnapshots(List<Volume> volumes, String snapshotType,
        String snapshotName, List<URI> snapshotURIs, String taskId) {
        
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        for (Volume volume : volumes) {           
            if (isProtectionBasedSnapshot(volume, snapshotType)
                     && snapshotType.equalsIgnoreCase(BlockSnapshot.TechnologyType.RP.toString())) {              
                // For protection-based snapshots, get the protection domains we
                // need to create snapshots on
                for (String targetVolumeStr : volume.getRpTargets()) {
                    Volume targetVolume = _dbClient.queryObject(Volume.class, URI.create(targetVolumeStr));  
                    BlockSnapshot snapshot = prepareSnapshotFromVolume(volume, snapshotName, targetVolume);                             
                    snapshot.setOpStatus(new OpStatusMap());
                    snapshot.setEmName(snapshotName);
                    snapshot.setEmInternalSiteName(targetVolume.getInternalSiteName()); 
                    snapshot.setVirtualArray(targetVolume.getVirtualArray());       
                    snapshots.add(snapshot);
                    
                    _log.info(String.format("Prepared snapshot : [%s]", snapshot.getLabel()));
                }                            
            } 
            else {
                boolean vplex = RPHelper.isVPlexVolume(volume);
                Volume volumeToSnap = volume;                
                if (vplex) {
                    volumeToSnap = vplexBlockServiceApiImpl.getVPLEXSnapshotSourceVolume(volume);
                }
                                
                boolean isRPTarget = false;
                if (NullColumnValueGetter.isNotNullValue(volume.getPersonality()) &&
                        volume.getPersonality().equals(PersonalityTypes.TARGET.name())) {
                    isRPTarget = true;
                }
                
                BlockSnapshot snapshot = prepareSnapshotFromVolume(volumeToSnap, snapshotName, (isRPTarget ? volume : null));
                snapshot.setTechnologyType(snapshotType);
                
                // Check to see if the requested volume is a former target that is now the
                // source as a result of a swap. This is done by checking the source volume's
                // virtual pool for RP protection.  If RP protection does not exist, we know this
                // is a former target.  
                // TODO: In the future the swap functionality should update the vpools accordingly to 
                // add/remove protection.  This check should be removed at that point and another
                // method to check for a swapped state should be used.
                boolean isFormerTarget = false;
                VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                if (NullColumnValueGetter.isNotNullValue(volume.getPersonality()) &&
                        volume.getPersonality().equals(PersonalityTypes.SOURCE.name()) &&
                        !VirtualPool.vPoolSpecifiesProtection(vpool)) {
                    isFormerTarget = true;
                }
                
                if (((isRPTarget || isFormerTarget) && vplex) || !vplex) {
                    // For RP+Vplex target and former target volumes, we do not want to create a 
                    // backing array CG snap. To avoid doing this, we do not set the consistency group.
                    // OR
                    // This is a native snapshot so do not set the consistency group, otherwise
                    // the SMIS code/array will get confused trying to look for a consistency
                    // group that only exists in RecoverPoint.                    
                    snapshot.setConsistencyGroup(null);
                }
                
                snapshots.add(snapshot);
                
                _log.info(String.format("Prepared snapshot : [%s]", snapshot.getLabel()));
            }
        }
                        
        for (BlockSnapshot snapshot: snapshots) {               
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.CREATE_VOLUME_SNAPSHOT);
            op.setStartTime(Calendar.getInstance());                
            snapshot.getOpStatus().createTaskStatus(taskId, op);     
            snapshotURIs.add(snapshot.getId());                             
        }         
        
        // Create all the snapshot objects
        _dbClient.createObject(snapshots);
        
        // But only return the unique ones
        return snapshots;
    }
    
    /**
     * Creates and returns a new ViPR BlockSnapshot instance with the passed
     * name for the passed volume.
     * 
     * @param volume The volume for which the snapshot is being created.
     * @param snapshotName The name to be given the snapshot
     * 
     * @return A reference to the new BlockSnapshot instance.
     */
    protected BlockSnapshot prepareSnapshotFromVolume(Volume volume, String snapshotName, Volume targetVolume) {
        BlockSnapshot snapshot = new BlockSnapshot();
        snapshot.setId(URIUtil.createId(BlockSnapshot.class));   
        URI cgUri = null;
        
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeByAssociatedVolumesConstraint(volume.getId().toString()),
                queryResults);
        if (queryResults.iterator().hasNext()) {
            Volume sourceVplexVolume = _dbClient.queryObject(Volume.class, queryResults.iterator().next());
            cgUri = sourceVplexVolume.getConsistencyGroup();
             snapshot.setProject(new NamedURI(sourceVplexVolume.getProject().getURI(), snapshotName));
        } else {
             cgUri = volume.getConsistencyGroup();
             snapshot.setProject(new NamedURI(volume.getProject().getURI(), snapshotName));
        }
                      
        if (cgUri != null) {
            snapshot.setConsistencyGroup(cgUri);
        }         
        
        snapshot.setSourceNativeId(volume.getNativeId());
        snapshot.setParent(new NamedURI(volume.getId(), snapshotName));
        String modifiedSnapshotName = snapshotName;
        
        // We want snaps of targets to contain the varray label so we can distinguish multiple
        // targets from one another
        if (targetVolume != null) {
            VirtualArray targetVarray = _dbClient.queryObject(VirtualArray.class, targetVolume.getVirtualArray());
            modifiedSnapshotName = modifiedSnapshotName + "-" + targetVarray.getLabel();
        }        
        snapshot.setLabel(modifiedSnapshotName);
        
        snapshot.setStorageController(volume.getStorageController());
        snapshot.setVirtualArray(volume.getVirtualArray());
        snapshot.setProtocol(new StringSet());
        snapshot.getProtocol().addAll(volume.getProtocol());            
        snapshot.setSnapsetLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(
            snapshotName, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));
        
        return snapshot;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<Volume> getVolumesToSnap(Volume reqVolume, String snapshotType) {       
        List<Volume> volumesToSnap = new ArrayList<Volume>();
        boolean vplex = RPHelper.isVPlexVolume(reqVolume);
                
        // If the requested volume is a regular RP protected volume (either RP source or RP target), 
        // then just pass in that volume. RP protected (non-VPlex) volumes are not in any 
        // underlying array CG.
        // OR
        // Only for RP+VPLEX just return the requested volume for RP bookmark (snapshot) requests.  We only
        // want a single BlockSnapshot created.
        if (!vplex || (snapshotType != null && snapshotType.equalsIgnoreCase(BlockSnapshot.TechnologyType.RP.toString()))) {
            volumesToSnap.add(reqVolume);
            return volumesToSnap;
        }
        
        // If the passed volume is in a consistency group all volumes
        // of the same type (source or target) in the consistency group 
        // should be snapped.
        URI cgURI = reqVolume.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgURI)) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            // If there is no corresponding native CG for the VPLEX
            // CG, then this is a CG created prior to 2.2 and in this
            // case we maintain pre-2.2 behavior by only snapping the
            // requested volume.
            if (!cg.checkForType(Types.LOCAL)) {
                volumesToSnap.add(reqVolume);
            } else {
                if (NullColumnValueGetter.isNotNullValue(reqVolume.getPersonality())) {
                    if (reqVolume.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
                        List<Volume> cgVolumes = getActiveCGVolumes(cg);
                        for (Volume cgVolume : cgVolumes) {
                            // We want to snap all source volumes in the CG so add each one as we
                            // find them.
                            if (NullColumnValueGetter.isNotNullValue(cgVolume.getPersonality()) && 
                                    cgVolume.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
                                volumesToSnap.add(cgVolume);
                            }
                        }
                    } else if (reqVolume.getPersonality().equals(PersonalityTypes.TARGET.name())) {
                        // In the case of target volumes, we do not want to snap all target
                        // volumes in the CG. We only want to create one for the requested
                        // volume.
                        volumesToSnap.add(reqVolume);
                    }
                }
            }
        } else {
            volumesToSnap.add(reqVolume);
        }
        
        return volumesToSnap;
    }
    
    /**
     * Uses the appropriate controller to create the snapshots.
     * 
     * @param reqVolume The volume from the snapshot request.
     * @param snapshotURIs The URIs of the prepared snapshots.
     * @param snapshotType The snapshot technology type.
     * @param createInactive true if the snapshots should be created but not
     *        activated, false otherwise.
     * @param taskId The unique task identifier.
     */
    @Override
    public void createSnapshot(Volume reqVolume, List<URI> snapshotURIs,
        String snapshotType, Boolean createInactive, Boolean readOnly, String taskId) {       	
    	boolean vplex = RPHelper.isVPlexVolume(reqVolume);
    	ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, reqVolume.getProtectionController());     
        URI storageControllerURI = null;
        if (vplex) {     
          Volume backendVolume = vplexBlockServiceApiImpl.getVPLEXSnapshotSourceVolume(reqVolume);
          storageControllerURI = backendVolume.getStorageController();
        } else {
            storageControllerURI = reqVolume.getStorageController();
        }
        
        if (isProtectionBasedSnapshot(reqVolume, snapshotType)) {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageControllerURI);
            RPController controller = (RPController) getController(RPController.class, protectionSystem.getSystemType());
            controller.createSnapshot(protectionSystem.getId(), storageSystem.getId(), snapshotURIs, createInactive, readOnly, taskId);   
        } else {
            if (vplex) {
                super.createSnapshot(vplexBlockServiceApiImpl.getVPLEXSnapshotSourceVolume(reqVolume), snapshotURIs, snapshotType, createInactive, readOnly, taskId);
            }
            else {
                super.createSnapshot(reqVolume, snapshotURIs, snapshotType, createInactive, readOnly, taskId);
            }
        }     
    }
    
    /**
     * Uses the appropriate controller to delete the snapshot.
     * 
     * @param snapshot The snapshot to delete
     * @param taskId The unique task identifier
     */
    @Override
    public void deleteSnapshot(BlockSnapshot snapshot, String taskId) {
        // If the volume is under protection
        if (TechnologyType.RP.name().equals(snapshot.getTechnologyType())) {
            Volume volume = _dbClient.queryObject(Volume.class,  snapshot.getParent());
            RPController rpController = getController(RPController.class, ProtectionSystem._RP);
            rpController.deleteSnapshot(volume.getProtectionController(), snapshot.getId(), taskId);
        } else {
            super.deleteSnapshot(snapshot, taskId);
        }
    }
    
    /**
     * Does this snapshot require any sort of protection intervention?  If it's a local array-based
     * snapshot, probably not.  If it's a protection-based snapshot or a remote array-based snapshot
     * that requires protection intervention to ensure consistency between the source and target, then
     * you should go to the protection controller
     *
     * @param volume source volume
     * @param snapshotType The snapshot technology type.
     * 
     * @return true if this is a protection based snapshot, false otherwise.
     */
    private boolean isProtectionBasedSnapshot(Volume volume, String snapshotType) {
        // This is a protection based snapshot request if:
        // The volume allows for bookmarking (it's under protection) and
        // - The param either asked for a bookmark, or
        // - The param didn't ask for a bookmark, but the volume is a remote volume
        if (volume.getProtectionController() != null
            && (snapshotType.equalsIgnoreCase(BlockSnapshot.TechnologyType.RP.toString()) || volume
                .getPersonality().equals(Volume.PersonalityTypes.TARGET.toString()))) {
            return true;
        }
        return false;
    }
    
    /**
     * Validates a restore snapshot request.
     *
     * @param snapshot The snapshot to restore.
     * @param parent The parent of the snapshot
     */
    public void validateRestoreSnapshot(BlockSnapshot snapshot, Volume parent) {
        // RecoverPoint snapshots (bookmarks) will be automatically activated 
        // before restore.
        if (snapshot.getEmName() == null) {
            super.validateRestoreSnapshot(snapshot, parent);
        }
    }
    
    /**
     * Restore the passed parent volume from the passed snapshot of that parent volume.
     * 
     * @param snapshot The snapshot to restore
     * @param parentVolume The volume to be restored.
     * @param taskId The unique task identifier.
     */
    @Override
    public void restoreSnapshot(BlockSnapshot snapshot, Volume parentVolume, String taskId) {
        // If the volume is under protection
        if (snapshot.getEmName() != null) {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                snapshot.getStorageController());
            RPController controller = getController(RPController.class, ProtectionSystem._RP);
            controller.restoreVolume(parentVolume.getProtectionController(),
                storageSystem.getId(), snapshot.getId(), taskId);
        } else {
            super.restoreSnapshot(snapshot, parentVolume, taskId);
        }
    }
    
    /**
     * Get the snapshots for the passed volume.
     * 
     * @param volume A reference to a volume.
     *  
     * @return The snapshots for the passed volume.
     */
    @Override
    public List<BlockSnapshot> getSnapshots(Volume volume) {
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        
        // Get all related RP volumes
        List<URI> rpVolumes = _rpHelper.getReplicationSetVolumes(volume);
                
        for (URI rpVolumeURI : rpVolumes) {
            Volume rpVolume = _dbClient.queryObject(Volume.class, rpVolumeURI);
            
            // Get all the related local snapshots and RP bookmarks for this RP Volume            
            snapshots.addAll(super.getSnapshots(rpVolume));
            
            // If this is a RP+VPLEX/MetroPoint volume get any local snaps for this volume as well,
            // we need to call out to VPLEX Api to get this information as the parent of these
            // snaps will be the backing volume.
            boolean vplex = RPHelper.isVPlexVolume(rpVolume);
            if (vplex) {
                snapshots.addAll(vplexBlockServiceApiImpl.getSnapshots(rpVolume));
            }
        }
        
        return snapshots;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI,
        List<URI> volumeURIs) {
        
        List<VolumeDescriptor> volumeDescriptors = _rpHelper.getDescriptorsForVolumesToBeDeleted(systemURI, volumeURIs);
        
        List<VolumeDescriptor> filteredDescriptors = VolumeDescriptor.filterByType(volumeDescriptors, 
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA}, 
                new VolumeDescriptor.Type[] { });
        for (VolumeDescriptor descriptor : filteredDescriptors) {
            URI volumeURI = descriptor.getDeviceURI();
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            if (volume != null && !volume.getInactive()) {
                addDescriptorsForMirrors(volumeDescriptors, volume);
            }
        }
        
        return volumeDescriptors;

    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void cleanupForViPROnlyDelete(List<VolumeDescriptor> volumeDescriptors) {
        // For a VIPR only deletion make sure to clean up the export
        // groups and mask so that they no longer reference associated
        // volumes.
        List<VolumeDescriptor> sourceVolumeDescriptors = VolumeDescriptor
            .getDescriptors(volumeDescriptors, VolumeDescriptor.Type.RP_SOURCE);
        List<URI> sourceVolumeURIs = new ArrayList<URI>();
        for (URI sourceVolumeURI : VolumeDescriptor.getVolumeURIs(sourceVolumeDescriptors)) {
            Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
            if (sourceVolume != null && !sourceVolume.getInactive() && 
                    !sourceVolume.isIngestedVolume(_dbClient)) { // Keeping this in here for when we do RP ingest.
                sourceVolumeURIs.add(sourceVolumeURI);
            }
        }
        for (URI assocVolumeURI : _rpHelper.getVolumesToDelete(sourceVolumeURIs)) {
            cleanVolumeFromExports(assocVolumeURI, true);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperations(Volume volume, VirtualPool volumeVirtualPool,
                                                          VirtualPool newVirtualPool, StringBuffer notSuppReasonBuff) {
        List<VirtualPoolChangeOperationEnum> allowedOperations = new ArrayList<VirtualPoolChangeOperationEnum>();
        
        if (VirtualPool.vPoolSpecifiesProtection(newVirtualPool) &&
            VirtualPoolChangeAnalyzer.isSupportedRPVolumeVirtualPoolChange(volume, volumeVirtualPool, 
                                                                                    newVirtualPool, _dbClient, notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.RP_PROTECTED);
        } 

        return allowedOperations;
    }
    
    /**
     * Method to determine whether the capacities for all the volumes being provisioned
     * can match.  Currently the capacities for vmax and xtremio cannot match exactly.
     * 
     * @param volumeStorageSystems - map indicating storage system types required to fulfill this request
     * @return boolean - indicating if the capacity of all volumes can match
     */
    protected boolean capacitiesCanMatch(Map<URI, StorageSystem> volumeStorageSystems) {
    	String systemToCompare = null;
    	for(Map.Entry<URI, StorageSystem> entry : volumeStorageSystems.entrySet()){
    		_log.info("Request requires provisioning on storage array of type: " + entry.getValue().getSystemType());
    		if (NullColumnValueGetter.isNullValue(systemToCompare)) {
    			systemToCompare = entry.getValue().getSystemType();
    			continue;
    		}
    		if (!capacityCalculatorFactory.getCapacityCalculator(systemToCompare).capacitiesCanMatch(entry.getValue().getSystemType())) {
    			_log.info("Determined that the capacity for all volumes being provisioned cannot match.");
    			return false;    			
    		}    		    	
    	}
    	_log.info("Determined that the capacity for all volumes being provisioned can match.");    		    
    	return true;
    }
    
    /**
     * Method to determine and set the capacity for volumes being provisioned on
     * storage systems where the capacities cannot match
     * 
     * @param allVolumesToUpdateCapacity - list of volumes to update the capacities of
     * @param associatedVolumePersonalityMap - map of the associated back end volumes and their personality type 
     * @param isExpand - boolean indicating if this is an expand operation
     * @param requestedSize - size requested in an expansion operation
     */
    protected Map<Volume.PersonalityTypes, Long> setUnMatchedCapacities(List<Volume> allVolumesToUpdateCapacity, Map<URI, String> associatedVolumePersonalityMap, boolean isExpand, Long capacityToUseInCalculation) {
    	Long srcCapacity = 0L;
    	Long tgtCapacity = 0L;
    	Map<Volume.PersonalityTypes, Long> capacities = new HashMap<Volume.PersonalityTypes, Long>();    	
    	for (Volume volume: allVolumesToUpdateCapacity) {
    		if ((NullColumnValueGetter.isNotNullValue(volume.getPersonality()) && volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.toString())) ||
    				(NullColumnValueGetter.isNotNullValue(associatedVolumePersonalityMap.get(volume.getId())) && associatedVolumePersonalityMap.get(volume.getId()).equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.toString()))) {
    			srcCapacity = (srcCapacity != 0L) ? srcCapacity : determineCapacity(volume, Volume.PersonalityTypes.SOURCE, capacityToUseInCalculation);
    			if (!isExpand) {
    				updateVolumeCapacity(volume, srcCapacity, isExpand);
    			}
    			
    		} else if ((NullColumnValueGetter.isNotNullValue(volume.getPersonality()) && volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString())) ||
    				(NullColumnValueGetter.isNotNullValue(associatedVolumePersonalityMap.get(volume.getId())) && associatedVolumePersonalityMap.get(volume.getId()).equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString()))) {
    			tgtCapacity = (tgtCapacity != 0L) ? tgtCapacity : determineCapacity(volume, Volume.PersonalityTypes.TARGET, capacityToUseInCalculation);
    			if (!isExpand) {
    				updateVolumeCapacity(volume, tgtCapacity, isExpand);
    			}
    		}
    	}
    	capacities.put(Volume.PersonalityTypes.SOURCE, srcCapacity);
    	capacities.put(Volume.PersonalityTypes.TARGET, tgtCapacity);
    	return capacities;
    }
        
    /**
     * Method to determine the capacity of either the source or target volumes in an unmatched capacity situation
     * 
     * @param volume - volume to determine the capacity for
     * @param type - personality type of the volume
     * @param isExpand - boolean indicating if this is an expand operation
     * @param requestedSize - size requested in an expansion operation
     * @return the determined capacity size for the volume
     */
    private Long determineCapacity(Volume volume, Volume.PersonalityTypes type, Long capacityToUseInCalculation) {
    	long capacity = 0L;
    	
    	StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,  volume.getStorageController());
    	    		
    	if (type == Volume.PersonalityTypes.SOURCE) {
    		capacity = capacityCalculatorFactory.getCapacityCalculator(storageSystem.getSystemType()).calculateAllocatedCapacity(capacityToUseInCalculation);
    	} else if (type == Volume.PersonalityTypes.TARGET) {
    		capacity = capacityCalculatorFactory.getCapacityCalculator(storageSystem.getSystemType()).calculateAllocatedCapacity(capacityToUseInCalculation + 5242880L);
    	}
    	
    	return capacity;
    }
    
    /**
     * Method to both update and persist the volume capacity
     * 
     * @param volume - volume to update the capacity for
     * @param capacity - capacity to update the volume to
     * @param isExpand - boolean indicating if this is an expand operation
     */
    private void updateVolumeCapacity(Volume volume, Long capacity, boolean isExpand) {
    	if (!volume.getCapacity().equals(capacity)) {                     
            // Update the volume's originally requested capacity in Cassandra
            // to be the new capacity. This will end up being used
            // as the capacity value when provisioning the volumes on the 
            // storage systems via the SMI-S provider.
            //
            // However, we don't want to persist the size of the volume if the operation is 
            // expand because the expand code takes care of that based on if the volume was 
            // indeed expanded or not.
            if (!isExpand) {
                _log.info(String.format("Updating capacity for volume [%s] from %s to %s.", 
                            volume.getLabel(), 
                            volume.getCapacity(), 
                            capacity));
                // Update capacity and persist volume
                volume.setCapacity(capacity);
                _dbClient.persistObject(volume);
            }
            else {
                _log.info(String.format("Do not update capacity for volume [%s] as this is an expand operation.", volume.getLabel()));
            }
        }
        else {
            _log.info(String.format("No need to update capacity for volume [%s].", volume.getLabel()));
        }
    }
}
