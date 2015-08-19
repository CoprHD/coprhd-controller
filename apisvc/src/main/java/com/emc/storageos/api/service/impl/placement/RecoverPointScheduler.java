/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RPSiteArray;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ConnectivityUtil.StorageSystemType;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.Protection.ProtectionType;
import com.emc.storageos.volumecontroller.RPProtectionRecommendation;
import com.emc.storageos.volumecontroller.RPProtectionRecommendation.PlacementProgress;
import com.emc.storageos.volumecontroller.RPRecommendation;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.VPlexRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.CapacityMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;

/**
 * Advanced RecoverPoint based scheduling function for block storage.  StorageScheduler is done based on desired
 * class-of-service parameters for the provisioned storage.
 */
public class RecoverPointScheduler implements Scheduler {
	
    public static final Logger _log = LoggerFactory.getLogger(RecoverPointScheduler.class);
    
    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    DbClient dbClient;
    protected CoordinatorClient coordinator;
    private RPHelper rpHelper;
    private StorageScheduler blockScheduler;          
    private VPlexScheduler vplexScheduler;            
    
    private Map<String, List<String>> storagePoolStorageSystemCache;   
    private Map<VirtualArray, Boolean> tgtVarrayHasHaVpool =
            new HashMap<VirtualArray, Boolean>();  
    private RPRecommendation srcHaRecommendation = new RPRecommendation();            
    private Map<URI, Recommendation> tgtHaRecommendation = 
            new HashMap<URI, Recommendation>();
    
    private AttributeMatcherFramework _matcherFramework;
    public void setMatcherFramework(AttributeMatcherFramework matcherFramework) {
        _matcherFramework = matcherFramework;
    }
    
    private PlacementStatus placementStatus;
    private PlacementStatus secondaryPlacementStatus;
    
    // List of storage systems that require vplex to provide protection
    private static List<String> systemsRequiringVplex = new ArrayList<String> 
	(Arrays.asList(DiscoveredDataObject.Type.xtremio.toString(), DiscoveredDataObject.Type.hds.toString()));
    
    public void setBlockScheduler(StorageScheduler blockScheduler) {
        this.blockScheduler = blockScheduler;
    }

    public void setRpHelper(RPHelper rpHelper) {
        this.rpHelper = rpHelper;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    
    public void setCoordinator(CoordinatorClient locator) {
        coordinator = locator;
    }
	public VPlexScheduler getVplexScheduler() {
		return vplexScheduler;
	}

	public void setVplexScheduler(VPlexScheduler vplexScheduler) {
		this.vplexScheduler = vplexScheduler;
	}

	public Map<String, List<String>> getStoragePoolStorageSystemCache() {
		return storagePoolStorageSystemCache;
	}

	public void setStoragePoolStorageSystemCache(
			Map<String, List<String>> storagePoolStorageSystemCache) {
		this.storagePoolStorageSystemCache = storagePoolStorageSystemCache;
	}
	
	public Map<VirtualArray, Boolean> getTgtVarrayHasHaVpool() {
		return tgtVarrayHasHaVpool;
	}

	public void setTgtVarrayHasHaVpool(Map<VirtualArray, Boolean> tgtVarrayHasHaVpool) {
		this.tgtVarrayHasHaVpool = tgtVarrayHasHaVpool;
	}
	
	public RPRecommendation getSrcHaRecommendation() {
       return this.srcHaRecommendation;
   }

   public void setSrcHaRecommendation(
           RPRecommendation srcHaRecommendation) {
       this.srcHaRecommendation = srcHaRecommendation;
   }

   public Map<URI, Recommendation> getTgtHaRecommendation() {
       return tgtHaRecommendation;
   }

   public void setTgtHaRecommendation(
           Map<URI, Recommendation> tgtHaRecommendation) {
       this.tgtHaRecommendation = tgtHaRecommendation;
   }
    
    /**
     * 
     *
     */
    public class RPVPlexVarrayVpool {
        private VirtualArray srcVarray = null;
        private VirtualPool srcVpool = null;
        private VirtualArray haVarray = null;
        private VirtualPool haVpool = null;
        public VirtualArray getSrcVarray() {
            return srcVarray;
        }
        public void setSrcVarray(VirtualArray srcVarray) {
            this.srcVarray = srcVarray;
        }
        public VirtualPool getSrcVpool() {
            return srcVpool;
        }
        public void setSrcVpool(VirtualPool srcVpool) {
            this.srcVpool = srcVpool;
        }
        public VirtualArray getHaVarray() {
            return haVarray;
        }
        public void setHaVarray(VirtualArray haVarray) {
            this.haVarray = haVarray;
        }
        public VirtualPool getHaVpool() {
            return haVpool;
        }
        public void setHaVpool(VirtualPool haVpool) {
            this.haVpool = haVpool;
        }                       
    }

    /**
     * Gets and verifies that the protection varrays passed in the request are
     * accessible to the tenant.
     *
     * @param project A reference to the project.
     * @param vpool class of service, contains protection varrays
     * @return A reference to the varrays
     * @throws java.net.URISyntaxException
     * @throws com.emc.storageos.db.exceptions.DatabaseException
     */
    static public List<VirtualArray> getProtectionVirtualArraysForVirtualPool(Project project, VirtualPool vpool, DbClient dbClient,
                                                                               PermissionsHelper permissionHelper) {
        List<VirtualArray> protectionVirtualArrays = new ArrayList<VirtualArray>();
        if (vpool.getProtectionVarraySettings() != null) {
            for (String protectionVirtualArray : vpool.getProtectionVarraySettings().keySet()) {
                try {
                    VirtualArray nh = dbClient.queryObject(VirtualArray.class, new URI(protectionVirtualArray));
                    protectionVirtualArrays.add(nh);
                    permissionHelper.checkTenantHasAccessToVirtualArray(project.getTenantOrg().getURI(), nh);
                } catch (URISyntaxException e) {
                    throw APIException.badRequests.invalidURI(protectionVirtualArray);
                }
            }
        }
        return protectionVirtualArrays;
    }

    /**
     * Select and return one or more storage pools where the volume(s)
     * should be created. The placement logic is based on:
     *      - varray, only storage devices in the given varray are candidates
     *      - protection varrays
     *      - CoS, specifies must-meet & best-meet service specifications
     *        - access-protocols: storage pools must support all protocols specified in CoS
     *        - snapshots: if yes, only select storage pools with this capability
     *        - snapshot-consistency: if yes, only select storage pools with this capability
     *        - performance: best-match, select storage pools which meets desired performance
     *        - provision-mode: thick/thin
     *        - numPaths: select storage pools with required number of paths to the volume
     *      - size: Place the resources in the minimum number of storage pools that can
     *              accommodate the size and number of resource requested.
     *
     *
     * @param varray varray requested for source
     * @param project for the storage
     * @param vpool vpool requested
     * @param capabilities CoS capabilities parameters
     * @return list of Recommendation objects to satisfy the request
     */
	public List<Recommendation> getRecommendationsForResources(VirtualArray varray, Project project, VirtualPool vpool,
							            VirtualPoolCapabilityValuesWrapper capabilities) {

        _log.info("Schedule storage for {} resource(s) of size {}.", capabilities.getResourceCount(), capabilities.getSize());

        List<VirtualArray> protectionVarrays = getProtectionVirtualArraysForVirtualPool(project, vpool, dbClient, _permissionsHelper);

        VirtualArray haVarray = null;
        VirtualPool haVpool = null; 
        RPVPlexVarrayVpool container = null; 
        if (VirtualPool.vPoolSpecifiesMetroPoint(vpool)){
        	container = swapSrcAndHAIfNeeded(varray, vpool);
        	varray = container.getSrcVarray();
        	vpool = container.getSrcVpool();
        	haVarray = container.getHaVarray();
        	haVpool = container.getHaVpool();        	
        }        

        // Get all storage pools that match the passed CoS params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.      
        List<StoragePool> candidatePools = getCandidatePools(varray, vpool, haVarray, haVpool, project, capabilities, RPHelper.SOURCE);                       
        
        if (candidatePools == null || candidatePools.isEmpty()) {
        	_log.error("No matching storage pools found for the source varray: {0}. There are no storage pools that " +
            		"match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to " +
            		"hold at least one resource of the requested size.",varray.getLabel());
        	throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getId(), varray.getId());            
        }
                      
        List<Recommendation> recommendations = buildCgRecommendations(capabilities, vpool, protectionVarrays, null);        
                                             
        this.initResources();             
        if (recommendations.isEmpty()){ 	  
	        if (VirtualPool.vPoolSpecifiesMetroPoint(vpool)) {
	        	_log.info("Getting recommendations for Metropoint volume placement...");    
	        	// MetroPoint has been enabled.  get the HA virtual array and virtual pool.  This will allow us to obtain 
	        	// candidate storage pool and secondary cluster protection recommendations.	        
	        	 haVarray = vplexScheduler.getHaVirtualArray(container.getSrcVarray(), project, container.getSrcVpool());
	        	 haVpool = vplexScheduler.getHaVirtualPool(container.getSrcVarray(), project, container.getSrcVpool());
	        	
	        	// Get the candidate source pools for the distributed cluster.  The 2 null params are ignored in the pool matching
	        	// because they are used to build the HA recommendations, which will not be done if MetroPoint is enabled.
	            List<StoragePool> haCandidateStoragePools = getCandidatePools(haVarray, haVpool, null, null, project, capabilities, RPHelper.SOURCE);            
	                        
	        	// MetroPoint has been enabled so we need to obtain recommendations for the primary (active) and secondary (HA/Stand-by) 
	            // VPlex clusters.
	            recommendations = createMetroPointRecommendations(container.getSrcVarray(), protectionVarrays, container.getSrcVpool(), haVarray, 
	            		haVpool, project, capabilities, candidatePools, haCandidateStoragePools, null);            
	        } else {
	        	 // Schedule storage based on the source pool constraint.        	
	            recommendations = scheduleStorageSourcePoolConstraint(varray, protectionVarrays, vpool, capabilities, candidatePools, project, null, null);      
	        }
        }
         
       logRecommendations(recommendations);        
        //if (true)
        	//throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(URI.create(null), URI.create(null));   
        //TODO Bharath - Remove intentional exception after testing
        return recommendations;
	}	
    
    /**
     * Schedule storage based on the incoming storage pools for source volumes. (New version)
     * 
     * @param varray varray requested for source
     * @param protectionVarrays Neighborhood to protect this volume to.
     * @param vpool vpool requested
     * @param capabilities parameters
     * @param candidatePools List of StoragePools already populated to choose from. RP+VPLEX. 
     * @param vpoolChangeVolume vpool change volume, if applicable
     * @param vpoolChangeParam 
     * @param protectionStoragePoolsMap pre-populated map for tgt varray to storage pools, use null if not needed
     * @return list of Recommendation objects to satisfy the request
     */
    protected List<Recommendation> scheduleStorageSourcePoolConstraint(VirtualArray varray,
    								List<VirtualArray> protectionVarrays, VirtualPool vpool, VirtualPoolCapabilityValuesWrapper capabilities,
    								List<StoragePool> candidatePools, Project project, Volume vpoolChangeVolume, 
									Map<VirtualArray, List<StoragePool>> preSelectedCandidateProtectionPoolsMap) {
    	 // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        placementStatus = new PlacementStatus();
    	
    	// If this is a change vpool, first check to see if we can build recommendations from the existing RP CG.
        // If this is the first volume in a new CG proceed as normal.
        if (vpoolChangeVolume != null) {     
        	recommendations = buildCgRecommendations(capabilities, vpool, protectionVarrays, vpoolChangeVolume);
        	  _log.info("Produced {} recommendations for placement.", recommendations.size());         
              return recommendations;
        }                      
                
        // Attempt to use these pools for selection based on protection
        StringBuffer sb = new StringBuffer("Determining if protection is possible from " + varray.getId() + " to: ");
        for (VirtualArray protectionVarray : protectionVarrays) {
            sb.append(protectionVarray.getId()).append(" ");
        }
        _log.info(sb.toString());
        
        // BEGIN: Put the local varray first in the list.  We want to give him pick of internal site name.
        int index = -1;
        for (VirtualArray targetVarray : protectionVarrays) {
        	if (targetVarray.getId().equals(varray.getId())) {
        		index = protectionVarrays.indexOf(targetVarray);
            	break;
        	}        	
        }
        
        if (index > 0) {
        	VirtualArray localVarray = protectionVarrays.get(index);
        	VirtualArray swapVarray = protectionVarrays.get(0);
        	protectionVarrays.set(0,  localVarray);
        	protectionVarrays.set(index, swapVarray);
        }
        
        // END: Put the local varray first in the list.  We want to give him pick of internal site name.        
        // Source Storage pool analysis:        
        // We can pretty much just work with the first storage pool in the candidate pool list per storage system.      
        // Go through each storage pool, map to storage system to find connectivity
        // Try with the storagePoolList as it currently is.
        // If we get through the process and couldn't achieve full protection, we should
        // take out the matched pool from the storagePoolList and try again.
      
        List<URI> protectionVarrayURIs = new ArrayList<URI>();
        for (VirtualArray vArray : protectionVarrays) {
        	protectionVarrayURIs.add(vArray.getId());
        	placementStatus.getProcessedProtectionVArrays().put(vArray.getId(), false);
        }
                            
        // If there are any pre selected Source candidate pools, use these (example RP+VPLEX/MetroPoint).
        // Otherwise get the matching Source pools.               
        _log.info(String.format("Source varray [%s] sorted pools:", varray.getLabel()));        
        printSortedStoragePools(candidatePools);
        
        //Fetch the list of pools for the source journal if a journal virtual pool is specified to be used for journal volumes.
        VirtualArray journalVarray = varray;
        if (NullColumnValueGetter.isNotNullValue(vpool.getJournalVarray())) {
        	journalVarray = dbClient.queryObject(VirtualArray.class, URI.create(vpool.getJournalVarray()));
        }
               
        VirtualPool journalVpool = vpool; 
        if (NullColumnValueGetter.isNotNullValue(vpool.getJournalVpool())) {
        	journalVpool = dbClient.queryObject( VirtualPool.class, URI.create(vpool.getJournalVpool()));
        }
                
        List<StoragePool> candidateJournalPools = getCandidatePools(journalVarray,  journalVpool,  null,  null, project, capabilities, RPHelper.JOURNAL);          
				
		StringBuffer journalPlacementLog = new StringBuffer();
		journalPlacementLog.append(String.format("Source varray : [%s--%s] , Source vpool [%s--%s] ", varray.getLabel(), varray.getId(), vpool.getLabel(), vpool.getId()));
		journalPlacementLog.append(String.format("Journal varray : [%s--%s], Journal vpool [%s--%s]", journalVarray.getLabel(), journalVarray.getId(), journalVpool.getLabel(), journalVpool.getId()));
		journalPlacementLog.append("Dumping journal storage pools:");
		
		printSortedStoragePools(candidateJournalPools);		
		_log.info(journalPlacementLog.toString());
  	
		// The attributes below will not change throughout the placement process
        placementStatus.setSrcVArray(varray.getLabel());
        placementStatus.setSrcVPool(vpool.getLabel());
               
        BlockConsistencyGroup cg = dbClient.queryObject(
                BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());  
        
        int totalRequestedCount = capabilities.getResourceCount();
        int totalSatisfiedCount = 0;
        int requestedCount = totalRequestedCount; 
        int satisfiedCount = 0;
      
        List<Recommendation> sourcePoolRecommendations =  getRecommendedPools(candidatePools, capabilities.getSize());        
        if (sourcePoolRecommendations == null || sourcePoolRecommendations.isEmpty()) {        	
        	_log.error(String.format("RP Placement : No matching storage pools found for the source varray: {%s}. There are no storage pools that " +
            		"match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to " +
            		"hold at least one resource of the requested size.",varray.getLabel()));
        	throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getId(), varray.getId()); 
        }
        
        String candidateSourceInternalSiteName = "";
        RPProtectionRecommendation rpProtectionRecommendation = new RPProtectionRecommendation();
        RPRecommendation sourceJournalRecommendation = null;       
       
		rpProtectionRecommendation.setVpoolChangeVolume(vpoolChangeVolume != null ? vpoolChangeVolume.getId() : null);
		rpProtectionRecommendation.setVpoolChangeVpool(vpoolChangeVolume != null ? vpoolChangeVolume.getVirtualPool() : null);
		rpProtectionRecommendation.setVpoolChangeProtectionAlreadyExists(vpoolChangeVolume != null ? vpoolChangeVolume.checkForRp() : false);			
	
    	for (Recommendation sourcePoolRecommendation : sourcePoolRecommendations) {    		
    		satisfiedCount = (sourcePoolRecommendation.getResourceCount() >= requestedCount) ? requestedCount : sourcePoolRecommendation.getResourceCount();        			        	           
        	_log.info("Looking to place " + satisfiedCount + " resources...");
    		// Start with the top of the list of source pools, find a solution based on that.            
    		// Given the candidatePools.get(0), what protection systems and internal sites protect it?
    	    Set<ProtectionSystem> protectionSystems = new HashSet<ProtectionSystem>();
    	    ProtectionSystem cgProtectionSystem = getCgProtectionSystem(capabilities.getBlockConsistencyGroup());
    		StoragePool sourcePool =  dbClient.queryObject(StoragePool.class, sourcePoolRecommendation.getSourcePool());      	        	        	
    		// If we have an existing RP consistency group we want to use the same protection system
    		// used by other volumes in it. 
    		if (cgProtectionSystem != null) {
    		    _log.info("RP Placement : Narrowing down placement to use protection system {}, which is currently used by RecoverPoint consistency group {}.", 
    		            cgProtectionSystem.getLabel(), cg);
    		    protectionSystems.add(cgProtectionSystem);
    		} else {
    		    protectionSystems = getProtectionSystemsForStoragePool(sourcePool, varray, VirtualPool.vPoolSpecifiesHighAvailability(vpool));
        		// Verify that the candidate pool can be protected
        		if (protectionSystems.isEmpty()) {            		
        			continue;
        		}
    		}      	

            // Sort the ProtectionSystems based on the last time a CG was created. Always use the
    		// ProtectionSystem with the oldest cgLastCreated timestamp to support a round-robin
    		// style of load balancing.
            List<ProtectionSystem> protectionSystemsLst = sortProtectionSystems(protectionSystems);               	
    		for (ProtectionSystem candidateProtectionSystem : protectionSystemsLst) {
                Calendar cgLastCreated = candidateProtectionSystem.getCgLastCreatedTime();
                
                _log.info("Attempting to use protection system {}, which was last used to create a CG on {}.", 
                        candidateProtectionSystem.getLabel(), cgLastCreated != null ? cgLastCreated.getTime().toString() : "N/A");        		    
    		    
    		    List<String> associatedStorageSystems = new ArrayList<String>();
    		    String internalSiteNameandAssocStorageSystem = getCgSourceInternalSiteNameAndAssociatedStorageSystem(capabilities.getBlockConsistencyGroup());
            
    		    // If we have existing source volumes in the RP consistency group, we want to use the same
    		    // source internal site.
    		    if (internalSiteNameandAssocStorageSystem != null) {
    		        _log.info("RP Placement : Narrowing down placement to use source internal site {}, which is currently used by RecoverPoint consistency group {}.", 
    		                internalSiteNameandAssocStorageSystem, cg);
    		        associatedStorageSystems.add(internalSiteNameandAssocStorageSystem);
    		    } else {
    		        associatedStorageSystems = getCandidateVisibleStorageSystems(sourcePool, candidateProtectionSystem, varray, protectionVarrays, VirtualPool.vPoolSpecifiesHighAvailability(vpool));
    		    }            	            	
    			// Get candidate internal site names and associated storage system, make sure you check RP topology to see if the sites can protect that many targets            	            	
    			if (associatedStorageSystems.isEmpty()) {
    				// no rp site clusters connected to this storage system, should not hit this, but just to be safe we'll catch it
    				_log.info("RP Placement: Protection System " + candidateProtectionSystem.getLabel() +  " does not have an rp site cluster connected to Storage pool " + sourcePool.getLabel());
    				continue;
    			}

    			for (String associatedStorageSystem : associatedStorageSystems) {    
    				if (candidateSourceInternalSiteName.isEmpty()) {
    					candidateSourceInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(associatedStorageSystem);
    				}
    			    
    			    _log.info(String.format("RP Placement : Choosing RP site %s for source", candidateSourceInternalSiteName));    		
    			    rpProtectionRecommendation.setProtectionDevice(candidateProtectionSystem.getId());    
    				RPRecommendation rpRecommendation = buildSourceRecommendation(associatedStorageSystem, varray, vpool, 
																candidateProtectionSystem, sourcePool, candidatePools,
																capabilities, satisfiedCount, placementStatus, vpoolChangeVolume, false);
    				if (rpRecommendation == null) {
    					// No placement found for the associatedStorageSystem, so continue.
    					continue;
    				}    			
    				    		
    				RPRecommendation haRecommendation = this.getHaRecommendation(varray, vpool, project, capabilities);
    				if (haRecommendation != null) {
    					rpRecommendation.setHaRecommendation(haRecommendation);
    				}
    				    				
    				totalSatisfiedCount += satisfiedCount;
    				requestedCount = requestedCount - totalSatisfiedCount;  
    				rpProtectionRecommendation.getSourceRecommendations().add(rpRecommendation);
                 	
    				if (sourceJournalRecommendation == null) {
    					sourceJournalRecommendation = buildJournalRecommendation(rpProtectionRecommendation, candidateSourceInternalSiteName, vpool.getJournalSize(),
    													journalVarray, journalVpool, candidateProtectionSystem,
     			    									candidateJournalPools, capabilities, totalSatisfiedCount, vpoolChangeVolume, false);
    					rpProtectionRecommendation.setSourceJournalRecommendation(sourceJournalRecommendation);
    				}
    				    				    		    	    		   
    				// If we made it this far we know that our source virtual pool and associated source virtual array 
    				// has a storage pool with enough capacity for the requested resources and which is accessible to an rp cluster site                   
    		    	rpProtectionRecommendation.setPlacementStepsCompleted(PlacementProgress.IDENTIFIED_SOLUTION_FOR_SOURCE);
    				if (placementStatus.isBestSolutionToDate(rpProtectionRecommendation)){
    					placementStatus.setLatestInvalidRecommendation(rpProtectionRecommendation);
    				}		
    		        
    		        // TODO Joe:  need this when we are creating multiple recommendations
    				// placementStatus.setLatestInvalidRecommendation(null);
    		        
		
    				// Find a solution, given this vpool, and the target varrays
    				if (findSolution(rpProtectionRecommendation, rpRecommendation, varray, vpool, protectionVarrays, 
    						capabilities, satisfiedCount, false, null, project)) {                     	
    					// Check to ensure the protection system can handle the new resources about to come down
    					if (!verifyPlacement(candidateProtectionSystem, rpProtectionRecommendation, rpProtectionRecommendation.getResourceCount())) {
    						continue;
    					}    					    				
    					 					    				                       
                        if ((totalSatisfiedCount >= totalRequestedCount)) {	
                        	recommendations.add(rpProtectionRecommendation);
                        	for (Recommendation rec : recommendations) {
                        		 _log.info("RP Placement: Found a recommendation for the request:\n" + ((RPProtectionRecommendation)rec).toString(dbClient)); 
                        	}
                        	// for testing	                        	
                            // if (true) throw APIException.badRequests.cannotFindSolutionForRP(placementStatus.toString(dbClient));
                        	return recommendations;
                        }
    				} else {
    					candidateSourceInternalSiteName = "";
    					// Not sure there's anything to do here.  Just go to the next candidate protection system or Protection System
    					_log.info("RP Placement : Could not find a solution against protection system {} and internal cluster name {}", 
    							candidateProtectionSystem.getLabel(),
    							candidateSourceInternalSiteName);
    					rpProtectionRecommendation = null;
    				}
    			} // end of for loop trying to find solution using possible rp cluster sites
    		} // end of protection systems for loop    		    	      	        
        }             
        //we went through all the candidate pools and there are still some of the volumes that haven't been placed, then we failed to find a solution      
        _log.error("ViPR could not find matching target storage pools that could be protected via RecoverPoint"); 
    	throw APIException.badRequests.cannotFindSolutionForRP(placementStatus.toString(dbClient)); 
    } 
        
    /**
     * 
     * @param srcVarray
     * @param srcVpool
     * @param project
     * @param capabilities
     * @return
     */
    private List<StoragePool> getCandidatePools(VirtualArray srcVarray, VirtualPool srcVpool, VirtualArray haVarray, VirtualPool haVpool,                                                        
                                                Project project, VirtualPoolCapabilityValuesWrapper capabilities, String personality) {
        
        List<StoragePool> candidateStoragePools = new ArrayList<StoragePool>();
        VirtualPoolCapabilityValuesWrapper newCapabilities = new VirtualPoolCapabilityValuesWrapper(capabilities);
     
        long size = capabilities.getSize();
        if (personality.equals(RPHelper.JOURNAL)) {
        	newCapabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, 1);
        	size = RPHelper.getJournalSizeGivenPolicy(Long.toString(capabilities.getSize()), srcVpool.getJournalSize(), 1);
        	newCapabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, size);
        }
        
        _log.info(String.format("Fetching candidate pools for %s - %s volumes of size %s\n", newCapabilities.getResourceCount(), personality, size));
                        
        // Determine if the source vpool specifies VPlex Protection
        if (VirtualPool.vPoolSpecifiesHighAvailability(srcVpool)) {        	
        	 candidateStoragePools = 
                     this.getMatchingPools(srcVarray,srcVpool, haVarray, haVpool, 
                     		project, newCapabilities);        	         	
        } else {                       
            candidateStoragePools = blockScheduler.getMatchingPools(srcVarray, srcVpool, newCapabilities);              
        } 
            
        if (candidateStoragePools == null || candidateStoragePools.isEmpty()) {
            // There are no matching storage pools found for the source varray
            _log.error("No matching storage pools found for the source varray: {0}. There are no storage pools that " +
                    "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to " +
                    "hold at least one resource of the requested size.",srcVarray.getLabel());
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(srcVpool.getId(), srcVarray.getId());            
        }
        
        // Verify that any storage pool(s) requiring a VPLEX front end for data protection have
        // HA enabled on the vpool, if not remove the storage pool(s) from consideration.
        if (VirtualPool.vPoolSpecifiesHighAvailability(srcVpool)) {
        	candidateStoragePools = removePoolsRequiringHaIfNotEnabled(candidateStoragePools, srcVpool, personality);
        }
        
        blockScheduler.sortPools(candidateStoragePools);
        printSortedStoragePools(candidateStoragePools);
        return candidateStoragePools ;
    }  
    
    private List<StoragePool> getMatchingPools(VirtualArray srcVarray, VirtualPool srcVpool, VirtualArray haVarray, VirtualPool haVpool, 
			Project project, VirtualPoolCapabilityValuesWrapper capabilities) {
    	List<StoragePool> srcCandidateStoragePools = new ArrayList<StoragePool>();
    	Map<String, List<StoragePool>> vplexPoolMapForSrcVarray = getVplexMatchingPools(srcVarray, srcVpool, haVarray, haVpool, project, capabilities);
    	  // Add all the appropriately matched source storage pools
    	if (vplexPoolMapForSrcVarray != null) {
	        for (String vplexId : vplexPoolMapForSrcVarray.keySet()) {
	            srcCandidateStoragePools.addAll(vplexPoolMapForSrcVarray.get(vplexId));
	        }	        
    	}
    	
    	if (!srcCandidateStoragePools.isEmpty()) {
	        _log.info("VPLEX pools matching completed: {}",
	                Joiner.on("\t").join(getURIsFromPools(srcCandidateStoragePools)));
	        blockScheduler.sortPools(srcCandidateStoragePools); 
        }
               
        return srcCandidateStoragePools;
    }
    
    /**
     * Retrieve valid Storage Pools from the VPLEX Scheduler.
     * 
     * @param srcVarray
     * @param srcVpool
     * @param haVpool 
     * @param haVarray 
     * @param capabilities
     * @return
     */    
    private Map<String, List<StoragePool>> getVplexMatchingPools(VirtualArray srcVarray, VirtualPool srcVpool, VirtualArray haVarray, VirtualPool haVpool, 
    							Project project, VirtualPoolCapabilityValuesWrapper capabilities) {    	     
        _log.info("Get matching pools for Varray[{}] and Vpool[{}]...", srcVarray.getLabel(), srcVpool.getLabel());
        List<StoragePool> allMatchingPools = vplexScheduler.getMatchingPools(srcVarray, null,
            srcVpool, capabilities);
                   
        _log.info("Get VPlex systems for placement...");
        // TODO Fixing CTRL-3360 since it's a blocker, will revisit this after. This VPLEX
        // method isn't doing what it indicates. It's looking at the CG to see if it's created
        // or not for VPLEX. Which is unneeded for RP+VPLEX.  
        //Set<URI> requestedVPlexSystems = 
        //      vplexScheduler.getVPlexSystemsForPlacement(srcVarray, srcVpool, srcVpoolCapabilities);        
        Set<URI> requestedVPlexSystems = null;
        
        _log.info("Get VPlex connected matching pools...");
        // Get the VPLEX connected storage pools
        Map<String, List<StoragePool>> vplexPoolMapForSrcVarray = 
                vplexScheduler.getVPlexConnectedMatchingPools(srcVarray, requestedVPlexSystems,
                        capabilities, allMatchingPools);
        
        if (vplexPoolMapForSrcVarray != null && !vplexPoolMapForSrcVarray.isEmpty()) {
             _log.info("Remove non RP connected storage pools...");
            // We only care about RP-connected VPLEX storage systems
             vplexPoolMapForSrcVarray = getRPConnectedVPlexStoragePools(vplexPoolMapForSrcVarray);
            
            if (vplexPoolMapForSrcVarray.isEmpty()) {
                // There are no RP connected VPLEX storage systems so we cannot provide
                // any placement recommendations.
                _log.info("No matching pools because there are no VPlex connected storage systems for the requested virtual array.");
                return null;
            }            
          
        } else {
            // There are no matching pools in the source virtual array
            // on any arrays connected to a VPlex storage system
            // or there are, but a specific VPlex system was requested
            // and there are none for that VPlex system.
            _log.info("No matching pools on storage systems connected to a VPlex");
            return null;
        }                            
        return vplexPoolMapForSrcVarray;
    }

	private RPRecommendation getHaRecommendation(VirtualArray varray,
			VirtualPool vpool,
			Project project, VirtualPoolCapabilityValuesWrapper capabilities) {
		
		// If the source Vpool specifies VPlex, we need to check if this is VPLEX local or VPLEX
        // distributed. If it's VPLEX distributed, there will be a separate recommendation just for that
        // which will be used by VPlexBlockApiService to create the distributed volumes in VPLEX.
        
        // If HA / VPlex Distributed is specified we need to get the VPLEX recommendations       
        // Only find the HA recommendations if MetroPoint is not enabled.  The HA/secondary cluster
        // Recommendations for MetroPoint need to involve RP connectivity so there is no sense executing 
        // this logic.    		
		if (vpool.getHighAvailability() ==  null || VirtualPool.HighAvailabilityType.vplex_local.name().equals(vpool.getHighAvailability()) 
				|| VirtualPool.vPoolSpecifiesMetroPoint(vpool)) {
			return null;
		}
				
		VirtualArray haVarray = vplexScheduler.getHaVirtualArray(varray, project, vpool);
   	 	VirtualPool haVpool = vplexScheduler.getHaVirtualPool(varray, project,vpool);		
		Map<String, List<StoragePool>> vplexPoolMapForSrcVarray = getVplexMatchingPools(varray, vpool, haVarray, haVpool, project, capabilities);
				  
    	Recommendation haRecommendation = findVPlexHARecommendations(varray, vpool, haVarray, haVpool,
                          	project, capabilities, vplexPoolMapForSrcVarray);
        if (haRecommendation == null) {
            _log.error("No HA Recommendations could be created.");
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getId(), varray.getId());
        }
        
        RPRecommendation rpHaRecommendation = new RPRecommendation();
        VPlexRecommendation vplexRec = (VPlexRecommendation) haRecommendation;
        rpHaRecommendation.setSourcePool(vplexRec.getSourcePool());
        rpHaRecommendation.setSourceDevice(vplexRec.getSourceDevice());
        rpHaRecommendation.setVirtualArray(vplexRec.getVirtualArray());
        rpHaRecommendation.setVirtualPool(vplexRec.getVirtualPool());
        rpHaRecommendation.setVirtualVolumeRecommendation(vplexRec);
        return rpHaRecommendation;             
	}
	
	 /**
     * This method is driven by the flag on the RP+VPLEX Source Vpool to use HA as RP Source or not.
     * When that flag is set we use the HA Varray and HA Vpool as the Sources for placement. Consequently, 
     * the Source Varray and Source VPool are then used for HA placement.
     * 
     * We may not need to swap if the flag isn't set, but this method returns the RPVPlexVarryVpool object
     * regardless so we can then just reference the resources in that object to pass down to the rest of the
     * scheduler methods.
     * 
     * @param srcVarray Original src varray 
     * @param srcVpool Original src vpool
     * @return RPVPlexVarryVpool which contains references to the src varray, src vpool, ha varray, ha vpool
     */
    private RPVPlexVarrayVpool swapSrcAndHAIfNeeded(VirtualArray srcVarray,
            VirtualPool srcVpool) {
        VirtualArray haVarray = null;
        VirtualPool haVpool = null;
        
        RPVPlexVarrayVpool container = new RPVPlexVarrayVpool();        
        container.setSrcVarray(srcVarray);
        container.setSrcVpool(srcVpool);            
        container.setHaVarray(haVarray);
        container.setHaVpool(haVpool);          

        // Potentially will swap src and ha, returns the container.
        container = setActiveProtectionAtHAVarray(container, dbClient);
        return container;
    }
    
    /**
     * Initialize common resources
     */
    private void initResources() {
        // initialize the storage pool -> storage systems map
        this.storagePoolStorageSystemCache = new HashMap<String, List<String>>();
        // Reset the HA Recommendations
        this.srcHaRecommendation = new RPRecommendation();
        this.tgtHaRecommendation = new HashMap<URI, Recommendation>();
        this.tgtVarrayHasHaVpool = new HashMap<VirtualArray, Boolean>();        
    }
	
	/**
	 * Determine if the storage pools require VPLEX in order to provide protection
	 * if they do and HA is not enabled on the vpool, remove it from consideration
	 * @param candidatePools - storage pools under consideration for protection
	 * @param vpool - the virtual pool where the candidate pools are referenced
	 * @return list of storage pools after non-valid storage pools are removed
	 */
    public List<StoragePool> removePoolsRequiringHaIfNotEnabled(List<StoragePool> candidatePools, VirtualPool vpool, String personality) {
    	List<StoragePool> storagePools = candidatePools;
    	List<StoragePool> invalidPools = new ArrayList<StoragePool>();			   
		for (StoragePool currentPool: candidatePools) {
			StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, currentPool.getStorageDevice());				
			if (systemsRequiringVplex.contains(storageSystem.getSystemType())) {
				if (!VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
					invalidPools.add(currentPool);
				}				
			}
		}
		storagePools.removeAll(invalidPools);
		if (storagePools == null || storagePools.isEmpty()) {
        	throw APIException.badRequests.storagePoolsRequireVplexForProtection(personality, vpool.getLabel());
        }
		return storagePools;		
	}   
        
    /**
    * For debug purpose, which lists the matched Pool URIs
    * @param pools
    * @return
    */
   protected List<URI> getURIsFromPools(List<StoragePool> pools) {
       List<URI> poolURIList = new ArrayList<URI>();
       for (StoragePool pool : pools) {
           poolURIList.add(pool.getId());
       }
       return poolURIList;
   }

   /**
    * 
    * @param srcVpool
    * @param srcVarray
    * @param project
    * @param srcVpoolCapabilities
    * @param vplexPoolMapForSrcVarray
    * @return
    */
   private Recommendation findVPlexHARecommendations(VirtualArray srcVarray, VirtualPool srcVpool, 
                                                               VirtualArray haVarray, VirtualPool haVpool, 
                                                               Project project, 
                                                               VirtualPoolCapabilityValuesWrapper srcVpoolCapabilities, 
                                                               Map<String, List<StoragePool>> vplexPoolMapForSrcVarray) {
       List<Recommendation> vplexHaVArrayRecommendations = null;
       
       if (haVarray == null) {
           haVarray = vplexScheduler.getHaVirtualArray(srcVarray, project, srcVpool);
       }
       if (haVpool == null) {              
           haVpool = vplexScheduler.getHaVirtualPool(srcVarray, project, srcVpool);
       }
           
       vplexHaVArrayRecommendations = getAllHARecommendations(
               srcVarray, srcVpool, 
               haVarray, haVpool, 
               srcVpoolCapabilities, 
               vplexPoolMapForSrcVarray);  
                           
       //There is only one recommendation ever, return the first recommendation.
       return vplexHaVArrayRecommendations.get(0);
   }

   /**
    * Gets all the HA placement recommendations.
    * 
    * @param srcVarray The source virtual array
    * @param srcVpool The source virtual pool
    * @param requestedHaVarray The requested highly available virtual array
    * @param haVpool The highly available virtual pool
    * @param capabilities The virtual pool capabilities
    * @param vplexPoolMapForSrcVarray The source virtual array, VPlex connected storage pools
    * @param srcStorageSystem The selected VPlex source leg storage system
    * @param isRpTarget true if the request is specific to a RecoverPoint target, false otherwise
    * 
    * @return A list of VPlexRecommendation instances specifying the
    *         HA recommended resource placement resources
    */
   protected List<Recommendation> getAllHARecommendations(VirtualArray srcVarray,
           VirtualPool srcVpool, VirtualArray requestedHaVarray, VirtualPool haVpool,
           VirtualPoolCapabilityValuesWrapper capabilities, 
           Map<String, List<StoragePool>> vplexPoolMapForSrcVarray) {
           
       _log.info("Executing VPlex high availability placement strategy");

       // Initialize the list of recommendations.
       List<Recommendation> recommendations = new ArrayList<Recommendation>();
       
       // The list of potential VPlex storage systems.
       Set<String> vplexStorageSystemIds = vplexPoolMapForSrcVarray.keySet();        
       _log.info("{} VPlex storage systems have matching pools",
               vplexStorageSystemIds.size());

       // For an HA request, get the possible high availability varrays
       // for each potential VPlex storage system.
       Map<String, List<String>> vplexHaVarrayMap = ConnectivityUtil.getVPlexVarrays(
                   dbClient, vplexStorageSystemIds, srcVarray.getId());

       // Note that both the high availability VirtualArray and VirtualPool are optional. 
       // When not specified, the high availability VirtualArray will be selected by the placement
       // logic. If no VirtualPool is specified for the HA VirtualArray, then the
       // passed VirtualPool is use.
       if (haVpool == null) {
           haVpool = srcVpool;
       }
               
       _log.info("Requested HA varray is {}", (requestedHaVarray != null ? requestedHaVarray.getId()
           : "not specified"));
       
       // Loop over the potential VPlex storage systems, and attempt
       // to place the resources.
       Iterator<String> vplexSystemIdsIter = vplexStorageSystemIds.iterator();
       while (vplexSystemIdsIter.hasNext()) {
           String vplexStorageSystemId = vplexSystemIdsIter.next();
           _log.info("Check matching pools for VPlex {}", vplexStorageSystemId);

           // Check if the resource can be placed on the matching
           // pools for this VPlex storage system.
           if(VirtualPool.ProvisioningType.Thin.toString()
                   .equalsIgnoreCase(srcVpool.getSupportedProvisioningType())) {
              capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
           }
               
           // Otherwise we now have to see if there is an HA varray
           // for the VPlex that also contains pools suitable to place 
           // the resources.
           List<String> vplexHaVarrayIds = vplexHaVarrayMap.get(vplexStorageSystemId);
           _log.info("Found {} HA varrays", vplexHaVarrayIds.size());
           for (String vplexHaVarrayId : vplexHaVarrayIds) {
               _log.info("Check HA varray {}", vplexHaVarrayId);
               // If a specific HA varray was specified and this
               // varray is not it, then skip the varray.
               if ((requestedHaVarray != null)
                   && (!vplexHaVarrayId.equals(requestedHaVarray.getId().toString()))) {
                   _log.info("Not the requested HA varray, skip");
                   continue;
               }

               // Get all storage pools that match the passed VirtualPool params,
               // and this HA VirtualArray. In addition, the
               // pool must have enough capacity to hold at least one
               // resource of the requested size.
               VirtualArray vplexHaVarray = dbClient.queryObject(VirtualArray.class,
                   URI.create(vplexHaVarrayId));
               
               List<StoragePool> allMatchingPoolsForHaVarray = vplexScheduler.getMatchingPools(
               		vplexHaVarray, null, haVpool, capabilities);
               _log.info("Found {} matching pools for HA varray", allMatchingPoolsForHaVarray.size());
               
               // Now from the list of candidate pools, we only want pools
               // on storage systems that are connected to the VPlex
               // storage system. We find these storage pools and associate
               // them to the VPlex storage systems to which their storage
               // system is connected.
               Map<String, List<StoragePool>> vplexPoolMapForHaVarray = 
                   vplexScheduler.sortPoolsByVPlexStorageSystem(allMatchingPoolsForHaVarray, vplexHaVarrayId);

               // If the HA varray has candidate pools for this
               // VPlex, see if the candidate pools in this HA
               // varray are sufficient to place the resources.
               List<Recommendation> recommendationsForHaVarray = new ArrayList<Recommendation>();
               if (vplexPoolMapForHaVarray.containsKey(vplexStorageSystemId)) {
                   _log.info("Found matching pools in HA varray for VPlex {}",
                       vplexStorageSystemId);
                   if (VirtualPool.ProvisioningType.Thin.toString()
                           .equalsIgnoreCase(haVpool.getSupportedProvisioningType())) {
                       capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
                   }                   
				recommendationsForHaVarray = blockScheduler.getRecommendationsForPools(vplexHaVarray.getId().toString(),
                           vplexPoolMapForHaVarray.get(vplexStorageSystemId), capabilities);
               } else {
                   _log.info("No matching pools in HA varray for VPlex {}",
                       vplexStorageSystemId);
               }

               // We have recommendations for pools in both
               // the source and HA varrays.
               if (!recommendationsForHaVarray.isEmpty()) {
                   _log.info("Matching pools in HA varray sufficient for placement.");
                   recommendations.addAll(vplexScheduler.createVPlexRecommendations(
                       vplexStorageSystemId, vplexHaVarray, haVpool, recommendationsForHaVarray));
               }

               // If we found recommendations for this HA varray
               // or we did not, but the user specifically requested a
               // HA varray, then we are done checking the HA
               // varrays for this VPlex.
               if (!recommendations.isEmpty() || (requestedHaVarray != null)) {
                   _log.info("Done trying to place resource for VPlex.");
                   break;
               }
           }
       }
       return recommendations;
   }
   
   /**
    * Utility method used to convert an RPProtectionRecommendation to a VPlexProteciontRecommendation.
    * 
    * @param rpRec the RPProtectionRecommendation to convert.
    * @param varray the VirtualArray.
    * @param vpool the VirtualPool.
    * @param haVpool the HA VirtualPool. This param can be null if there is no HA vpool specified.
    * @return the converted recommendation.
    */
   
   /* 
   private RPProtectionRecommendation convertRPProtectionRecommendation(RPProtectionRecommendation protectionRecommendation, 
		   								VirtualArray varray, VirtualPool vpool, VirtualPool haVpool) {
       RPProtectionRecommendation pRec = new RPProtectionRecommendation();
       RPRecommendation rpRec = new RPRecommendation();
     
       pRec.setSourceInternalSiteName(protectionRecommendation.getInternalSiteName());
       pRec.setProtectionDevice(protectionRecommendation.getProtectionDevice());
       pRec.setVpoolChangeVolume(protectionRecommendation.getVpoolChangeVolume());
       pRec.setVpoolChangeVpool(protectionRecommendation.getVpoolChangeVpool());
       pRec.setVpoolChangeProtectionAlreadyExists(protectionRecommendation.isVpoolChangeProtectionAlreadyExists());
       pRec.setResourceCount(protectionRecommendation.getResourceCount());
       
       pRec.setSourceJournalRecommendation(protectionRecommendation.getSourceJournalRecommendation());
       
       if (protectionRecommendation.getStandbyJournalRecommendation() != null) {
    	   pRec.setStandbyJournalRecommendation(protectionRecommendation.getStandbyJournalRecommendation());
       }
       
       for (RPRecommendation rpRecommendation : protectionRecommendation.getRpRecommendations()) {    	             
    	   rpRec.setSourceDevice(rpRecommendation.getSourceDevice());
    	   rpRec.setSourcePool(rpRecommendation.getSourcePool());
    	   rpRec.setVirtualArray(rpRecommendation.getVirtualArray());
    	   if (haVpool != null) {
    	       	rpRec.setVirtualPool(haVpool);
	       } else {
    	       	rpRec.setVirtualPool(vpool);
	       }
    	
//    	   if (rpRecommendation.getVPlexStorageSystem() != null) {
//    		   rpRec.setVPlexStorageSystem(protectionRecommendation.getSourceInternalSiteStorageSystem());
//    	   }
    	   rpRec.setSourceVPlexHaRecommendations(rpRecommendation.getSourceVPlexHaRecommendations());    	   
    	   rpRec.setRpSiteAssociateStorageSystem(rpRecommendation.getRpSiteAssociateStorageSystem());    	       	  
    	   rpRec.setVarrayProtectionMap(rpRecommendation.getVarrayProtectionMap());  
    	   
    	   pRec.getRpRecommendations().add(rpRec);
       }
      
       return pRec;
   } */
   
   /**
    * Creates recommendations for MetroPoint.  This consists of single recommendations that include
    * the both the primary and secondary HA clusters and their associated RP protection details.
    * 
    * @param srcVarray the source virtual array.
    * @param tgtVarrays the target protection virtual arrays.
    * @param srcVpool the source virtual pool.
    * @param haVarray the HA (second cluster) virtual array.
    * @param haVpool the HA (second cluster) virtual array.
    * @param project the project.
    * @param capabilities the capability params.
    * @param candidatePrimaryPools candidate source pools to use for the primary cluster.
    * @param candidateSecondaryPools candidate source pools to use for the primary cluster.
    * @return list of Recommendation objects to satisfy the request
    */
   private List<Recommendation> createMetroPointRecommendations(VirtualArray srcVarray, List<VirtualArray> tgtVarrays, VirtualPool srcVpool,
   		VirtualArray haVarray, VirtualPool haVpool, Project project, VirtualPoolCapabilityValuesWrapper capabilities, 
   		List<StoragePool> candidatePrimaryPools, List<StoragePool> candidateSecondaryPools, Volume vpoolChangeVolume) {
       // Initialize a list of recommendations to be returned.
       List<Recommendation> recommendations = new ArrayList<Recommendation>();
       RPProtectionRecommendation rpProtectionRecommendaton = null;
           
       // Get all the matching pools for each target virtual array.  If the target varray's
       // vpool specifies HA, we will only look for VPLEX connected storage pools.
       Map<VirtualArray, List<StoragePool>> tgtVarrayStoragePoolsMap = getTargetMatchingPools(tgtVarrays,
              srcVpool, project, capabilities, vpoolChangeVolume);
       
        rpProtectionRecommendaton = createMetroPointRecommendations(srcVarray, tgtVarrays, srcVpool, haVarray, haVpool, 
                                                                   capabilities, candidatePrimaryPools, candidateSecondaryPools, 
                                                                   tgtVarrayStoragePoolsMap, 
                                                                   vpoolChangeVolume, project);
       
       _log.info("Produced {} recommendations for MetroPoint placement.", rpProtectionRecommendaton.getResourceCount());           
       recommendations.add(rpProtectionRecommendaton);
       
       return recommendations;
   }    
  
   
   /**
    * Scheduler for a Vpool change from an unprotected VPLEX Virtual volume to a RP+VPLEX protected Virtual volume.
    *
    * @param changeVpoolVolume volume that is being changed to a protected vpool
    * @param newVpool vpool requested to change to (must be protected)
    * @param protectionVarrays Varrays to protect this volume to.
    * @return list of Recommendation objects to satisfy the request
    */
   public List<Recommendation> scheduleStorageForVpoolChangeUnprotected(Volume changeVpoolVolume, VirtualPool newVpool,
       List<VirtualArray> protectionVarrays) {
       _log.info("Schedule storage for vpool change to vpool {} for volume {}.", 
                   newVpool.getLabel() + "[" + String.valueOf(newVpool.getId()) + "]", 
                   changeVpoolVolume.getLabel() + "[" + String.valueOf(changeVpoolVolume.getId()) + "]");                         
       this.initResources();
       
       VirtualArray varray = dbClient.queryObject(VirtualArray.class, changeVpoolVolume.getVirtualArray());

       // Swap src and ha if the flag has been set on the vpool
       RPVPlexVarrayVpool container = this.swapSrcAndHAIfNeeded(varray, newVpool);
               
       CapacityMatcher capacityMatcher = new CapacityMatcher();
       Project project = dbClient.queryObject(Project.class, changeVpoolVolume.getProject());        
       VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
       capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, changeVpoolVolume.getCapacity());
       capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
     
       capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, changeVpoolVolume.getConsistencyGroup());
       
       List<StoragePool> allMatchingPools = getCandidatePools(varray, newVpool, null, null, project, capabilities, RPHelper.SOURCE);
               
       List<StoragePool> sourcePools = new ArrayList<StoragePool>();
       
       // Find out how much space we'll need to fit the Journals for the source
       long journalSize = RPHelper.getJournalSizeGivenPolicy(String.valueOf(changeVpoolVolume.getCapacity()), container.getSrcVpool().getJournalSize(), capabilities.getResourceCount());

       // Make sure our pool is in this list; this is a check to ensure the pool is in our existing varray and new Vpool
       // and can fit the new sizes needed.  If it can not, send down pools that can.
       Iterator<StoragePool> iter = allMatchingPools.iterator();
       while (iter.hasNext()) {
           StoragePool pool = (StoragePool)iter.next();
           if (pool.getId().equals(changeVpoolVolume.getPool())) {
               // Make sure there's enough space for this journal volume in the current pool; it's preferred to use it.
               if (capacityMatcher.poolMatchesCapacity(pool, journalSize, journalSize, false, false, null)) {
                   sourcePools.add(pool);
                   break;
               } else {
                   _log.warn(String.format("Not enough capacity found to place RecoverPoint journal volume on pool: %s, searching for other less preferable pools.", pool.getNativeGuid()));
                   break;
               }
           }
       }

       if (sourcePools.isEmpty()) {
           // Fall-back: if the existing source pool couldn't be used, let's find a different pool.
           sourcePools.addAll(allMatchingPools);

           if (sourcePools.isEmpty()) {
               // We could not verify the source pool exists in the CoS, return appropriate error
               _log.error("No matching storage pools found for the source varray: {0}. There are no storage pools that " +
                           "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to " +
                           "hold at least one resource of the requested size.", container.getSrcVarray().getLabel());
               throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(container.getSrcVpool().getId(), container.getSrcVarray().getId());              
           }
       }

       // Schedule storage based on the determined storage pools.
       List<Recommendation> recommendations = scheduleStorageSourcePoolConstraint(container.getSrcVarray(), protectionVarrays, container.getSrcVpool(), 
               capabilities, sourcePools, project, changeVpoolVolume, null);
                   
       logRecommendations(recommendations);
       return recommendations;
   }

	private void logRecommendations(List<Recommendation> recommendations) {
		if (recommendations != null && !recommendations.isEmpty()) {
	       	_log.info("Created VPlex Protection recommendations:\n");
	       	for (Recommendation rec : recommendations) {
	       		RPProtectionRecommendation protectionRec = (RPProtectionRecommendation) rec;
	       		_log.info(protectionRec.toString(dbClient));        		
	       		}
       	}
	}
       
   /**
    * Gather matching pools for a collection of protection varrays. Collects 
    * a list of vplex connected storage pools if the protection virtual pool
    * specifies high availability. 
    *
    * @param tgtVarrays The protection varrays
    * @param srcVpool the requested vpool that must be satisfied by the storage pool
    * @param srcVpoolCapabilities capabilities
    * @param vpoolChangeVolume The main volume for the change vpool operation 
    * @return A list of matching storage pools and varray mapping
    */
   private Map<VirtualArray, List<StoragePool>> getTargetMatchingPools(List<VirtualArray> tgtVarrays,
           VirtualPool srcVpool, Project project, VirtualPoolCapabilityValuesWrapper srcVpoolCapabilities, Volume vpoolChangeVolume) {
       _log.info("Getting a list of pools matching each protection Virtual Array.");
       
       Map<VirtualArray, List<StoragePool>> tgtVarrayStoragePoolMap = new HashMap<VirtualArray, List<StoragePool>>();
       VirtualPool tgtVpool = null;
              
       for (VirtualArray tgtVarray : tgtVarrays) {
           tgtVpool = getTargetVirtualPool(tgtVarray, srcVpool);
           
           List<StoragePool> tgtVarrayMatchingPools = new ArrayList<StoragePool>();
           
           // Check to see if this is a change vpool request for an existing RP+VPLEX/MetroPoint protected volume.
           // If it is, we want to isolate already provisioned targets to the single storage pool that they are already in.
           if (vpoolChangeVolume != null) {   
              Volume alreadyProvisionedTarget = RPHelper.findAlreadyProvisionedTargetVolume(vpoolChangeVolume, tgtVarray.getId(), dbClient);
              if (alreadyProvisionedTarget != null) {           
                  _log.info(String.format("Existing target volume [%s] found for varray [%s].", alreadyProvisionedTarget.getLabel(), tgtVarray.getLabel()));
                  
                  URI storagePoolURI = null;
                  if (alreadyProvisionedTarget.getAssociatedVolumes() != null 
                          && !alreadyProvisionedTarget.getAssociatedVolumes().isEmpty()) {
                      Volume sourceBackingVol = VPlexUtil.getVPLEXBackendVolume(alreadyProvisionedTarget, true, dbClient, true);
                      storagePoolURI = sourceBackingVol.getPool();
                  }
                  else {
                      storagePoolURI = alreadyProvisionedTarget.getPool();   
                  }
                  
                  // Add the single existing storage pool for this varray
                  StoragePool storagePool = dbClient.queryObject(StoragePool.class, storagePoolURI);
                  tgtVarrayMatchingPools.add(storagePool);
                  tgtVarrayStoragePoolMap.put(tgtVarray, tgtVarrayMatchingPools);

                  // No need to go further, continue on to the next target varray
                  continue;
              }
           }         
           
           tgtVarrayMatchingPools = getCandidatePools(tgtVarray, tgtVpool, null, null, project, srcVpoolCapabilities, RPHelper.TARGET);                     
           
           _log.info("Matched pools for target virtual array {} and target virtual pool {}:\n", 
                   tgtVarray.getLabel(), tgtVpool.getLabel());
           printSortedStoragePools(tgtVarrayMatchingPools);            
           
           if (VirtualPool.vPoolSpecifiesHighAvailability(tgtVpool)) {
               
               // Get all the VPLEX connected storage pools from the matched pools
               Map<String, List<StoragePool>> sortedTargetVPlexStoragePools = 
                       vplexScheduler.sortPoolsByVPlexStorageSystem(tgtVarrayMatchingPools, String.valueOf(tgtVarray.getId()));
               
               // We only care about RP-connected VPLEX storage systems
               sortedTargetVPlexStoragePools = getRPConnectedVPlexStoragePools(sortedTargetVPlexStoragePools);
          
               if (sortedTargetVPlexStoragePools != null && !sortedTargetVPlexStoragePools.isEmpty()) {
                   // Add the protection virtual array and list of VPLEX connected storage pools
                   tgtVarrayStoragePoolMap.put(
                           tgtVarray, sortedTargetVPlexStoragePools.get(sortedTargetVPlexStoragePools.keySet().iterator().next()));
               }
               else {                  
                   // There are no RP connected VPLEX storage systems so we cannot provide
                   // any placement recommendations for the target.
                   _log.error("No matching pools because there are no RP connected VPlex storage systems for the requested virtual array[{}] and virtual pool[{}].", tgtVarray.getLabel(), tgtVpool.getLabel());
                   throw APIException.badRequests.noRPConnectedVPlexStorageSystemsForTarget(tgtVpool.getLabel(), tgtVarray.getLabel());
               }
               
               tgtVarrayHasHaVpool.put(tgtVarray, true);
               
               // If the target vpool specifies VPlex, we need to check if this is VPLEX local or VPLEX
               // distributed. If it's VPLEX distributed, there will be a separate recommendation just for that
               // which will be used by VPlexBlockApiService to create the distributed volumes in VPLEX.
               if (tgtVpool != null) {
                   boolean isVplexDistributed = VirtualPool.HighAvailabilityType.vplex_distributed.name()
                           .equals(tgtVpool.getHighAvailability());
                   
                   if (isVplexDistributed) {
                       this.tgtHaRecommendation.put(tgtVarray.getId(), 
                               findVPlexHARecommendations(tgtVarray, tgtVpool, null, null, project, srcVpoolCapabilities, sortedTargetVPlexStoragePools));
                   }
               }
           } 
           else {
               tgtVarrayStoragePoolMap.put(tgtVarray,  tgtVarrayMatchingPools);
               tgtVarrayHasHaVpool.put(tgtVarray, false);
           }
       }

       return tgtVarrayStoragePoolMap;
   }
   
   /**
    * Filters out all the non-RP connected storage pools from the passed
    * vplex to storage pool map.
    * 
    * @param vplexStoragePoolMap the mapping of vplex storage systems to storage pools.
    * @return a map of VPLEX to StoragePool
    */
   private Map<String, List<StoragePool>> getRPConnectedVPlexStoragePools(Map<String, List<StoragePool>> vplexStoragePoolMap) {       
       Map<String, List<StoragePool>> poolsToReturn = vplexStoragePoolMap;       
       if (vplexStoragePoolMap != null ) {
           // Narrow down the list of candidate VPLEX storage systems/pools to those
           // that are RP connected.
           Set<String> vplexStorageSystemIds = vplexStoragePoolMap.keySet();
           _log.info("{} VPlex storage systems have matching pools",
                   vplexStorageSystemIds.size());
               
           URIQueryResultList results = new URIQueryResultList();
           boolean rpConnection = false;
               
           for (String vplexId : vplexStorageSystemIds) {
               rpConnection = false;
               dbClient.queryByConstraint(
                       AlternateIdConstraint.Factory.
                       getRPSiteArrayByStorageSystemConstraint(vplexId),
                       results);
               while (results.iterator().hasNext()) {
                   URI uri = results.iterator().next();
                   RPSiteArray siteArray = dbClient.queryObject(RPSiteArray.class, uri);
                   if (siteArray != null) {
                       // There is at least 1 RP connection to this VPLEX
                       rpConnection = true;
                       break;
                   }
               } 
                   
               if (!rpConnection) {
                   poolsToReturn.remove(vplexId);
               }
           }
       }       
       return poolsToReturn;
   }

   /**
    * Gets the virtual pool associated with the virtual array.
    * 
    * @param tgtVarray
    * @param srcVpool the base virtual pool
    * @return
    */
   private VirtualPool getTargetVirtualPool(VirtualArray tgtVarray, VirtualPool srcVpool) {
       VpoolProtectionVarraySettings settings = rpHelper.getProtectionSettings(srcVpool, tgtVarray);
       // If there was no vpool specified use the source vpool for this varray.
       VirtualPool tgtVpool = srcVpool;
       if (settings.getVirtualPool() != null) {
           tgtVpool = dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
       }       
       return tgtVpool;
   }
   
   /**
    * Convert the HA recommendations of type VPlexRecommendation to
    * VPlexProtectionRecommendation.
    * 
    * @param vplexRecommendations
    * @return
    */
   private List<Recommendation> convertHARecommendations(List<Recommendation> vplexRecommendations) {
       List<Recommendation> vPlexProtectionRecommendations =
               new ArrayList<Recommendation>();
       
       for (Recommendation recommendation : vplexRecommendations) {
           // All HA recommendations are of type VPlexRecommendation but just in case
           if (recommendation instanceof VPlexRecommendation) {
               vPlexProtectionRecommendations.add(
                       new RPProtectionRecommendation((RPProtectionRecommendation)recommendation));
           }
       }       
       return vPlexProtectionRecommendations;
   }
   
   /**
    * This function will swap src and ha varrays and src and ha vpools IF 
    * the src vpool has specified this.
    * 
    * @param srcVarray
    * @param srcVpool
    * @param haVarray
    * @param haVpool
    * @param dbClient
    */
   public static RPVPlexVarrayVpool setActiveProtectionAtHAVarray(RPVPlexVarrayVpool varrayVpool, DbClient dbClient) {                
       // Refresh vpools in case previous activities have changed their temporal representation.
       VirtualArray srcVarray = varrayVpool.getSrcVarray();
       VirtualPool srcVpool = dbClient.queryObject(VirtualPool.class, varrayVpool.getSrcVpool().getId());
       VirtualArray haVarray = varrayVpool.getHaVarray(); 
       VirtualPool haVpool = varrayVpool.getHaVpool();
               
       // Check to see if the user has selected that the HA Varray should be used
       // as the RP Source.               
       if (VirtualPool.isRPVPlexProtectHASide(srcVpool)) {                                  
           
           haVarray = dbClient.queryObject(VirtualArray.class, 
                               URI.create(srcVpool.getHaVarrayConnectedToRp()));
           
           _log.info("Source Vpool[{}] indicates that we should use HA Varray[{}] as RP Source.", srcVpool.getLabel(), haVarray.getLabel());   
                           
           String haVpoolId = srcVpool.getHaVarrayVpoolMap().get(srcVpool.getHaVarrayConnectedToRp());
           
           if (haVpoolId != null 
                   && !haVpoolId.isEmpty()
                   && !haVpoolId.equals(NullColumnValueGetter.getNullStr())) {                 
               haVpool = dbClient.queryObject(VirtualPool.class, 
                                               URI.create(haVpoolId));
               _log.info("HA Vpool has been defined [{}]", haVpool.getLabel());
               
               // Temporarily inherit the HA and Protection Settings/RP specific info from Source Vpool.
               // These modifications will not be persisted to the db.
               haVpool.setProtectionVarraySettings(srcVpool.getProtectionVarraySettings());
               haVpool.setRpCopyMode(srcVpool.getRpCopyMode());
               haVpool.setRpRpoType(srcVpool.getRpRpoType());
               haVpool.setRpRpoValue(srcVpool.getRpRpoValue());    
               haVpool.setMultivolumeConsistency(srcVpool.getMultivolumeConsistency());
               haVpool.setHighAvailability(srcVpool.getHighAvailability());
               haVpool.setMetroPoint(srcVpool.getMetroPoint());
               haVpool.setHaVarrayConnectedToRp(srcVarray.getId().toString());                 
               haVpool.setJournalSize(NullColumnValueGetter.isNotNullValue(srcVpool.getJournalSize()) ? srcVpool.getJournalSize() : null);            
           }
           else {
               _log.info("HA Vpool has not been defined, using Source Vpool[{}].", srcVpool.getLabel());                   
               // Use source vpool. That means the source vpool will have to have the HA varray
               // added to it, otherwise this will not work. That is done during vpool create via the UI
               // by the user.
               // This is the same behaviour as VPLEX. So we're just reusing that behaviour.
               // If not done, placement will fail.
               haVpool = srcVpool;
           }
           
           // Ensure that we define the haVarrayVpoolMap on the haVpool to use srcVarray and srcVpool.
           StringMap haVarrayVpoolMap = new StringMap();
           haVarrayVpoolMap.put(srcVarray.getId().toString(), srcVpool.getId().toString());                    
           haVpool.setHaVarrayVpoolMap(haVarrayVpoolMap);
                                   
           _log.info("HA Varray[{}] and HA Vpool[{}] will be used as Source Varray and Source Vpool.", haVarray.getLabel(), haVpool.getLabel());
           _log.info("Source Varray[{}] and Source Vpool[{}] will be used as HA Varray and HA Vpool.", srcVarray.getLabel(), srcVpool.getLabel());
           
           // Now HA becomes Source and Source becomes HA.
           VirtualArray tempVarray = srcVarray;
           VirtualPool tempVpool = srcVpool;
           
           varrayVpool.setSrcVarray(haVarray);
           varrayVpool.setSrcVpool(haVpool);
           
           varrayVpool.setHaVarray(tempVarray);
           varrayVpool.setHaVpool(tempVpool);          
       }
       
       return varrayVpool;
   }
   
  
   
   /**
    * Scheduler for a Vpool change from a protected VPLEX Virtual volume to a different type
    * pf protection. Ex: RP+VPLEX upgrade to MetroPoint
    *
    * @param volume volume that is being changed to a protected vpool
    * @param newVpool vpool requested to change to (must be protected)
    * @param protectionVarrays Varrays to protect this volume to.
    * @param vpoolChangeParam The change param for the vpool change operation
    * @return list of Recommendation objects to satisfy the request
    */
   public List<Recommendation> scheduleStorageForVpoolChangeProtected(Volume volume, VirtualPool newVpool,
                                                                       List<VirtualArray> protectionVirtualArraysForVirtualPool) {
       _log.info("Schedule storage for vpool change to vpool {} for volume {}.", 
               newVpool.getLabel() + "[" + String.valueOf(newVpool.getId()) + "]", 
               volume.getLabel() + "[" + String.valueOf(volume.getId()) + "]");
       
       VirtualPool currentVpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
               
       this.initResources();
       
       VirtualArray varray = dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());

       // Swap src and ha if the flag has been set on the vpool
       RPVPlexVarrayVpool container = this.swapSrcAndHAIfNeeded(varray, newVpool);
               
       CapacityMatcher capacityMatcher = new CapacityMatcher();
       Project project = dbClient.queryObject(Project.class, volume.getProject());        
       VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
       capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, volume.getCapacity());
       capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
       capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, volume.getConsistencyGroup());
                               
       List<StoragePool> sourcePools = new ArrayList<StoragePool>();
       List<StoragePool> haPools = new ArrayList<StoragePool>();
               
       VirtualArray haVarray = vplexScheduler.getHaVirtualArray(container.getSrcVarray(), project, container.getSrcVpool());
       VirtualPool haVpool = vplexScheduler.getHaVirtualPool(container.getSrcVarray(), project, container.getSrcVpool());        
              
       // Recommendations to return
       List<Recommendation> recommendations = Lists.newArrayList();
       
       // Upgrade RP+VPLEX to MetroPoint
       if (VirtualPool.vPoolSpecifiesRPVPlex(currentVpool)
               && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {        
           // We already have our VPLEX Metro source and targets provisioned.
           // We're going to leverage this for placement.
           _log.info("Scheduling storage for upgrade to MetroPoint, we need to place a HA/Stand-by/Secondary Journal");
           
           // Get a handle on the existing source and ha volumes, we want to use the references to their 
           // existing storage pools to pass to the RP Scheduler.
           Volume sourceBackingVolume = null;
           Volume haBackingVolume = null;            
           for (String associatedVolumeId : volume.getAssociatedVolumes()) {
               URI associatedVolumeURI = URI.create(associatedVolumeId);
               Volume backingVolume = dbClient.queryObject(Volume.class, associatedVolumeURI);
               if (backingVolume.getVirtualArray().equals(volume.getVirtualArray())) {
                   sourceBackingVolume = backingVolume;
               }
               else {
                   haBackingVolume = backingVolume;
               }    
           }
   
           // We already have a source vpool from the (the existing one), so just add that one only to the list.
           sourcePools.add(dbClient.queryObject(StoragePool.class, sourceBackingVolume.getPool()));
           haPools.add(dbClient.queryObject(StoragePool.class, haBackingVolume.getPool()));
           
           // Obtain a list of RP protection Virtual Arrays.
           List<VirtualArray> tgtVarrays = 
                   RecoverPointScheduler.getProtectionVirtualArraysForVirtualPool(
                           project, container.getSrcVpool(), dbClient, _permissionsHelper);
   
           recommendations = createMetroPointRecommendations(container.getSrcVarray(), tgtVarrays, container.getSrcVpool(), haVarray, 
                                                               haVpool, project, capabilities, sourcePools, haPools, 
                                                               volume);            
       }
       
       logRecommendations(recommendations);       
       return recommendations;
   }
		
	/**
	 * Checks if the existing volume's storage pool is in either the assigned or
	 * matched storage pools of the virtual pool being used in the current volume request
	 * @param vpool - virtual pool being used in the current volume request
	 * @param existingVolumeStoragePool - the existing volume's storage pool
	 * @return true or false depending whether the storage pool is in either list 
	 */
	private boolean verifyStoragePoolAvailability (VirtualPool vpool, URI existingVolumeStoragePool) {		
		List<StoragePool> pools = VirtualPool.getValidStoragePools(vpool, dbClient, true);
		if (!pools.isEmpty()) {
			for (StoragePool pool : pools) {
				if (pool.getId().equals(existingVolumeStoragePool)) {
					return true;
				}
			}
		}		
		return false;
	}
	
	/** Based on the current volume request's virtual pool, determine the protection settings and use them to determine
	 *  the protection virtual arrays and the associated protection virtual pool.  Pass the protection virtual array
	 *  along with the existing target volume to determine if the storage pools align
	 * @param targetVolume - existing target volume
	 * @param vpool - virtual pool being used in the current volume request 
	 * @return true or false depending whether the existing target volume's storage pool is available to the current virtual pool of the request
	 */
	private boolean verifyTargetStoragePoolAvailability(Volume targetVolume, VirtualPool vpool) {		        
		if (vpool.getProtectionVarraySettings() != null && !vpool.getProtectionVarraySettings().isEmpty()) {		
			String settingsURI = vpool.getProtectionVarraySettings().get(targetVolume.getVirtualArray().toString());
			VpoolProtectionVarraySettings settings = dbClient.queryObject(VpoolProtectionVarraySettings.class, URI.create(settingsURI));
			// If there was no vpool specified with the protection settings, use the base vpool for the new volume request
			URI protectionVpoolId = vpool.getId();
			if (settings.getVirtualPool()!=null) {
				protectionVpoolId = settings.getVirtualPool();
			}
			VirtualPool protectionVpool = dbClient.queryObject(VirtualPool.class, protectionVpoolId);
			if (verifyStoragePoolAvailability(protectionVpool, targetVolume.getPool())) {
				return true;
			}     					    			    		
    	} 
		return false;
	}

	/** Determine if the protection storage pools used in an existing volume's 
	 *  creation request are available to the current request
	 * @param srcVolume - existing source volume to examine storage pools for 
	 * @param vpool - virtual pool for the current volume creation request
	 * @param cgName - consistency group name of the current volume creation request
	 * @return true or false depending whether the storage pools are available
	 */
	private boolean verifyExistingSourceProtectionPools(Volume srcVolume, VirtualPool vpool, String cgName) {
    	// Check if the storage pools used by the existing source and its journal are available in the current vpool
    	if (!verifyStoragePoolAvailability(vpool, srcVolume.getPool())) {
    		_log.warn("Unable to fully align placement with existing volumes in RecoverPoint consistency group {}.  " +
                    "The storage pool {} used by an existing source volume cannot be used.", cgName, srcVolume.getPool());
    		return false;
    	} else if (!verifyStoragePoolAvailability(vpool, dbClient.queryObject(Volume.class, srcVolume.getRpJournalVolume()).getPool())) {
    		_log.warn("Unable to fully align placement with existing volumes in RecoverPoint consistency group {}.  " +
                    "The storage pool {} used by an existing source journal volume cannot be used.", cgName,dbClient.queryObject(Volume.class, srcVolume.getRpJournalVolume()).getPool());
    		return false;
    	}
		
    	// Check if the storage pools used by the existing source RP targets and their journals are available in the current vpool
    	Iterator<String> targetVolumes = srcVolume.getRpTargets().iterator();
    	while (targetVolumes.hasNext()) {
    		Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(targetVolumes.next()));
    		if (!verifyTargetStoragePoolAvailability(targetVolume, vpool)) {
    			_log.warn("Unable to fully align placement with existing volumes in RecoverPoint consistency group {}.  " +
                        "The storage pool {} used by an existing target volumes cannot be used.", cgName, targetVolume.getPool());
        		return false;
    		}
    		
    		Volume targetJournal = dbClient.queryObject(Volume.class, targetVolume.getRpJournalVolume());
    		if (!verifyTargetStoragePoolAvailability(targetJournal, vpool)) {
    			_log.warn("Unable to fully align placement with existing volumes in RecoverPoint consistency group {}.  " +
                        "The storage pool {} used by an existing target journal volume cannot be used.", cgName, dbClient.queryObject(Volume.class, targetVolume.getRpJournalVolume()).getPool());
        		return false;
    		}
    	}
		return true;
	}
	
	protected List<Recommendation> buildCgRecommendations(
	        VirtualPoolCapabilityValuesWrapper capabilities, VirtualPool vpool, List<VirtualArray> protectionVarrays,
	        Volume vpoolChangeVolume) {
	    BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
	    _log.info("Attempting to align placement (protection system, storage pools, internal site names) with " +
	    		"existing volumes in RecoverPoint consistency group {}.", cg.getLabel());
        
	    List<Recommendation> recommendations = new ArrayList<Recommendation>();        
        
        // Find the first existing source volume
        List<Volume> sourceVolumes = rpHelper.getCgVolumes(capabilities.getBlockConsistencyGroup(), Volume.PersonalityTypes.SOURCE.toString());
        
        
        if (sourceVolumes.isEmpty()) {
            _log.info("Unable to fully align placement with existing volumes in RecoverPoint consistency group {}.  " +
                    "The consistency group currently contains no volumes.", cg.getLabel());
            return recommendations;
        }
        
        // Verify that all the underlying protection storage pools used by the existing source volume are available to this request
        if (!verifyExistingSourceProtectionPools(sourceVolumes.get(0), vpool, cg.getLabel())) {
        	return recommendations;
        }
        
        Volume sourceVolume = null;
        boolean createRecommendations = false;
        for (Volume currentSourceVolume : sourceVolumes) {        	        	        	        	
            // For each source volume, check the storage pool capacity for each of the pools 
            // corresponding to the source, targets, and journals.  If we find a source
            // volume who's corresponding volumes (source, targets, journals) use pools with
            // enough capacity, use it to produce the recommendation.
            if (cgPoolsHaveAvailableCapacity(currentSourceVolume, capabilities, vpool, protectionVarrays)) {
                createRecommendations = true;
                sourceVolume = currentSourceVolume;
                break;
            }
        }

        if (!createRecommendations) {
            return recommendations;
        }
               
        RPProtectionRecommendation recommendation = new RPProtectionRecommendation();                         
        Volume sourceJournal = dbClient.queryObject(Volume.class, sourceVolume.getRpJournalVolume());        
        RPRecommendation sourceJournalRecommendation = new RPRecommendation();        
        sourceJournalRecommendation.setSourceDevice(sourceJournal.getStorageController());
        sourceJournalRecommendation.setSourcePool(sourceJournal.getPool());        
        sourceJournalRecommendation.setVirtualArray(sourceJournal.getVirtualArray());       
        sourceJournalRecommendation.setVirtualPool(dbClient.queryObject(VirtualPool.class, sourceJournal.getVirtualPool())); 
        sourceJournalRecommendation.setInternalSiteName(sourceJournal.getInternalSiteName());
        recommendation.setSourceJournalRecommendation(sourceJournalRecommendation);
        
        recommendation.setProtectionDevice(sourceVolume.getProtectionController());
        recommendation.setVpoolChangeVolume(vpoolChangeVolume != null ? vpoolChangeVolume.getId() : null);
        recommendation.setVpoolChangeVpool(vpoolChangeVolume != null ? vpoolChangeVolume.getVirtualPool() : null);
        recommendation.setVpoolChangeProtectionAlreadyExists(vpoolChangeVolume != null ? vpoolChangeVolume.checkForRp() : false);
        recommendation.setResourceCount(capabilities.getResourceCount());
        
       for (VirtualArray protectionVarray : protectionVarrays) {                           
            // Find the existing source volume target that corresponds to this protection
            // virtual array.  We need to see if the storage pool has capacity for another
            // target volume.
    	   	RPRecommendation rpRecommendation = new RPRecommendation();    	  
    	    rpRecommendation.setSourcePool(sourceVolume.getPool());
    	    rpRecommendation.setSourceDevice(sourceVolume.getStorageController());
    	    rpRecommendation.setInternalSiteName(sourceVolume.getInternalSiteName());
    	    //TODO Bharath - fill in HA recommendation and VPLEX recommendation if that exists.
    	    
    	    RPRecommendation targetRecommendation = new RPRecommendation();     	
            Volume targetVolume = getTargetVolumeForProtectionVirtualArray(sourceVolume, protectionVarray);
            targetRecommendation.setInternalSiteName(targetVolume.getInternalSiteName());
            StoragePool targetPool = dbClient.queryObject(StoragePool.class, targetVolume.getPool());
            targetRecommendation.setSourcePool(targetPool.getId());
            targetRecommendation.setSourceDevice(targetPool.getStorageDevice());
                                            
            RPRecommendation journalRecommendation = new RPRecommendation();
            Volume targetJournal = dbClient.queryObject(Volume.class, targetVolume.getRpJournalVolume());
            journalRecommendation.setSourcePool(targetJournal.getPool());
            journalRecommendation.setSourceDevice(targetJournal.getStorageController());
            journalRecommendation.setVirtualPool(dbClient.queryObject(VirtualPool.class, targetJournal.getVirtualPool()));
            journalRecommendation.setVirtualArray(targetJournal.getVirtualArray());
            
            if (rpRecommendation.getTargetRecommendations() == null) {
            	rpRecommendation.setTargetRecommendations(new ArrayList<RPRecommendation>());
            }
            rpRecommendation.getTargetRecommendations().add(targetRecommendation);            
            recommendation.getSourceRecommendations().add(rpRecommendation);
        }
    
        _log.info(String.format("Produced recommendation based on existing source volume %s from " +
                "RecoverPoint consistency group %s: \n %s", sourceVolume.getLabel(), cg.getLabel(), 
                recommendation.toString(dbClient)));
        
        recommendations.add(recommendation);        
        return recommendations;
	}
	
	/**
	 * @param sourceVolume
	 * @param capabilities
	 * @param vpool
	 * @param protectionVarrays
	 * @return
	 */
	private boolean cgPoolsHaveAvailableCapacity(Volume sourceVolume, VirtualPoolCapabilityValuesWrapper capabilities, 
	        VirtualPool vpool, List<VirtualArray> protectionVarrays) {
	    boolean cgPoolsHaveAvailableCapacity = true;
	    Map<URI, Long> storagePoolRequiredCapacity = new HashMap<URI, Long>();
	    Map<URI, StoragePool> storagePoolCache = new HashMap<URI, StoragePool>();
	    
        if (sourceVolume != null) {
        	// TODO:  need to update code below to look like the stuff Bharath added for multiple resources
        	long sourceVolumesRequiredCapacity = getSizeInKB(capabilities.getSize() * capabilities.getResourceCount());
            StoragePool sourcePool = dbClient.queryObject(StoragePool.class, sourceVolume.getPool());
            storagePoolCache.put(sourcePool.getId(), sourcePool);
            updateStoragePoolRequiredCapacityMap(storagePoolRequiredCapacity, sourcePool.getId(), sourceVolumesRequiredCapacity);
            
            Volume sourceJournal = dbClient.queryObject(Volume.class, sourceVolume.getRpJournalVolume());
            long sourceJournalSizePerPolicy = RPHelper.getJournalSizeGivenPolicy(String.valueOf(capabilities.getSize()), vpool.getJournalSize(), capabilities.getResourceCount());
            long sourceJournalVolumesRequiredCapacity = getSizeInKB(sourceJournalSizePerPolicy); 
            StoragePool sourceJournalPool = dbClient.queryObject(StoragePool.class, sourceJournal.getPool());
            storagePoolCache.put(sourceJournalPool.getId(), sourceJournalPool);
            updateStoragePoolRequiredCapacityMap(storagePoolRequiredCapacity, sourceJournalPool.getId(), sourceJournalVolumesRequiredCapacity);
            
            if (sourceVolume.getRpTargets() != null) {                
                for (VirtualArray protectionVarray : protectionVarrays) {            
                    // Find the pools that apply to this virtual
                    VpoolProtectionVarraySettings settings = rpHelper.getProtectionSettings(vpool, protectionVarray);
                    // If there was no vpool specified with the protection settings, use the base vpool for this varray.
                    VirtualPool protectionVpool = vpool;
                    if (settings.getVirtualPool() != null) {
                        protectionVpool = dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
                    }
                    
                    // Find the existing source volume target that corresponds to this protection
                    // virtual array.  We need to see if the storage pool has capacity for another
                    // target volume.
                    Volume targetVolume = getTargetVolumeForProtectionVirtualArray(sourceVolume, protectionVarray);

                    // Target volumes will be the same size as the source
                    long targetVolumeRequiredCapacity = getSizeInKB(capabilities.getSize());
                    StoragePool targetPool = dbClient.queryObject(StoragePool.class, targetVolume.getPool());
                    storagePoolCache.put(targetPool.getId(), targetPool);
                    updateStoragePoolRequiredCapacityMap(storagePoolRequiredCapacity, targetPool.getId(), targetVolumeRequiredCapacity);
                    
                    // Account for the target journal volumes.
                    Volume targetJournalVolume = dbClient.queryObject(Volume.class, targetVolume.getRpJournalVolume());
                    long targetJournalSizePerPolicy =
                    		RPHelper.getJournalSizeGivenPolicy(
                    				String.valueOf(capabilities.getSize()), protectionVpool.getJournalSize(), capabilities.getResourceCount());
                    long targetJournalVolumeRequiredCapacity = getSizeInKB(targetJournalSizePerPolicy);
                    StoragePool targetJournalPool = dbClient.queryObject(StoragePool.class, targetJournalVolume.getPool());
                    storagePoolCache.put(targetJournalPool.getId(), targetJournalPool);
                    updateStoragePoolRequiredCapacityMap(storagePoolRequiredCapacity, targetJournalPool.getId(), targetJournalVolumeRequiredCapacity);                    
                }
            }
            
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
            
            for (URI storagePoolUri : storagePoolRequiredCapacity.keySet()) {
                StoragePool storagePool = storagePoolCache.get(storagePoolUri);
                if (storagePool.getFreeCapacity() < storagePoolRequiredCapacity.get(storagePoolUri)) {
                    cgPoolsHaveAvailableCapacity = false;
                    _log.info(String.format("Unable to fully align placement with existing source volume %s from RecoverPoint consistency group %s.  " +
                    		"Storage pool %s does not have adequate capacity.", sourceVolume.getLabel(), storagePool.getLabel(), cg.getLabel()));
                    break;
                } else {
                    _log.info(String.format("Storage pool %s, used by consistency group %s, has the required capacity and will be used for this placement request.", 
                            storagePool.getLabel(), cg.getLabel()));
                }
            }
        }        
        return cgPoolsHaveAvailableCapacity;
	}
	
	/**
	 * Given a source volume, gets the associated target volume for the protection virtual array.
	 * @param sourceVolume
	 * @param protectionVarray
	 * @return
	 */
	private Volume getTargetVolumeForProtectionVirtualArray(Volume sourceVolume, VirtualArray protectionVarray) {
	    Iterator<String> targetVolumes = sourceVolume.getRpTargets().iterator();
        while (targetVolumes.hasNext()) {
            Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(targetVolumes.next()));
            if( protectionVarray.getId().equals(targetVolume.getVirtualArray())) {
                return targetVolume;
            }
        }
        return null;
	}
	
	/**
	 * Convenience method to add entries to a Map used track required capacity per storage pool.
	 * @param storagePoolRequiredCapacity
	 * @param key the storage pool URI
	 * @param value 
	 */
	private void updateStoragePoolRequiredCapacityMap(Map<URI, Long> storagePoolRequiredCapacity, 
	        URI storagePoolUri, long requiredCapacity) {
        if (storagePoolRequiredCapacity.get(storagePoolUri) == null) {
            storagePoolRequiredCapacity.put(storagePoolUri, requiredCapacity);
        } else {
            long updatedRequiredCapacity = storagePoolRequiredCapacity.get(storagePoolUri) + requiredCapacity;
            storagePoolRequiredCapacity.put(storagePoolUri, updatedRequiredCapacity);
        }
	}

    /**
     * Creates primary (active) and secondary (standby) cluster recommendations for MetroPoint.  
     * 
     * We first determine the type of MetroPoint request based on the protection virtual array
     * configuration (single remote, local only, or local and remote).  Using this information
     * we determine a possible placement recommendation for the primary cluster.  Using the
     * primary cluster recommendation we then figure out a secondary cluster recommendation.  
     * The secondary cluster recommendation needs protection attributes that give with the
     * primary cluster recommendation to satisfy the type of MetroPoint configuration requested.
     * 
     * @param varray the source virtual array.
     * @param protectionVarrays the RecoverPoint protection virtual arrays.
     * @param vpool the source virtual pool.
     * @param haVarray the HA virtual array - secondary cluster.
     * @param haVpool the HA virtual pool - secondary cluster.
     * @param capabilities parameters.
     * @param candidateActiveSourcePools the candidate primary cluster source pools.
     * @param candidateStandbySourcePools  the candidate secondary cluster source pools.
     * @param candidateProtectionPoolsMap pre-populated map for tgt varray to storage pools, use null if not needed
     * @return list of Recommendation objects to satisfy the request
     */
    protected RPProtectionRecommendation createMetroPointRecommendations(VirtualArray varray,
    												List<VirtualArray> protectionVarrays, VirtualPool vpool, VirtualArray haVarray, VirtualPool haVpool, 
										            VirtualPoolCapabilityValuesWrapper capabilities,List<StoragePool> candidateActiveSourcePools,
										            List<StoragePool> candidateStandbySourcePools, Map<VirtualArray, List<StoragePool>> candidateProtectionPools, 
										            Volume vpoolChangeVolume, Project project) {
    	    	
    	// Initialize a list of recommendations to be returned.    	
    	Map<Recommendation, Recommendation> metroPointRecommendations = new HashMap<Recommendation, Recommendation>();
    	Set<ProtectionSystem> secondaryProtectionSystems = new HashSet<ProtectionSystem>();
    	placementStatus = new PlacementStatus();
    	secondaryPlacementStatus = new PlacementStatus();

    	int requestedResourceCount = capabilities.getResourceCount();
    	int totalSatisfiedCount = 0;

    	List<URI> protectionVarrayURIs = new ArrayList<URI>();
    	for (VirtualArray vArray : protectionVarrays) {
    		protectionVarrayURIs.add(vArray.getId());
    		placementStatus.getProcessedProtectionVArrays().put(vArray.getId(), false);
    	}

    	List<URI> activeSourcePoolUris = new ArrayList<URI>();    	
    	List<URI> standbySourcePoolUris = new ArrayList<URI>();    	
    	List<StoragePool> sortedActiveSourceJournalPools = null;        
    	List<StoragePool> sortedStandbySourceJournalPools = null;
    	Map<URI, List<StoragePool>> targetVarraySortedPools = new HashMap<URI, List<StoragePool>>();

    	// Sort the primary source candidate pools.
    	VirtualArray activeJournalVarray = varray;
    	if (candidateActiveSourcePools != null && !candidateActiveSourcePools.isEmpty()) {
    		_log.info(String.format("Primary Source varray [%s] pools:", varray.getLabel()));
    	    printSortedStoragePools(candidateActiveSourcePools);

    		if (NullColumnValueGetter.isNotNullValue(vpool.getJournalVarray())) {
    			activeJournalVarray = dbClient.queryObject(VirtualArray.class, URI.create(vpool.getJournalVarray()));
    		}

    		VirtualPool activeSourceJournalVpool = (vpool.getJournalVpool() != null ?
    				dbClient.queryObject(VirtualPool.class, URI.create(vpool.getJournalVpool())) : vpool);
    		_log.info(String.format("Active Source Journal varray [%s] pools:", activeJournalVarray.getLabel()));
    		sortedActiveSourceJournalPools = getCandidatePools(activeJournalVarray, activeSourceJournalVpool, null, null, project, capabilities, RPHelper.JOURNAL);     
    	}

    	// Sort the secondary source candidate pools.
    	VirtualArray standbySourceJournalVarray = haVarray;
    	if (candidateStandbySourcePools != null && !candidateStandbySourcePools.isEmpty()) {
    		_log.info(String.format("Secondary Source varray [%s] pools:", haVarray.getLabel()));    		
    		printSortedStoragePools(candidateStandbySourcePools);
    		
    		if (NullColumnValueGetter.isNotNullValue(vpool.getStandbyJournalVarray())) {
    			standbySourceJournalVarray = dbClient.queryObject(VirtualArray.class, URI.create(vpool.getStandbyJournalVarray()));
    		}

    		VirtualPool standbySourceJournalVpool = (vpool.getStandbyJournalVpool() != null ?
    				dbClient.queryObject(VirtualPool.class, URI.create(vpool.getStandbyJournalVpool())) : haVpool);
    		sortedStandbySourceJournalPools = getCandidatePools(standbySourceJournalVarray, standbySourceJournalVpool, null, null, project, capabilities, RPHelper.JOURNAL);
    		_log.info(String.format("Secondary Source varray [%s] pools:", standbySourceJournalVarray.getLabel()));
    		printSortedStoragePools(sortedStandbySourceJournalPools);
    	}

    	// Sort the target protection candidate pools.
    	if (candidateProtectionPools != null 
    			&& !candidateProtectionPools.isEmpty()) {
    		for (VirtualArray protectionVarray : candidateProtectionPools.keySet()) {
    			_log.info(String.format("Target varray [%s] sorted pools:", protectionVarray.getLabel()));
    			targetVarraySortedPools.put(protectionVarray.getId(), candidateProtectionPools.get(protectionVarray));
    		}
    	}

    	List<VirtualArray> activeProtectionVarrays = new ArrayList<VirtualArray>();
    	if (haVarray != null) {
    		// Build the list of protection virtual arrays to consider for determining a
    		// primary placement solution.  Add all virtual arrays from the source virtual
    		// pool list of protection virtual arrays, except for the HA virtual array.  
    		// In the case of local and/or remote protection, the HA virtual array should 
    		// never be considered as a valid protection target for primary placement.
    		for (VirtualArray protectionVarray : protectionVarrays) {
    			if (!protectionVarray.getId().equals(haVarray.getId())) {
    				activeProtectionVarrays.add(protectionVarray);
    			}
    		}
    	}

    	List<VirtualArray> standbyProtectionVarrays = new ArrayList<VirtualArray>();
    	// Build the list of protection virtual arrays to consider for determining a
    	// secondary placement solution.  Add all virtual arrays from the source virtual
    	// pool list of protection virtual arrays, except for the source virtual array.  
    	// In the case of local and/or remote protection, the source virtual array should 
    	// never be considered as a valid protection target for secondary placement.
    	for (VirtualArray protectionVarray : protectionVarrays) {
    		if (!protectionVarray.getId().equals(varray.getId())) {
    			standbyProtectionVarrays.add(protectionVarray);
    		}
    	}

    	// The attributes below will not change throughout the placement process
    	placementStatus.setSrcVArray(varray.getLabel());
    	placementStatus.setSrcVPool(vpool.getLabel());

    	Map<URI, Set<ProtectionSystem>> standbyStoragePoolsToProtectionSystems = new HashMap<URI, Set<ProtectionSystem>>();
    	List<Recommendation> recommendedPools = getRecommendedPools(candidateActiveSourcePools, capabilities.getSize());
    	if (recommendedPools == null || recommendedPools.isEmpty()) {
    		//TODO Bharath: fail here
    	}
    	
    	RPProtectionRecommendation rpProtectionRecommendation = new RPProtectionRecommendation();        
		rpProtectionRecommendation.setVpoolChangeVolume(vpoolChangeVolume != null ? vpoolChangeVolume.getId() : null);
		rpProtectionRecommendation.setVpoolChangeVpool(vpoolChangeVolume != null ? vpoolChangeVolume.getVirtualPool() : null);
		rpProtectionRecommendation.setVpoolChangeProtectionAlreadyExists(vpoolChangeVolume != null ? vpoolChangeVolume.checkForRp() : false);	
		
		RPRecommendation activeJournalRecommendation = null;
		RPRecommendation standbyJournalRecommendation = null;
		
    	while (totalSatisfiedCount < requestedResourceCount) {        	
    		boolean secondaryRecommendationSolution = false;
    		int satisfiedSourceVolCount = 0;

    		if (vpoolChangeVolume != null) {
    			// If this is a change vpool operation, the source has already been placed and there is only 1
    			// valid pool, the existing one. This is just to used to pass through the placement code.
    			URI existingPrimarySourcePoolUri = candidateActiveSourcePools.iterator().next().getId();
    			_log.info(String.format("Primary Source Pool already exists, reuse pool: [%s].", existingPrimarySourcePoolUri.toString()));
    			activeSourcePoolUris.add(existingPrimarySourcePoolUri);        	    
    			satisfiedSourceVolCount = 1;
    		}
    		
    		int remainingPossiblePrimarySrcPoolSolutions = activeSourcePoolUris.size();
    		_log.info("Determining RP placement for the primary (active) MetroPoint cluster.");
    		for (Recommendation recommendedPool : recommendedPools) {
    			satisfiedSourceVolCount = (recommendedPool.getResourceCount() >= requestedResourceCount) ? requestedResourceCount : recommendedPool.getResourceCount(); 
    			//for (URI primarySourcePoolURI : activeSourcePoolUris) {
    			--remainingPossiblePrimarySrcPoolSolutions;
    			// Start with the top of the list of source pools, find a solution based on that.            
    			// Given the candidatePools.get(0), what protection systems and internal sites protect it?
    			Set<ProtectionSystem> primaryProtectionSystems = new HashSet<ProtectionSystem>();
    			ProtectionSystem cgProtectionSystem = getCgProtectionSystem(capabilities.getBlockConsistencyGroup());
    			StoragePool sourcePool = dbClient.queryObject(StoragePool.class, recommendedPool.getSourcePool());
    			// If we have an existing RP consistency group we want to use the same protection system
    			// used by other volumes in it. 
    			if (cgProtectionSystem != null) {
    				BlockConsistencyGroup cg = dbClient.queryObject(
    						BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
    				_log.info("Narrowing down placement to use protection system {}, which is currently used by RecoverPoint consistency group {}.", 
    						cgProtectionSystem.getLabel(), cg);
    				primaryProtectionSystems.add(cgProtectionSystem);
    			} else {
    				primaryProtectionSystems = 
    						getProtectionSystemsForStoragePool(sourcePool, varray, true);
    				if (primaryProtectionSystems.isEmpty()) {
    					continue;
    				}
    			}

    			// Sort the ProtectionSystems based on the last time a CG was created. Always use the
    			// ProtectionSystem with the oldest cgLastCreated timestamp to support a round-robin
    			// style of load balancing.
    			List<ProtectionSystem> primaryProtectionSystemsLst = 
    					sortProtectionSystems(primaryProtectionSystems);

    			for (ProtectionSystem primaryProtectionSystem : primaryProtectionSystemsLst) {
    				Calendar cgLastCreated = primaryProtectionSystem.getCgLastCreatedTime();
    				_log.info("Attempting to use protection system {}, which was last used to create a CG on {}.", 
    						primaryProtectionSystem.getLabel(), cgLastCreated != null ? cgLastCreated.getTime().toString() : "N/A");

    				List<String> primaryAssociatedStorageSystems = getCandidateVisibleStorageSystems(sourcePool, primaryProtectionSystem, varray, activeProtectionVarrays, true);

    				// Get candidate internal site names and associated storage system, make sure you check RP topology to see if the sites can protect that many targets            	            	
    				if (primaryAssociatedStorageSystems.isEmpty()) {
    					// no rp site clusters connected to this storage system, should not hit this, but just to be safe we'll catch it
    					_log.info("RP Placement: Protection System " + primaryProtectionSystem.getLabel() +  " does not have an rp site cluster connected to Storage pool " + sourcePool.getLabel());
    					continue;
    				}

    				for (String primaryAssociatedStorageSystem : primaryAssociatedStorageSystems) {        			            	
    					// Start over with the recommendation object
    					String primarySourceInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(primaryAssociatedStorageSystem);
    					URI primarySourceStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(primaryAssociatedStorageSystem), 
    							dbClient, StorageSystemType.BLOCK);

    					VirtualPool journalVpool = (NullColumnValueGetter.isNotNullValue(vpool.getJournalVpool()) ? 
    							dbClient.queryObject(VirtualPool.class, URI.create(vpool.getJournalVpool())) : haVpool);
    					
    					rpProtectionRecommendation.setProtectionDevice(primaryProtectionSystem.getId());  
    					RPRecommendation primaryRpRecommendation = buildSourceRecommendation(primaryAssociatedStorageSystem, varray, vpool, 
		    															primaryProtectionSystem, sourcePool, candidateActiveSourcePools,
																		capabilities, satisfiedSourceVolCount, placementStatus, vpoolChangeVolume, false);    					    				    					    					    					
    					if (primaryRpRecommendation == null) {
    						// No source placement found for the primaryAssociatedStorageSystem, so continue.
    						continue;
    					}
    					
    					if (activeJournalRecommendation == null) {
    						activeJournalRecommendation = buildJournalRecommendation(rpProtectionRecommendation, primarySourceInternalSiteName, vpool.getJournalSize(), 
    														activeJournalVarray, journalVpool, primaryProtectionSystem,
         			    									sortedActiveSourceJournalPools, capabilities, requestedResourceCount, vpoolChangeVolume, false);
        					rpProtectionRecommendation.setSourceJournalRecommendation(activeJournalRecommendation);
        				}
    					    				    				    					
    					rpProtectionRecommendation.getSourceRecommendations().add(primaryRpRecommendation);    					
    					
    					_log.info("An RP source placement solution has been identified for the MetroPoint primary (active) cluster.");

    					// Find a solution, given this vpool, and the target varrays
    					if (findSolution(rpProtectionRecommendation, primaryRpRecommendation, varray, vpool, activeProtectionVarrays, 
    							capabilities, satisfiedSourceVolCount, true, null, project)) {                     	
    						// Check to ensure the protection system can handle the new resources about to come down
    						if (!verifyPlacement(primaryProtectionSystem, rpProtectionRecommendation, rpProtectionRecommendation.getResourceCount())) {
    							continue;
    						}

    						_log.info("An RP target placement solution has been identified for the MetroPoint primary (active) cluster.");

    						_log.info(rpProtectionRecommendation.toString(dbClient));

    						// We have a primary cluster protection recommendation for the specified metroPointType.  We need to now determine if we can
    						// protect the secondary cluster for the given metroPointType.
    						_log.info("Determining RP placement for the secondary (standby) MetroPoint cluster.");
    						secondaryRecommendationSolution = false;

    						// Get the candidate secondary cluster source pools - sets secondarySourcePoolURIs.
    						List<Recommendation> secondaryPoolsRecommendation = new ArrayList<Recommendation>();
    						if (vpoolChangeVolume != null) {
    							// If this is a change vpool operation, the source has already been placed and there is only 1
    							// valid pool for the secondary side, the existing one. This is just to used to pass through the placement code.
    							URI existingSecondarySourcePoolUri = candidateStandbySourcePools.iterator().next().getId();
    							_log.info(String.format("Secondary Source Pool already exists, reuse pool: [%s].", existingSecondarySourcePoolUri.toString()));
    							standbySourcePoolUris.add(existingSecondarySourcePoolUri);
    						}
    						else {
    							secondaryPoolsRecommendation = getRecommendedPools(candidateStandbySourcePools, capabilities.getSize());
    						}                        	    

    						secondaryPlacementStatus.setSrcVArray(haVarray.getLabel());
    						secondaryPlacementStatus.setSrcVPool(haVpool.getLabel());

    						//for (URI secondarySourcePoolURI : standbySourcePoolUris) {
    						for(Recommendation secondaryPoolRecommendation : secondaryPoolsRecommendation) {    							
    							// Start with the top of the list of source pools, find a solution based on that.            
    							StoragePool standbySourcePool = dbClient.queryObject(StoragePool.class, secondaryPoolRecommendation.getSourcePool()); 	        	

    							// Lookup source pool protection systems in the cache first.
    							if (standbyStoragePoolsToProtectionSystems.containsKey(standbySourcePool.getId())) {
    								secondaryProtectionSystems = standbyStoragePoolsToProtectionSystems.get(standbySourcePool.getId());
    							} else {
    								secondaryProtectionSystems = getProtectionSystemsForStoragePool(standbySourcePool, haVarray, true);

    								if (secondaryProtectionSystems.isEmpty()) {
    									continue;
    								}
    								// Cache the result for this pool
    								standbyStoragePoolsToProtectionSystems.put(standbySourcePool.getId(), secondaryProtectionSystems);	
    							}

    							ProtectionSystem selectedSecondaryProtectionSystem = null;

    							// Ensure the we have a secondary protection system that matches the primary protection system
    							for (ProtectionSystem secondaryProtectionSystem : secondaryProtectionSystems) {
    								if (secondaryProtectionSystem.getId().equals(rpProtectionRecommendation.getProtectionDevice())) {
    									// We have a protection system match for this pool, continue.
    									selectedSecondaryProtectionSystem = secondaryProtectionSystem;
    									break;
    								}
    							}

    							if (selectedSecondaryProtectionSystem == null) {
    								// There is no protection system for this pool that matches the selected primary
    								// protection system.  So lets try another pool.
    								_log.info("RP Placement: Secondary source storage pool " + standbySourcePool.getLabel() + " does not have connectivity to the selected primary protection system.");
    								continue;
    							} else { 
    								// List of concatenated Strings that contain the RP site + associated storage system.
    								List<String> secondaryAssociatedStorageSystems = getCandidateVisibleStorageSystems(standbySourcePool, selectedSecondaryProtectionSystem, 
    										haVarray, activeProtectionVarrays, true);

    								// Get candidate internal site names and associated storage system, make sure you check RP topology to see if the sites can protect that many targets            	            	
    								if (secondaryAssociatedStorageSystems.isEmpty()) {
    									// no rp site clusters connected to this storage system, should not hit this, but just to be safe we'll catch it
    									_log.info("RP Placement: Protection System " + selectedSecondaryProtectionSystem.getLabel() +  " does not have an rp site cluster connected to Storage pool " + standbySourcePool.getLabel());
    									continue;
    								}

    								Set<String> sortedSecondaryAssociatedStorageSystems = new LinkedHashSet<String>();
    								Set<String> sameAsPrimary = new HashSet<String>();

    								// Perform a preliminary sorting operation.  We want to only consider secondary associated storage systems
    								// that reference the same storage system as the primary recommendation.  Also, want to prefer RP sites
    								// that are different
    								String secondarySourceInternalSiteName = "";
    								for (String secondaryAssociatedStorageSystem : secondaryAssociatedStorageSystems) {  
    									secondarySourceInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(secondaryAssociatedStorageSystem);
    									URI secondarySourceStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(secondaryAssociatedStorageSystem), 
    											dbClient, StorageSystemType.BLOCK);                        				                        				

    									if (secondaryAssociatedStorageSystem.equals(
    											primaryRpRecommendation.getRpSiteAssociateStorageSystem())) {
    										sameAsPrimary.add(secondaryAssociatedStorageSystem);
    									} else if (secondarySourceStorageSystemURI.equals(primarySourceStorageSystemURI) 
    											&& !secondarySourceInternalSiteName.equals(primarySourceInternalSiteName)) {
    										sortedSecondaryAssociatedStorageSystems.add(secondaryAssociatedStorageSystem);
    									}
    								}

    								sortedSecondaryAssociatedStorageSystems.addAll(sameAsPrimary);

    								for (String secondaryAssociatedStorageSystem : sortedSecondaryAssociatedStorageSystems) {
    									standbySourceJournalVarray = (NullColumnValueGetter.isNotNullValue(vpool.getStandbyJournalVarray()) ?
    											dbClient.queryObject(VirtualArray.class, URI.create(vpool.getStandbyJournalVarray())) : haVarray);
    									journalVpool = (NullColumnValueGetter.isNotNullValue(vpool.getStandbyJournalVpool()) ? 
    											dbClient.queryObject(VirtualPool.class, URI.create(vpool.getStandbyJournalVpool())) : haVpool);
    									
    									RPRecommendation secondaryRpRecommendation = buildSourceRecommendation(secondaryAssociatedStorageSystem, haVarray, haVpool, 
	    																					selectedSecondaryProtectionSystem, 
											    											standbySourcePool, candidateStandbySourcePools, 
											    											capabilities,
											    											satisfiedSourceVolCount, secondaryPlacementStatus, null, true);                   
    									
    									if (secondaryRpRecommendation == null) {
    										// No source placement found for the secondaryAssociatedStorageSystem, so continue.
    										continue;
    									}
    									
    									if (standbyJournalRecommendation == null) {
    										standbyJournalRecommendation = buildJournalRecommendation(rpProtectionRecommendation, secondarySourceInternalSiteName, vpool.getJournalSize(), 
    																			standbySourceJournalVarray, journalVpool, primaryProtectionSystem,
    																			sortedStandbySourceJournalPools, capabilities,requestedResourceCount, vpoolChangeVolume, true);
    			        					rpProtectionRecommendation.setStandbyJournalRecommendation(standbyJournalRecommendation);
    			        				}    									
    									    								
    									primaryRpRecommendation.setHaRecommendation(secondaryRpRecommendation);    									
    									
    									// Find a solution, given this vpool, and the target varrays
    									if (findSolution(rpProtectionRecommendation, secondaryRpRecommendation, haVarray, vpool, standbyProtectionVarrays, 
    											capabilities, satisfiedSourceVolCount, true, primaryRpRecommendation, project)) {
    										// Check to ensure the protection system can handle the new resources about to come down
    										if (!verifyPlacement(primaryProtectionSystem, rpProtectionRecommendation, rpProtectionRecommendation.getResourceCount())) {
    											continue;
    										}

    										_log.info("An RP target placement solution has been identified for the MetroPoint secondary (standby) cluster.");

    										metroPointRecommendations.put(primaryRpRecommendation, secondaryRpRecommendation);
    										secondaryRecommendationSolution = true;
    										break;
    									} else {
    										// Unable to find a suitable secondary target solution
    										continue;
    									}
    								}

    								if (secondaryRecommendationSolution) {
    									// We are done - secondary recommendation found
    									requestedResourceCount = requestedResourceCount - satisfiedSourceVolCount;
    									totalSatisfiedCount += satisfiedSourceVolCount;
    									break;
    								} else {
    									continue;
    								}
    							}
    						}

    						if (!secondaryRecommendationSolution) {
    							_log.info("Unabled to find MetroPoint secondary cluster placement recommendation that jives with primary cluster recommendation.  "
    									+ "Need to find a new primary recommendation.");
    							// Exhausted all the secondary pool URIs.  Need to find another primary solution.
    							break;
    						}    						    			
    						return rpProtectionRecommendation;
    					} else {
    						// Not sure there's anything to do here.  Just go to the next candidate protection system or Protection System
    						_log.info("Could not find a solution against protection system {} and internal cluster name {}", 
    								primaryProtectionSystem.getLabel(),
    								primarySourceInternalSiteName);
    					}
    				} // end of for loop trying to find solution using possible rp cluster sites
    			} // end of protection systems for loop        		           
    		} // end of candidate source pool while loop

    		//we went through all the candidate pools and there are still some of the volumes that haven't been placed, then we failed to find a solution
    		if ((remainingPossiblePrimarySrcPoolSolutions == 0) && totalSatisfiedCount < capabilities.getResourceCount()) {
    			_log.error("Could not find a MetroPoint placement solution.  In a MetroPoint consistency group, there can "
    					+ "exist at most one remote copy and from zero to two local copies.  If there is no remote copy, "
    					+ "there must be two local copies, one at each side of the VPLEX Metro.");
    			throw APIException.badRequests.cannotFindSolutionForRP(buildMetroProintPlacementStatusString());
    		}        	
    	}
    	_log.error("ViPR could not find matching target storage pools that could be protected via RecoverPoint"); 

    	_log.error("Could not find a MetroPoint placement solution.  In a MetroPoint consistency group, there can "
    			+ "exist at most one remote copy and from zero to two local copies.  If there is no remote copy, "
    			+ "there must be two local copies, one at each side of the VPLEX Metro.");
    	throw APIException.badRequests.cannotFindSolutionForRP(buildMetroProintPlacementStatusString());        	
    }
    
    /**
     * Distill the list of storage pools visible to the journal virtual array into only those pools that can see the same RP site as the corresponding source copy.
     * @param sortedSourceJournalStoragePoolsMap source journal storage pools map 
     * @param psId protection system 
     * @param internalSiteName internal site name
     * @param journalVarray virtual array of the journal
     * @return size sorted journal storage pools map
     */
    private Set<StoragePool> filterJournalPoolsByRPSiteConnectivity(List<StoragePool> journalStoragePools, URI psId,
										String internalSiteName, VirtualArray journalVarray, VirtualPool journalVpool) {
    		boolean hasRPSiteVisibleJournals = false;
    		Set<StoragePool> rpSiteVisiblePools = new HashSet<StoragePool>();    		
    	
    		for (StoragePool journalStoragePool : journalStoragePools) {    			    			    				
    				_log.info(String.format("Checking pool : [%s]", journalStoragePool.getLabel()));
    				
    				List<String> associatedStorageSystems = getCandidateTargetVisibleStorageSystems(psId, 
                            journalVarray, internalSiteName, 
                            journalStoragePool, VirtualPool.vPoolSpecifiesHighAvailability(journalVpool));

					if (associatedStorageSystems == null || associatedStorageSystems.isEmpty()) {
						_log.info(String.format("Solution cannot be found using target pool " + journalStoragePool.getLabel() + " there is no connectivity to rp cluster sites."));
						continue;
					}
					
					_log.info(String.format("Associated storage systems for pool [%s] : [%s]", journalStoragePool.getLabel(), Joiner.on("-").join(associatedStorageSystems)));    				    
				
					for (String associatedStorageSystem : associatedStorageSystems) {
						 URI storageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(
								 						ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem), 
								 						dbClient, StorageSystemType.BLOCK);
						 StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemURI);
			                
	    				if (!isRpSiteConnectedToVarray(storageSystemURI, psId, internalSiteName, journalVarray)) {
	    					_log.info(String.format("RP Journal Placement : StorageSystem [%s] does NOT have connectivity to RP site [%s], ignoring..",
	    											storageSystem.getLabel(), internalSiteName));
	    					continue;
	    				}
	    				
	    				rpSiteVisiblePools.add(journalStoragePool);
	    				hasRPSiteVisibleJournals = true;
					}    			
    		}    	
    		
    	_log.info(String.format("Following pools are considered for Journal volumes on site %s - [%s]", internalSiteName, Joiner.on("-").join(rpSiteVisiblePools)));
		return (hasRPSiteVisibleJournals ? rpSiteVisiblePools : null);
    }

	/**
     * Builds the source placement recommendation based on the source pool and it's associated storage
     * system/RP site.
     *    
     * @param associatedStorageSystem the associated RP site + storage system concatenated in a single string.
     * @param vArray the virtual array.
     * @param vPool the virtual pool.
     * @param journalVarray the journal virtual array for this copy
     * @param journalVpool the journal virtual pool for this copy
     * @param ps the protection system
     * @param sourcePool the source storage pool.
     * @param sortedSourcePoolsMap the stored (by size) source storage pool map.  
     * @param sortedSourceJournalStoragePoolsMap the stored (by size) source journal storage pool map.  Used to find the journal pool.
     * @param capabilities the capability params.
     * @param satisfiedSourceVolCount the statisfied volume count.
     * @param placementStat the PlacementStatus to update.
     * @param vpoolChangeVolume change virtual pool param
     * @param standbySite indicates if this is a metropoint standby site (or active if false)
     * @return recommendation object
     */
    private RPRecommendation buildSourceRecommendation(String associatedStorageSystem, VirtualArray vArray, 
								    		VirtualPool vPool, ProtectionSystem ps, StoragePool sourcePool, List<StoragePool> sortedSourcePoolsMap, 
								    		VirtualPoolCapabilityValuesWrapper capabilities,
								    		int satisfiedSourceVolCount, PlacementStatus placementStat, Volume vpoolChangeVolume, boolean isMPStandby) {
	    String sourceInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(associatedStorageSystem);
	    URI sourceStorageSytemUri = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem),
	    											dbClient, StorageSystemType.BLOCK);
	    
		if (!isRpSiteConnectedToVarray(
				sourceStorageSytemUri, ps.getId(), sourceInternalSiteName, vArray)) {
			_log.info(String.format("RP Placement: Disqualified RP site [%s] because its initiators are not in a network configured for use by the virtual array [%s]",
					sourceInternalSiteName, vArray.getLabel()));
			return null;
		}            	
		
		URI storageSystemUri =  ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem), 
				dbClient, StorageSystemType.BLOCK);		
		StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemUri);
		String type = storageSystem.getSystemType();		
				
		RPRecommendation rpRecommendation = new RPRecommendation();		
		rpRecommendation.setRpSiteAssociateStorageSystem(associatedStorageSystem);		
		rpRecommendation.setSourcePool(sourcePool.getId());
		rpRecommendation.setSourceDevice(sourcePool.getStorageDevice());
		if (DiscoveredDataObject.Type.vplex.name().equals(type)) {
			VPlexRecommendation virtualVolumeRecommendation = new VPlexRecommendation();
			virtualVolumeRecommendation.setVirtualArray(rpRecommendation.getVirtualArray());
			virtualVolumeRecommendation.setVirtualPool(rpRecommendation.getVirtualPool());
			virtualVolumeRecommendation.setVPlexStorageSystem((sourceStorageSytemUri));
			rpRecommendation.setVirtualVolumeRecommendation(virtualVolumeRecommendation);
		}						
    	rpRecommendation.setResourceCount(satisfiedSourceVolCount);
    	rpRecommendation.setVirtualArray(vArray.getId());
    	rpRecommendation.setVirtualPool(vPool);    	 
    	rpRecommendation.setInternalSiteName(sourceInternalSiteName);
		return rpRecommendation;
    }
    
	/**
     * Builds the source placement recommendation based on the source pool and it's associated storage
     * system/RP site.
     *    
     * @param associatedStorageSystem the associated RP site + storage system concatenated in a single string.
     * @param vArray the virtual array.
     * @param vPool the virtual pool.
     * @param journalVarray the journal virtual array for this copy
     * @param journalVpool the journal virtual pool for this copy
     * @param ps the protection system
     * @param sourcePool the source storage pool.
     * @param sortedSourcePoolsMap the stored (by size) source storage pool map.  
     * @param sortedSourceJournalStoragePoolsMap the stored (by size) source journal storage pool map.  Used to find the journal pool.
     * @param capabilities the capability params.
     * @param satisfiedSourceVolCount the statisfied volume count.
     * @param placementStat the PlacementStatus to update.
     * @param vpoolChangeVolume change virtual pool param
     * @param standbySite indicates if this is a metropoint standby site (or active if false)
     * @return recommendation object
     */
    private RPRecommendation buildJournalRecommendation(RPProtectionRecommendation rpProtectionRecommendation, String internalSiteName, 
    										String journalPolicy, VirtualArray journalVarray, VirtualPool journalVpool, ProtectionSystem ps, 
								    		List<StoragePool> journalStoragePools, VirtualPoolCapabilityValuesWrapper capabilities,
								    		int satisfiedSourceVolCount, Volume vpoolChangeVolume, boolean isMPStandby) {
	   	
        Set<StoragePool> rpSiteVisibleJournalPools = filterJournalPoolsByRPSiteConnectivity(journalStoragePools, ps.getId(), 
        																	internalSiteName, journalVarray, journalVpool) ;
		if (null == rpSiteVisibleJournalPools) {			
			_log.info(String.format("RP Journal Placement: Disqualified RP site [%s] because its initiators are not in a network configured for use by the virtual array [%s]", 
					internalSiteName, journalVarray.getLabel()));
			return null;
		}        				
			
		//Primary source journal remains what it was before the change Vpool operation.  
		URI sourceJournalPoolURI;
		if (vpoolChangeVolume != null 
		        && !NullColumnValueGetter.isNullURI(vpoolChangeVolume.getRpJournalVolume()) 
		        && !isMPStandby) {
			Volume existingJournalVolume = dbClient.queryObject(Volume.class, vpoolChangeVolume.getRpJournalVolume());
			sourceJournalPoolURI = existingJournalVolume.getPool();
		} else {
			sourceJournalPoolURI = placeJournalStoragePool(rpSiteVisibleJournalPools, capabilities, satisfiedSourceVolCount,
															rpProtectionRecommendation, null, journalPolicy);
		}
		
		RPRecommendation rpJournalRecommendation = new RPRecommendation();		
		rpJournalRecommendation.setSourcePool(sourceJournalPoolURI);
		rpJournalRecommendation.setSourceDevice(dbClient.queryObject(StoragePool.class, sourceJournalPoolURI).getStorageDevice());		
		rpJournalRecommendation.setInternalSiteName(internalSiteName);
		rpJournalRecommendation.setResourceCount(1);
		_log.info(String.format("Setting Journal Recommendation : journal-varray [%s], journal-vpool [%s]", journalVarray.getLabel(), journalVpool.getLabel()));	
		rpJournalRecommendation.setVirtualArray(journalVarray.getId());
		rpJournalRecommendation.setVirtualPool(journalVpool);		    
    	return rpJournalRecommendation;
    }
	
	
    /**
     * Builds the PlacementStatus string for MetroPoint.  Includes the primary and secondary
     * PlacementStatus objects.
     * @return
     */
    private String buildMetroProintPlacementStatusString() {
    	StringBuffer placementStatusBuf = new StringBuffer();
    	if (placementStatus != null) {
	    	placementStatusBuf.append("\nPrimary Cluster");
	    	placementStatusBuf.append(placementStatus.toString(dbClient));
    	}
    	
    	if (secondaryPlacementStatus != null) {
	    	placementStatusBuf.append("\nSecondary Cluster");
	    	placementStatusBuf.append(secondaryPlacementStatus.toString(dbClient));
    	}    	
    	return placementStatusBuf.toString();
    }
    
    /**
     * Verifies that the protection system is capable of handling the recommendation.
     * 
     * @param ps the protection system.
     * @param recommendation the recommendation to verify against the protection system.
     * @param resourceCount the resource count.
     * @return true if the protection system of capable of handling the request, false otherwise.
     */
    private boolean verifyPlacement(ProtectionSystem ps, RPProtectionRecommendation recommendation, int resourceCount) {
    	if (!this.fireProtectionPlacementRules(ps, recommendation, resourceCount)) {
			_log.warn("Although we found a solution using RP system {}, the protection placement rules found there aren't enough available resource on the appliance to satisfy the request.",
					ps.getLabel());
			// If we made it this far we have an rp configuration with enough resources available to protect a source volume and its perspective target volumes
			// but the protection system cannot handle the request 
			recommendation.setPlacementStepsCompleted(PlacementProgress.PROTECTION_SYSTEM_CANNOT_FULFILL_REQUEST);
			if (secondaryPlacementStatus != null && secondaryPlacementStatus.isBestSolutionToDate(recommendation)){
				secondaryPlacementStatus.setLatestInvalidRecommendation(recommendation);
			}
			return false;
		}
    	return true;
    }
    
    /**
     * Gets the protection systems that protect the given storage pool.
     * 
     * @param storagePool the storage pool to use for protection system connectivity.
     * @param vArray the virtual array used to search for protection system connectivity.
     * @param isRpVplex true if this request is for RP+VPlex, false otherwise.
     * @return the list of protection systems that protect the storage pool.
     */
    private Set<ProtectionSystem> getProtectionSystemsForStoragePool(StoragePool storagePool, VirtualArray vArray, boolean isRpVplex) {
	    Set<ProtectionSystem> protectionSystems = new HashSet<ProtectionSystem>();
		protectionSystems = ConnectivityUtil.getProtectionSystemsForStoragePool(dbClient, storagePool, vArray.getId(), isRpVplex);
		
		// Verify that the candidate pool can be protected
		if (protectionSystems.isEmpty()) {
			// TODO: for better performance, should we remove all storage pools that belong to the same array as this storage pool?
			// Log message indicating this storage pool does not have protection capabilities
			_log.info("RP Placement: Storage pool " + storagePool.getLabel() + " does not have connectivity to a protection system.");
			// Remove the pool we were trying to use.
		} 
		
		return protectionSystems;
    }

    /**
     * Print out the sorted storage pools
     * 
     * @param varraySortedPoolMap Sorted Storage Pools
     */
    private void printSortedStoragePools(List<StoragePool> poolList) {                                    
        StringBuffer poolLabelsStr = new StringBuffer(); 
        for (StoragePool pool : poolList) {               
        	poolLabelsStr.append(String.format("Storage Pool : %s - Free Capacity : %s\n", pool.getLabel(), pool.getFreeCapacity()));
        }        
        _log.info(poolLabelsStr.toString());
        _log.info("------------------------------------------------");
    }

    /**
     * Gets the ProtectionSystem associated with an RP BlockConsistencyGroup.  All volumes
     * in a CG will have the same ProtectionSystems so we just need to reference the
     * first volume.
     * 
     * @param blockConsistencyGroupUri
     * @return
     */
	private ProtectionSystem getCgProtectionSystem(URI blockConsistencyGroupUri) {
        ProtectionSystem protectionSystem = null;
        List<Volume> cgVolumes = rpHelper.getCgVolumes(blockConsistencyGroupUri);
        
        if (cgVolumes != null && !cgVolumes.isEmpty()) {
        	for (Volume cgVolume : cgVolumes) {
        		if (cgVolume.getProtectionController() != null) {
        			return dbClient.queryObject(
        					ProtectionSystem.class, cgVolume.getProtectionController());
  
        		}
        	}
        }       
        return protectionSystem;
    }
    
    /**
     * Gets the internal site name for existing source volumes in an RP
     * consistency group.
     * @param blockConsistencyGroupUri
     * @return
     */
    private String getCgSourceInternalSiteNameAndAssociatedStorageSystem(URI blockConsistencyGroupUri) {
        String associatedStorageSystem = null;        
        List<Volume> cgSourceVolumes = rpHelper.getCgVolumes(blockConsistencyGroupUri, Volume.PersonalityTypes.SOURCE.toString());
        
        if (!cgSourceVolumes.isEmpty()) {
            Volume cgVol = cgSourceVolumes.get(0);
            String sourceInternalSiteName = cgVol.getInternalSiteName();
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, cgVol.getStorageController());

        	// Special check for VPLEX
            if (ConnectivityUtil.isAVPlex(storageSystem)) {
            	// Determine the proper serial number for the volume object provided.
                String clusterId = ConnectivityUtil.getVplexClusterForVarray(cgVol.getVirtualArray(), storageSystem.getId(), dbClient);
                
                for (String assemblyId : storageSystem.getVplexAssemblyIdtoClusterId().keySet()) {
                    if (storageSystem.getVplexAssemblyIdtoClusterId().get(assemblyId).equals(clusterId)) {
                        associatedStorageSystem = sourceInternalSiteName + " " + assemblyId;
                        break;
                    }
                }
                
            } else {
            	// Non-VPLEX
            	associatedStorageSystem = sourceInternalSiteName + " " + storageSystem.getSerialNumber();
            }        
        }
        
        return associatedStorageSystem;
    }

    /**
     * Returns a list of all the pools that the source varray can see that matches free capacity in the pools
     * 
     * @param orderedPools
     * @param capabilities
     * @param resourceCount
     * @param vpool
     * @return
     */
  
    private List<Recommendation> getRecommendedPools(List<StoragePool> storagePools, long size) {  
    	    	
    	long requestedCapacityInKB = getSizeInKB(size);
    	List<Recommendation> recommendations = new ArrayList<Recommendation>();
    	    	
    	_log.info(String.format("requested size in Bytes : %s, in KB %s", size, requestedCapacityInKB));
    	TreeMap<Integer, StoragePool> resourceCountStoragePoolMap = new TreeMap<Integer, StoragePool>(Collections.reverseOrder());
    	for(StoragePool storagePool : storagePools) {
    		Recommendation recommendation = new Recommendation();
    		int count = Math.abs((int) (storagePool.getFreeCapacity()/requestedCapacityInKB));
    		if (count > 0) {
    			resourceCountStoragePoolMap.put(count, storagePool);
    		}
    		recommendation.setSourcePool(storagePool.getId());
    		recommendation.setResourceCount(count);
    		recommendations.add(recommendation);
    	}    	

    	_log.info(String.format("\n# of resources of size %s that pools can accomodate:\n", size));
    	for (Map.Entry<Integer, StoragePool> poolMap : resourceCountStoragePoolMap.entrySet()) {
    		_log.info(String.format("StoragePool %s : Count possible %s\n", poolMap.getValue().getLabel(), poolMap.getKey()));
    	}
    	
    	  Collections.sort(recommendations, new Comparator<Recommendation>(){
  	        @Override
  	        public int compare(Recommendation a1, Recommendation a2) {
  	             return ComparisonChain.start()
  	                   .compare(a1.getResourceCount(), a2.getResourceCount())    	                  
  	                   .compare(a1.getResourceCount(), a1.getResourceCount()).result();
  	        }});
    	     
    	return recommendations;
    }
              
    /** 
     * @param candidatePoolsURI list of candidate storage pools for journal that is visible to the same RP site as the corresponding copy
     * @param capabilities capabilities
     * @param satisfiedResourceCount resource count that the journal policy needs to satisfy in its placement
     * @param recommendations recommendations
     * @param recommendation recommendation
     * @param journalPolicy journal policy
     * @return storagePool URI
     */
    private URI placeJournalStoragePool(Set<StoragePool> candidatePoolsBySize, VirtualPoolCapabilityValuesWrapper capabilities, 
    									int satisfiedResourceCount, RPProtectionRecommendation rpProtectionRecommendation, 
    									RPRecommendation rpRecommendation, String journalPolicy) {    	
    	
    	Set<URI> candidatePoolsUri = new HashSet<URI>();
    	//calculate the total size requested
    	long journalSizePerPolicy = RPHelper.getJournalSizeGivenPolicy(String.valueOf(capabilities.getSize()), journalPolicy, satisfiedResourceCount);
    	long totalCapacityRequestedInKB = getSizeInKB(journalSizePerPolicy * satisfiedResourceCount);
    	
    	Set<URI> poolsAlreadyInRecommendation = null;       	
    	//Eliminate storage pools in the candidate pool list that cannot satisfy this requested size.    
		for (StoragePool storagePool : candidatePoolsBySize) {
			if (storagePool.getFreeCapacity() > totalCapacityRequestedInKB) {
				candidatePoolsUri.add(storagePool.getId());
			}
		}

    	//Next, lets get all the pools that are already consumed by the recommendation(s)
    	poolsAlreadyInRecommendation = getAllPoolsInRecommendations(rpProtectionRecommendation, rpRecommendation);    	    	
		for(URI poolUri : candidatePoolsUri) {
    		if (poolsAlreadyInRecommendation.contains(poolUri))  {
    			continue;
    		}	    		
    		StoragePool pool = dbClient.queryObject(StoragePool.class, poolUri);
    		_log.info(String.format("found pool [%s] for journal", pool.getLabel()));
    		return poolUri;
		}    
    	
    	//TODO: We never had a way to handle the case when journal pools didnt have enough capacity for the given count # of volumes and journal policy
    	//Need to handle that better. This has been the case since day-1, so we need to fix that.
    	    	        
    	//If we got here, then we couldnt find a pool that is not already in the recommendation.
    	//return the first pool that is big enough (assuming its big enough for now, from the largest to smallest ordered pool list) to satisfy the request. 
    	
    	//candidatePoolsBySize is not empty in this method, since if there were no pools available, we wouldnt enter this method and fail much earlier.
    	URI poolUri = candidatePoolsBySize.iterator().next().getId();
    	StoragePool pool = dbClient.queryObject(StoragePool.class, poolUri);
    	_log.info(String.format("Default: found pool [%s] for journal", pool.getLabel()));
    	return  poolUri;    	
    }
        
    /**
     * @param resourceSize in bytes
     * @return size in KB
     */
    private static final long getSizeInKB(long resourceSize) {
        return (resourceSize%1024 == 0) ? resourceSize/1024 : resourceSize/1024 +1;
    }
    
    /** Returns a map of list of pools which are sorted based on the free capacity of the pool for each varray
     * @param protectionVarrayURIs 
     * @param sourceVarrayURI
     * @param vpool
     * @param capabilities
     * @return 
     */
    private Map<URI, List<StoragePool>> getVarrayStoragePoolsBySize(List<URI> protectionVarrayURIs, URI sourceVarrayURI, VirtualPool vpool, VirtualPoolCapabilityValuesWrapper capabilities, Project project) {
    	Map<URI,List<StoragePool>> varrayPoolsSortedBySize = new HashMap<URI, List<StoragePool>>(); 
    	
    	//start with all the protection varrays
    	for (URI varrayURI : protectionVarrayURIs) {    		
    		VirtualArray targetVarray = dbClient.queryObject(VirtualArray.class, varrayURI);
    		// Find the pools that apply based on the varray protection settings
        	VpoolProtectionVarraySettings settings = rpHelper.getProtectionSettings(vpool, targetVarray);
            // If there was no vpool specified with the protection settings, use the base vpool for this varray.
            VirtualPool targetVpool = vpool;
            if (settings.getVirtualPool()!=null) {
                targetVpool = dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
            }
            
            //Get all the storage pools visible to this varray and vpool
            List<StoragePool> matchingPools = getCandidatePools(targetVarray, targetVpool, null, null, project, capabilities, RPHelper.TARGET);
            List<Recommendation> targetPoolRecommendations = new ArrayList<Recommendation>() ;
            //Bharath TODO: remove this and the line above. getCandidatePoolsForSource(matchingPools, capabilities, capabilities.getResourceCount(), targetVarray, targetVpool, null, null);
            	                                       
            // If there are existing volumes in the RP consistency group, we need to make sure we only consider
            // those storage pools associated with CG protection system.
            ProtectionSystem protectionSystem = getCgProtectionSystem(capabilities.getBlockConsistencyGroup());
            
            if (protectionSystem != null) {
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
                _log.info("RecoverPoint consistency group {} is already tied to protection system {}.  Removing target storage pools " +
                		"that are not protected by this protection system.", cg.getLabel(), protectionSystem.getLabel());
                Iterator<StoragePool> matchingPoolsIter = matchingPools.iterator();
                StoragePool storagePool = null;
                
             // while (matchingPoolsIter.hasNext()) {
                for(Recommendation targetPoolRecommendation : targetPoolRecommendations)
                {               
                    //storagePool = matchingPoolsIter.next();
                	storagePool = dbClient.queryObject(StoragePool.class, targetPoolRecommendation.getSourcePool());
                    StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, targetPoolRecommendation.getSourceDevice()); 
                    StringSet associatedStorageSystems = protectionSystem.getAssociatedStorageSystemsWithString(storageSystem.getSerialNumber());
                    if (associatedStorageSystems.isEmpty()) {
                        _log.info("Removing storage pool {} from list of protection virtual array candidate pools.  " +
                        		"The storage pool is not protected by {}", storagePool.getLabel(), protectionSystem.getLabel());
                        matchingPoolsIter.remove();
                    }
                }
            }
            // We have all the pools now, sort them based on size.
            _log.info(String.format("Target varray [%s] sorted pools:", targetVarray.getLabel()));                         
            if (varrayPoolsSortedBySize.get(varrayURI) == null) {
            	varrayPoolsSortedBySize.put(varrayURI, new ArrayList<StoragePool>());            	
            }	            
            varrayPoolsSortedBySize.get(varrayURI).addAll(matchingPools);                                                		   
    	}
    	return varrayPoolsSortedBySize;
    }
            
    /** Find a pool for the target volume. The candidateTargetPoolURI will be the preferred pool unless that pool is already used/in the recommendation(s).
     * If the candidateTargetPoolURI is in the recommendation, then an available pool that is not the source pool or source journal pool will be used. 
     * If there is no pool that match the criteria from above, then the target pool will be the same as the source pool. 
     * 
     * @param candidateTargetPoolURI
     * @param candidatePoolsURI
     * @param capabilities
     * @param recommendations
     * @param recommendation
     * @param protectionVpool
     * @return
     */
    private List<Recommendation> getTargetPoolsRecommendation(List<StoragePool> candidatePools, VirtualPoolCapabilityValuesWrapper capabilities, 
    								RPProtectionRecommendation rpProtectionRecommendation, RPRecommendation rpRecommendation, VirtualArray varray, 
    								VirtualPool protectionVpool) { 
    	//TODO (Brad/Bharath): ChangeVPool doesnt add any new targets. If new targets are requested as part of the changeVpool, then this code needs to be enhanced
    	//to be able to handle that. 
    	
    	List<Recommendation> targetPoolRecommendation = new ArrayList<Recommendation>();
    	
    	//1, lets get all the pools that are already consumed by the recommendation(s)
    	Set<URI> recommendationPoolsUriList = getAllPoolsInRecommendations(
    			rpProtectionRecommendation, rpRecommendation);
    	
    	List<StoragePool> recommendationPoolsList = new ArrayList<StoragePool>();
    	for(URI poolUri : recommendationPoolsUriList) {
    		recommendationPoolsList.add(dbClient.queryObject(StoragePool.class, poolUri));
    	}
    	    	
    	//2. Get all pools matching varray and capabilities
    	List<StoragePool> matchingPools = blockScheduler.getMatchingPools(varray, protectionVpool, capabilities);
    	List<StoragePool> matchingPoolsNotInRecommendation = new ArrayList<StoragePool>();
    	
    	//3. Filter the pools to not consider already recommended pools
    	Iterator<StoragePool> poolsIterator = matchingPools.iterator();
    	while (poolsIterator.hasNext()) {
    		StoragePool matchingPool = (StoragePool)poolsIterator.next();
    		if (recommendationPoolsUriList.contains(matchingPool.getId())) {
    			continue;
    		}
    		
    		matchingPoolsNotInRecommendation.add(matchingPool);
    	}
    	
    	//4. find recommendation solution with pools from step 3 first.
    	if (!matchingPoolsNotInRecommendation.isEmpty()) {
    		targetPoolRecommendation = blockScheduler.getRecommendationsForPools(varray.getId().toString(), matchingPoolsNotInRecommendation, capabilities);       	
    	}
 		
		if (!targetPoolRecommendation.isEmpty()) {
			return targetPoolRecommendation;
		}
		
    	//5. If we couldnt find unique pools not already in recommendation, 
		//find a pool that is already being used in the recommendation, we need a pool that was used as a Target storage pool.
		//we dont want to use a pool that was selected previously for Source/SourceJournal.
		
		/* Bharath - TODO Fix
		List<StoragePool> poolsAlreadyRecommended = getAllPoolsInRecommendations(rpProtectionRecommendation, rpRecommendation);
	
		if (!poolsAlreadyRecommended.isEmpty()) {
    		targetPoolRecommendation = blockScheduler.getRecommendationsForPools(varray.getId().toString(), recommendedTargetPoolList, capabilities);       	
    	}
 		
		if (!targetPoolRecommendation.isEmpty()) {
			return targetPoolRecommendation;
		}	
		*/
		   			
		return targetPoolRecommendation;
    }
    
    /** Find a pool for the target volume. The candidateTargetPoolURI will be the preferred pool unless that pool is already used/in the recommendation(s).
     * If the candidateTargetPoolURI is in the recommendation, then an available pool that is not the source pool or source journal pool will be used. 
     * If there is no pool that match the criteria from above, then the target pool will be the same as the source pool. 
     * 
     * @param candidateTargetPoolURI
     * @param candidatePoolsURI
     * @param capabilities
     * @param recommendations
     * @param recommendation
     * @param protectionVpool
     * @return
        private Recommendation getTargetPool(List<StoragePool> candidatePools, long size, int requestedResourceCount, 
    								  List<Recommendation> recommendations, RPProtectionRecommendation recommendation, VirtualArray varray, 
    								  VirtualPool protectionVpool) { 
    	//TODO (Brad/Bharath): ChangeVPool doesnt add any new targets. If new targets are requested as part of the changeVpool, then this code needs to be enhanced
    	//to be able to handle that.
    	
    	Recommendation poolRecommendation = new Recommendation(); 
    	int satisfiedResourceCount = 0;
    	
    	//1. Get all pools that are already recommended
    	Set<URI> recommendationPoolsUriList = getAllPoolsInRecommendations(
    			recommendations, recommendation);    	
    	List<StoragePool> recommendationPoolsList = new ArrayList<StoragePool>();
    	for(URI poolUri : recommendationPoolsUriList) {
    		recommendationPoolsList.add(dbClient.queryObject(StoragePool.class, poolUri));
    	}
    	    	
    	//2. Build a list of pools that are not in recommendation already.
    	List<StoragePool> matchingPoolsNotInRecommendation = new ArrayList<StoragePool>();    	
    	Iterator<StoragePool> candidatePoolsIter = candidatePools.iterator();
    	while (candidatePoolsIter.hasNext()) {
    		StoragePool matchingPool = (StoragePool)candidatePoolsIter.next();
    		if (recommendationPoolsUriList.contains(matchingPool.getId())) {
    			continue;
    		}    		
    		matchingPoolsNotInRecommendation.add(matchingPool);
    	}
    
    	//3. Arrange the remaining pools by resource count. If this list is non-empty, return the first pool in this list. 
    	//   The first pool will be the pool that can hold the largest amount of resources. 
    	List<Recommendation> matchingPoolsRecommendation = getRecommendedPools(matchingPoolsNotInRecommendation, size);
    	if (!matchingPoolsRecommendation.isEmpty()) {
    		return matchingPoolsRecommendation.get(0);

    	}
		
    	//4. If we couldnt find a pool from step 3 above, we need to determine from list of pools already used in recommendation.
    	// Start with all the pools that have been chosen for RP target/target journal first. If that fails to find a match, then pick from source/source journal	
		List<StoragePool> recommendedTargetPoolList = new ArrayList<StoragePool>();
		List<StoragePool> recommendedSourcePoolList = new ArrayList<StoragePool>();
		for (StoragePool recommendationPool : recommendationPoolsList) {
			if (recommendationPool.getId().equals(recommendation.getSourcePool()) || 
					recommendationPool.getId().equals(recommendation.getSourceJournalStoragePool())){
				recommendedSourcePoolList.add(recommendationPool);
				continue;
			}						
			recommendedTargetPoolList.add(recommendationPool);
		}   
		
		// Arrange the target recommended pool list
		List<Recommendation> recommendedPools= getRecommendedPools(recommendedTargetPoolList, size);		
		if (recommendedPools.isEmpty()) {
			recommendedPools = getRecommendedPools(recommendedSourcePoolList, size);
		}
		
		if (!recommendedPools.isEmpty()) {
			return recommendedPools.get(0);
//			int count = recommendedPools.get(0).getResourceCount();
//    		if (count > 0) {
//    			satisfiedResourceCount = (count > requestedResourceCount) ? requestedResourceCount : count;	    	
//    		}
//    		poolRecommendation.setResourceCount(satisfiedResourceCount);
//    		poolRecommendation.setSourcePool(recommendedPools.get(0).getSourcePool());
//    		return poolRecommendation ;
		}		
		return null;
    }*/
    
    
	/** Returns a list of all the pools that are already in recommendations as well as part of the current recommendation that we are populating. 
	 * @param recommendations
	 * @param recommendation
	 * @return
	 */
	private Set<URI> getAllPoolsInRecommendations(RPProtectionRecommendation rpProtectionRecommendation,
							RPRecommendation rpRecommendation) {
		Set<URI> poolsAlreadyInRecommendation;
		
		if (rpProtectionRecommendation != null && rpProtectionRecommendation.getSourceRecommendations() != null &&
				!rpProtectionRecommendation.getSourceRecommendations().isEmpty()) {
			poolsAlreadyInRecommendation = getPoolsInAllRecommendations(rpProtectionRecommendation);
		} else {
    		poolsAlreadyInRecommendation = getPoolsInThisRecommendation(rpRecommendation); 
    	}
		
		return poolsAlreadyInRecommendation;
	}
    
    
    /** List of pools that are already used and in the recommendations
     * 
     * @param recommendations
     * @return
     */
    private Set<URI> getPoolsInAllRecommendations(RPProtectionRecommendation rpProtectionRecommendation)  {
    	Set<URI> poolsAlreadyInRecommendation = new HashSet<URI>();
    	
    	if (rpProtectionRecommendation.getSourceJournalRecommendation() != null) {
    		poolsAlreadyInRecommendation.add(rpProtectionRecommendation.getSourceJournalRecommendation().getSourcePool());
    	}
    	
    	if (rpProtectionRecommendation.getStandbyJournalRecommendation() != null) {
    		poolsAlreadyInRecommendation.add(rpProtectionRecommendation.getStandbyJournalRecommendation().getSourcePool());
    	}
    	
    	if (rpProtectionRecommendation.getTargetJournalRecommendations() != null) {
    		for (RPRecommendation targetJournalRecommendation : rpProtectionRecommendation.getTargetJournalRecommendations()) {
    			poolsAlreadyInRecommendation.add(targetJournalRecommendation.getSourcePool());
    		}
    	}
    	
    	for (RPRecommendation rpRecommendation : rpProtectionRecommendation.getSourceRecommendations()) {    		
    		poolsAlreadyInRecommendation.add(rpRecommendation.getSourcePool());    		    	    		 
    		 if (rpRecommendation.getTargetRecommendations() != null) {
    		 for (Recommendation targetRecommendation : rpRecommendation.getTargetRecommendations()){    			 	    			
    				 poolsAlreadyInRecommendation.add(targetRecommendation.getSourcePool());    				 
				 }
    		 }
    	}    	
	    
    	return poolsAlreadyInRecommendation;
    }
    
    
    /** List of pools consumed by this recommendation. This recommendation is still being processed, hence the pools selected as part of this recommendation wouldnt
     * have made it into the recommendations. Hence, we need this call to only fetch the list of pools used in this recommendation.
     * 
     * @param recommendation
     * @return
     */
    private Set<URI> getPoolsInThisRecommendation(RPRecommendation rpRecommendation)  {
    	Set<URI> poolsAlreadyInRecommendation = new HashSet<URI>();    	
    	    	
    	if(rpRecommendation != null){
	    	poolsAlreadyInRecommendation.add(rpRecommendation.getSourcePool());	
    	}
        	   
    	return poolsAlreadyInRecommendation;
    }
    
	/**
     * Find the internal site names that qualify for this pool and protection system and varrays.
     * Use the RP Topology to ensure that you disqualify those internal site names (clusters) that
     * couldn't possibly protect to all of the varrays.
     * 
     * @param srcPool source storage pool
	 * @param candidateProtectionSystem candidate protection system
	 * @param sourceVarray source virtual array
	 * @param protectionVarrays all of the target varrays
     * @return set of internal site names that are valid
     */
    private List<String> getCandidateVisibleStorageSystems(StoragePool srcPool,
			ProtectionSystem candidateProtectionSystem,
			VirtualArray sourceVarray, List<VirtualArray> protectionVarrays, boolean isRPVPlex) {
        _log.info("RP Placement: Trying to find the RP Site candidates for the source...");
        
    	Set<String> validAssociatedStorageSystems = new HashSet<String>();
    	
    	Set<URI> vplexs = null;
    	// If this is an RP+VPLEX or MetroPoint request, we need to find the VPLEX(s). We are only interested in 
        // connectivity between the RP Sites and the VPLEX(s). The backend Storage Systems are irrelevant in this case.
    	if (isRPVPlex) {
    	    _log.info("RP Placement: This is an RP+VPLEX/MetroPoint request.");
    	    // Find the VPLEX(s) associated to the Storage System (derived from Storage Pool) and varray
            vplexs = ConnectivityUtil
                .getVPlexSystemsAssociatedWithArray(dbClient, srcPool.getStorageDevice(),
                    new HashSet<String>(Arrays.asList(sourceVarray.getId().toString())), null);
        }
    	
    	for (String associatedStorageSystem : candidateProtectionSystem.getAssociatedStorageSystems()) {
    	    URI storageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem), dbClient, StorageSystemType.BLOCK);
            
    	    // If this is a RP+VPLEX or MetroPoint request check to see if the associatedStorageSystem is
    	    // in the list of valid VPLEXs, if it is, add the internalSiteName.     	        	    
    	    if (vplexs != null && !vplexs.isEmpty()) {
    	        if (vplexs.contains(storageSystemURI)) {
    	            validAssociatedStorageSystems.add(associatedStorageSystem);
    	        }
    	        // For RP+VPLEX or MetroPoint we only want to check the available VPLEX(s).
                continue;
    	    }
    	    
    		if (storageSystemURI.equals(srcPool.getStorageDevice())) {
    		    validAssociatedStorageSystems.add(associatedStorageSystem);
    		}
    	}
    	
    	// Check topology to ensure that each site in the list is capable of protecting to protectionVarray.size() of sites.
    	// It is assumed that a site can protect to itself.
    	_log.info("Checking for qualifying source RP cluster, given connected storage systems");
    	Set<String> removeAssociatedStorageSystems = new HashSet<String>(); 
    	for (String validAssociatedStorageSystem : validAssociatedStorageSystems) {
    	    String internalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(validAssociatedStorageSystem);
    		if (candidateProtectionSystem.canProtectToHowManyClusters(internalSiteName) < protectionVarrays.size()) {
    			removeAssociatedStorageSystems.add(validAssociatedStorageSystem);
    		}    		
    		else if (!isInternalSiteAssociatedWithVarray(sourceVarray, internalSiteName, candidateProtectionSystem)) {
    		    // Now remove any RP clusters that aren't available in the VSAN (network) associated with the varray
                removeAssociatedStorageSystems.add(validAssociatedStorageSystem);
            }
    	}    	
    	
    	validAssociatedStorageSystems.removeAll(removeAssociatedStorageSystems);
    	
    	if (validAssociatedStorageSystems.isEmpty()) {
    	    URI storageSystemURI = srcPool.getStorageDevice();
    	    if (vplexs != null && !vplexs.isEmpty()) {
    	        // For logging purposes just find the first VPLEX 
    	        storageSystemURI = vplexs.iterator().next();
    	    }
    		_log.warn(String.format("RP Placement: There is no RP cluster associated with storage system %s on protection system %s capable of protecting to all %d varrays",
    		        storageSystemURI, candidateProtectionSystem.getNativeGuid(), protectionVarrays.size()));
    	}
    	
    	// Sort the valid associated storage systems by visibility to the arrays already
    	return reorderAssociatedStorageSystems(candidateProtectionSystem, validAssociatedStorageSystems, sourceVarray);
	}

	/**
	 * Get the candidate internal site names associated with this storage pool (it's storage system) and the
	 * protection system.
	 * 
	 * @param protectionDevice protection system
	 * @param sourceInternalSiteName 
	 * @param targetPool target storage pool.
	 * @return
	 */
	private List<String> getCandidateTargetVisibleStorageSystems(URI protectionDevice, VirtualArray targetVarray,
			String sourceInternalSiteName, StoragePool targetPool, boolean isRPVPlex) {
	    _log.info("RP Placement: Trying to find the RP cluster candidates for the target");
	    
    	List<String> validAssociatedStorageSystems = new ArrayList<String>();
		ProtectionSystem protectionSystem = dbClient.queryObject(ProtectionSystem.class, protectionDevice);
		
		Set<URI> vplexs = null;
        // If this is an RP+VPLEX or MetroPoint request, we need to find the VPLEX(s). We are only interested in 
		// connectivity between the RP Sites and the VPLEX(s). The backend Storage Systems are irrelevant in this case. 
        if (isRPVPlex) {
            _log.info("RP Placement: This is an RP+VPLEX/MetroPoint request.");
            // Find the VPLEX(s) associated to the Storage System (derived from Storage Pool) and varray
           vplexs = ConnectivityUtil.getVPlexSystemsAssociatedWithArray(dbClient, targetPool.getStorageDevice(),
               new HashSet<String>(Arrays.asList(targetVarray.getId().toString())), null);           
        }		
		
    	for (String associatedStorageSystem : protectionSystem.getAssociatedStorageSystems()) {
    	    boolean validAssociatedStorageSystem = false;
    	    URI storageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem), dbClient, StorageSystemType.BLOCK);
            String internalSiteName =  ProtectionSystem.getAssociatedStorageSystemSiteName(associatedStorageSystem);
    	    
    	    // If this is a RP+VPLEX or MetroPoint request
            if (vplexs != null && !vplexs.isEmpty()) {
                // If this is a RP+VPLEX or MetroPoint request check to see if the associatedStorageSystem is
                // in the list of valid VPLEXs, if it is, add the internalSiteName. 
                if (vplexs.contains(storageSystemURI)) {
                    validAssociatedStorageSystem = true;
                }                
            }
            else if (storageSystemURI.equals(targetPool.getStorageDevice())) {
            	validAssociatedStorageSystem = true;
            }
    	    
    		if (validAssociatedStorageSystem) {    		          		    
    			if (!validAssociatedStorageSystems.contains(associatedStorageSystem)) {
    				if (protectionSystem.canProtect(sourceInternalSiteName, internalSiteName)) {    				    				    
    				    validAssociatedStorageSystems.add(associatedStorageSystem);
        				_log.info("RP Placement: Found that we can use {} -> {} because they have connectivity.", 
        				        ((protectionSystem.getRpSiteNames() != null) ? protectionSystem.getRpSiteNames().get(sourceInternalSiteName) : sourceInternalSiteName), 
        				        ((protectionSystem.getRpSiteNames() != null) ? protectionSystem.getRpSiteNames().get(internalSiteName) : internalSiteName));
    				} else {
    					_log.info("RP Placement: Found that we cannot use {} -> {} due to lack of connectivity.", sourceInternalSiteName, 
    					        internalSiteName);
    				}
    			}
    		}
    	}
    	
    	// If the source internal site name is in this list, make it first in the list.  This helps to prefer a local site to a local varray, if it exists.
    	int index = -1;
    	String preferedAssociatedStorageSystem = null;
    	for (String validAssociatedStorageSystem : validAssociatedStorageSystems) {
    	    String internalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(validAssociatedStorageSystem);
    		if (internalSiteName.equals(sourceInternalSiteName)) {
    			index = validAssociatedStorageSystems.indexOf(validAssociatedStorageSystem);
    			preferedAssociatedStorageSystem = validAssociatedStorageSystem;
    		}
    	}
    	
    	if (index > 0) {
    		String swapSiteName = validAssociatedStorageSystems.get(0);
    		validAssociatedStorageSystems.set(index, swapSiteName);
    		validAssociatedStorageSystems.set(0, preferedAssociatedStorageSystem);
    	}
    	    	
    	Set<String> removeAssociatedStorageSystems = new HashSet<String>(); 
    	for (String validAssociatedStorageSystem : validAssociatedStorageSystems) {
    	    String internalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(validAssociatedStorageSystem);
        	_log.info("Checking for qualifying target RP cluster, given connected storage systems");
    		if (!isInternalSiteAssociatedWithVarray(targetVarray, internalSiteName, protectionSystem)) {
    		    // Now remove any internal site that contains RP clusters that are not available in the VSAN (network) associated with the varray
    			removeAssociatedStorageSystems.add(validAssociatedStorageSystem);
    		}
    	}

    	validAssociatedStorageSystems.removeAll(removeAssociatedStorageSystems);
    	
    	if (validAssociatedStorageSystems.isEmpty()) {
            URI storageSystemURI = targetPool.getStorageDevice();
            if (vplexs != null && !vplexs.isEmpty()) {
                // For logging purposes just find the first VPLEX 
                storageSystemURI = vplexs.iterator().next();
            }
            _log.warn(String.format("RP Placement: There is no RP cluster associated with storage system %s on protection system %s",
                    storageSystemURI, protectionSystem.getNativeGuid()));
        }

    	// Sort the valid associated storage systems by visibility to the arrays already
    	return reorderAssociatedStorageSystems(protectionSystem, validAssociatedStorageSystems, targetVarray);
	}

	/**
	 * Reorder the storage systems/cluster list to prefer site/cluster pairs that are already pre-configured to be 
	 * visible to each other.  The ones that aren't pre-configured are put at the end of the list.
	 * 
	 * @param protectionSystem protection system
	 * @param validAssociatedStorageSystems list of cluster/array pairs that are valid for a source/target
	 * @param varray virtual array to filter on.
	 * @return list of sorted cluster/array pairs.
	 */
	private List<String> reorderAssociatedStorageSystems(ProtectionSystem protectionSystem,
														 Collection<String> validAssociatedStorageSystems, 
														 VirtualArray varray) {
		
		Map<String, Boolean> serialNumberInVarray = new HashMap<>();
		
		// Create a sorted list of storage systems, with splitter-visible arrays at the top of the list
    	List<String> sortedVisibleStorageSystems = new ArrayList<String>();
    	for (String assocStorageSystem : validAssociatedStorageSystems) {
    		String assocSerialNumber    = ProtectionSystem.getAssociatedStorageSystemSerialNumber(assocStorageSystem);
    		String rpCluster            = ProtectionSystem.getAssociatedStorageSystemSiteName(assocStorageSystem);

    		// Calling isStorageArrayInVarray is very expensive (and something we don't want to do going forward)
    		// So to minimize the calls, only call for each serial number once and store the result.
    		if (!serialNumberInVarray.containsKey(assocSerialNumber)) {
    			if (isStorageArrayInVarray(varray, assocSerialNumber)) {
    				serialNumberInVarray.put(assocSerialNumber, Boolean.TRUE);
    			} else {
    				serialNumberInVarray.put(assocSerialNumber, Boolean.FALSE);
    			}
    		}
    			
    		// If this serial number/storage array isn't in our varray, don't continue.
    		if (!serialNumberInVarray.get(assocSerialNumber)) {
    			continue;
    		}
    		
    		// Is this array seen by any RP cluster already according to the RP?  If so, put it to the front of the list
    		if (protectionSystem.getSiteVisibleStorageArrays() != null) {
    			for (Map.Entry<String, AbstractChangeTrackingSet<String>> clusterStorageSystemsEntry : protectionSystem.getSiteVisibleStorageArrays().entrySet()) {
    				if (rpCluster.equals(clusterStorageSystemsEntry.getKey())) {
    					for (String serialNumber : clusterStorageSystemsEntry.getValue()) {
    						if (assocSerialNumber.equals(serialNumber)) {
    							sortedVisibleStorageSystems.add(rpCluster + " " + serialNumber);
    						}
    					}
    				}
    			}
    		}
    	}

	    // If there is no RP-array list at all or it's not currently a splitter, the array is added to the list anyway.
    	// It's just added further down the line.
    	for (String assocStorageSystem : validAssociatedStorageSystems) {
    		String assocSerialNumber = ProtectionSystem.getAssociatedStorageSystemSerialNumber(assocStorageSystem);
    		String rpCluster         = ProtectionSystem.getAssociatedStorageSystemSiteName(assocStorageSystem);

    		if (!sortedVisibleStorageSystems.contains(rpCluster + " " + assocSerialNumber) &&
    			 serialNumberInVarray.get(assocSerialNumber)) {
    			sortedVisibleStorageSystems.add(rpCluster + " " + assocSerialNumber);
    		}
    	}

    	return sortedVisibleStorageSystems;
	}

    /**
     * Is the serial number associated with the storage array in the same network(s) as the virtual array?
     * 
     * @param varray virtual array 
     * @param serialNumber serial number of an array; for VPLEX we've broken down the serial numbers by VPLEX cluster.
     * @return true if it's in the virtual array.
     */
    private boolean isStorageArrayInVarray(VirtualArray varray, String serialNumber) {
    	if (serialNumber == null) {
    		return false;
    	}
    	
    	StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, ConnectivityUtil.findStorageSystemBySerialNumber(serialNumber, dbClient, StorageSystemType.BLOCK));
    	if (storageSystem == null) {
    		return false;
    	}
    	
    	// Special check for VPLEX
        if (ConnectivityUtil.isAVPlex(storageSystem)) {           
            String clusterId = storageSystem.getVplexAssemblyIdtoClusterId().get(serialNumber);            
            
            // Check to see if this varray and VPLEX cluster match up, if so we're Ok to use it.
            return VPlexUtil.checkIfVarrayContainsSpecifiedVplexSystem(varray.getId().toString(), clusterId, storageSystem.getId(), dbClient);
        }
    	
    	// For a single serial number, we wouldn't be in this code if it weren't in the varray, so we keep things
        // simple and return true.
        if (storageSystem.getSerialNumber().equals(serialNumber)) {
            return true;
        }
    	
    	return false;
	}

	/**
	 * Is the cluster associated with the virtual array (and its networks) configured?
	 * 
	 * @param sourceVarray source varray
	 * @param candidateProtectionSystem protection system
	 * @return
	 */
	private boolean isInternalSiteAssociatedWithVarray(VirtualArray varray, String internalSiteName, ProtectionSystem candidateProtectionSystem) {
	    String translatedInternalSiteName = candidateProtectionSystem.getRpSiteNames().get(internalSiteName);
		if (candidateProtectionSystem == null
		        || candidateProtectionSystem.getSiteInitiators() == null) { 
			_log.warn("Disqualifying use of RP Cluster " + translatedInternalSiteName + " because it was not found to have any discovered initiators.  Re-run discovery.");
		    return false;
		}
		
		// Check to see if this RP Cluster is assigned to this virtual array.			
		StringSetMap siteAssignedVirtualArrays = candidateProtectionSystem.getSiteAssignedVirtualArrays();
		if (siteAssignedVirtualArrays != null
		        && !siteAssignedVirtualArrays.isEmpty()) {
		    
		    // Store a list of the valid internal sites for this varray. 
		    List<String> associatedInternalSitesForThisVarray = new ArrayList<String>();
		    
		    // Loop over all entries. If the associatedInternalSitesForThisVarray remains empty, ALL internal sites are valid for this varray.
		    for (Map.Entry<String, AbstractChangeTrackingSet<String>> entry : siteAssignedVirtualArrays.entrySet()) {
		        // Check to see if this entry contains the varray
		        if (entry.getValue().contains(varray.getId().toString())) {
		            // This varray has been explicitly associated to this internal site
		            String associatedInternalSite = entry.getKey();
		            _log.info(String.format("Varray [%s] has been explicitly associated with RP Cluster [%s]", 
		                                        varray.getLabel(), 
		                                        candidateProtectionSystem.getRpSiteNames().get(associatedInternalSite)));
		            associatedInternalSitesForThisVarray.add(associatedInternalSite);
		        }
		    }

		    // If associatedInternalSitesForThisVarray is not empty and this internal site is not in the list, 
		    // return false as we can't use it this internal site. 
		    if (!associatedInternalSitesForThisVarray.isEmpty() && !associatedInternalSitesForThisVarray.contains(internalSiteName)) {
		        // The user has isolated this varray to specific internal sites and this is not one of them.
		        _log.info("Disqualifying use of RP Cluster " + translatedInternalSiteName + " because there are assigned associations to varrays and varray " + varray.getLabel() + " is not one of them.");
		        return false;
		    }
		}
		
		for (String endpoint : candidateProtectionSystem.getSiteInitiators().get(internalSiteName)) {
			if (endpoint == null) {
			    continue;
			}
			
			if (rpHelper.isInitiatorInVarray(varray, endpoint)) {
				_log.info("Qualifying use of RP Cluster " + translatedInternalSiteName + " because it is not excluded explicitly and there's connectivity to varray " + varray.getLabel());
				return true;
			}
		}
		
		_log.info("Disqualifying use of RP Cluster " + translatedInternalSiteName + " because it was not found to be connected to a Network that belongs to varray " + varray.getLabel());
		return false;
	}

	/**
     * Placement method that assembles recommendation objects based on the vpool and protection varrays.
     * Recursive: peels off one protectionVarray to hopefully assemble one Protection object within the recommendation object, then calls itself 
     * with the remainder of the protectionVarrays.  If it fails to find a Protection for that protectionVarray, it returns failure and puts the 
     * protectionVarray back on the list.
     * 
     * @param varrayOrderedPoolList the sorted protection varray pool mappings.
     * @param recommendations the list of all recommendations.
     * @param recommendation the recommendation for which we are attempting to find protection placement.
     * @param varray the source virtual array.
     * @param vpool the source virtual pool.
     * @param protectionVarrays the list of protection virtual arrays.
     * @param capabilities the capability params.
     * @param requestedCount the resource count.
     * @param metroPointType the MetroPoint type.
     * @param primaryRecommendation the primary recommendation in the case of a MetroPoint request.  This will be populated only
     *                              when the request pertains to the secondary recommendation, so we can pull information from the
     *                              primary recommendation.
     * @return true if a protection recommendation can be found, false otherwise.
     */
	private boolean findSolution(RPProtectionRecommendation rpProtectionRecommendation, RPRecommendation rpRecommendation,
			VirtualArray varray, VirtualPool vpool, List<VirtualArray> protectionVarrays, VirtualPoolCapabilityValuesWrapper capabilities, 
			int requestedCount, boolean isMetroPoint, RPRecommendation primaryRecommendation, Project project) {
			
		if (protectionVarrays.isEmpty()) {
			_log.info("Could not find target solution because there are no protection virtual arrays specified.");
			return false;
		}
    	// Find the virtual pool that applies to this protection virtual array		
    	VirtualArray protectionVarray = protectionVarrays.get(0);    	    
    	placementStatus.getProcessedProtectionVArrays().put(protectionVarray.getId(), true);
    	
    	// Find the pools that apply to this virtual
    	VpoolProtectionVarraySettings protectionSettings = rpHelper.getProtectionSettings(vpool, protectionVarray);
        // If there was no vpool specified with the protection settings, use the base vpool for this varray.
        VirtualPool protectionVpool = vpool;
        if (protectionSettings.getVirtualPool() != null) {
            protectionVpool = dbClient.queryObject(VirtualPool.class, protectionSettings.getVirtualPool());          
        }

        _log.info("Determing placement on protection varray : " + protectionVarray.getLabel());
        
        // Find matching pools for the protection varray
        VirtualPoolCapabilityValuesWrapper newCapabilities = new VirtualPoolCapabilityValuesWrapper(capabilities);
        newCapabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, requestedCount);       
        List<StoragePool> candidateTargetPools = getCandidatePools(protectionVarray, protectionVpool, null, null, project, newCapabilities, RPHelper.TARGET);
        
        // Get pool recommendations. each recommendation also specifies the resource count that the pool can satisfy based on the size requested.
        List<Recommendation> targetPoolRecommendations = getRecommendedPools(candidateTargetPools, newCapabilities.getSize());
       
       	if (targetPoolRecommendations.isEmpty()) {
    	   //TODO: Error out here
       	}    
                     
        VirtualArray targetJournalVarray = protectionVarray;
        if (!NullColumnValueGetter.isNullURI(protectionSettings.getJournalVarray())) {
        	 targetJournalVarray = dbClient.queryObject(VirtualArray.class, protectionSettings.getJournalVarray());
        }
        
        VirtualPool targetJournalVpool = dbClient.queryObject(VirtualPool.class, protectionVpool.getId());
        if(!NullColumnValueGetter.isNullURI(protectionSettings.getJournalVpool())) {
        	targetJournalVpool = dbClient.queryObject(VirtualPool.class, protectionSettings.getJournalVpool());
        }
               
        List<StoragePool> candidateJournalPools = getCandidatePools(targetJournalVarray, targetJournalVpool, null, null, project, newCapabilities, RPHelper.JOURNAL);
        if (candidateJournalPools.isEmpty()) {
        	//TODO: Error out here, no journal storage pool found
        }
        _log.info("Candidate Target Storage pools: ");
        printSortedStoragePools(candidateTargetPools);
        
        _log.info("Candidate Journal Storage pools: ");
        printSortedStoragePools(candidateJournalPools);
                
        Iterator<Recommendation> targetPoolRecommendationsIter = targetPoolRecommendations.iterator();
        // TODO: Distill the storage pools into their storage systems so we can determine protection system and internal site name connectivity        
        while(targetPoolRecommendationsIter.hasNext()) {       
	        	Recommendation targetPoolRecommendation = targetPoolRecommendationsIter.next();
	        	StoragePool candidateTargetPool = dbClient.queryObject(StoragePool.class, targetPoolRecommendation.getSourcePool());	        
	        	List<String> associatedStorageSystems = getCandidateTargetVisibleStorageSystems(rpProtectionRecommendation.getProtectionDevice(), 
	        	                                                                                protectionVarray, rpRecommendation.getInternalSiteName(), 
	        	                                                                                candidateTargetPool, 
	        	                                                                                VirtualPool.vPoolSpecifiesHighAvailability(protectionVpool));
	        	
	        	if (associatedStorageSystems.isEmpty()) {
	        		_log.info("Solution cannot be found using target pool " + candidateTargetPool.getLabel() + " there is no connectivity to rp cluster sites.");
	        		updatePoolList(candidateTargetPools, candidateTargetPool);
	        		continue;
	        	}
		        	
		        // We want to find an internal site name that isn't already in the solution
	        	for (String associatedStorageSystem : associatedStorageSystems) {
	        	    String targetInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(associatedStorageSystem);
	        	    URI targetStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(
	        	    								ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem), 
	        	    								dbClient, StorageSystemType.BLOCK);
	        	    
	        	    ProtectionType protectionType = null;
	        		if (!rpProtectionRecommendation.containsTargetInternalSiteName(targetInternalSiteName)) {        		
	        		     protectionType = null;
	        		    
	    		    // MetroPoint has been specified so process the MetroPoint targets accordingly.
	    		    if (isMetroPoint) {
	                    if (targetInternalSiteName.equals(rpRecommendation.getInternalSiteName())) {
	                        // A local protection candidate.
	                        if (isMetroPointProtectionSpecified(rpProtectionRecommendation, ProtectionType.LOCAL)) {
	                            // We already have protection specified for the local type
	                            // so continue onto the next candidate RP site.
	                            continue;
	                        }
	                        
	                        // Add the local protection
	                        protectionType = ProtectionType.LOCAL;
	                	} else if (!targetInternalSiteName.equals(rpRecommendation.getInternalSiteName())) {
	                        if (isMetroPointProtectionSpecified(rpProtectionRecommendation, ProtectionType.REMOTE)) {
	                        	// We already have remote protection specified so continue onto the next 
	                        	// candidate RP site.
	                            continue;
	                        } else {
	                        	if (primaryRecommendation != null) {
	                                String primaryTargetInternalSiteName = getMetroPointRemoteTargetRPSite(rpProtectionRecommendation);
	                                if (primaryTargetInternalSiteName != null 
	                                        && !targetInternalSiteName.equals(primaryTargetInternalSiteName))  {
	                                    // We want the secondary target site to be different than the secondary source
	                                    // site but the same as the primary target site.
	                                    continue;
	                                }
	                            }
	                        	
	                        	// Add the remote protection
	                            protectionType = ProtectionType.REMOTE;
	                        	}
	                    	}
	    		    	}
	        		} 	        		    
	    			// Check to make sure the RP site is connected to the varray
	        		URI psUri = rpProtectionRecommendation.getProtectionDevice();
	    			if (!isRpSiteConnectedToVarray(
					        targetStorageSystemURI, psUri, targetInternalSiteName, protectionVarray)) {
							_log.info(String.format("RP Placement: Disqualified RP site [%s] because its initiators are not in a network configured for use by the virtual array [%s]",
		    									targetInternalSiteName, protectionVarray.getLabel()));
							updatePoolList(candidateTargetPools, candidateTargetPool);;
		    				continue;
	    			}
	    			
//	    			_log.info(String.format("RP Placement identified RP Site [%s], verify if journal storage has visiblity to the same RP Site",  targetInternalSiteName));        			
//	    			Set<StoragePool> rpSiteVisibleJournalPools = filterJournalPoolsByRPSiteConnectivity(
//	 					   												candidateJournalPools, psUri, 
//	 					   												targetInternalSiteName, targetJournalVarray, targetJournalVpool);     			   										 
//					if (null == rpSiteVisibleJournalPools) {
//						_log.info(String.format("RP Journal Placement: Disqualified RP site [%s] because its initiators are not in a network configured for use by the virtual array [%s]", 
//														targetInternalSiteName, targetJournalVarray.getLabel()));
//						continue;
//					}
						 					
					_log.info(String.format("RP Placement : Choosing RP Site %s for target on varray %s", targetInternalSiteName, protectionVarray.getLabel()));	    			
	    			// Maybe make a topology check in here?  Or is the source topology check enough?        			
	    			// Add the protection object to the recommendation
	    			RPRecommendation targetRecommendation = new RPRecommendation();
	    			if (protectionType != null) {
	    				targetRecommendation.setProtectionType(protectionType);
	    			}
	    			
	    			targetRecommendation.setVirtualPool(protectionVpool);
	    			targetRecommendation.setVirtualArray(protectionVarray.getId());
	    			targetRecommendation.setInternalSiteName(targetInternalSiteName);
	    			StorageSystem targetStorageSystem = dbClient.queryObject(StorageSystem.class, targetStorageSystemURI);
	    			if (targetStorageSystem.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
	    				VPlexRecommendation virtualVolumeRecommendation = new VPlexRecommendation();
	    				virtualVolumeRecommendation.setVirtualArray(targetRecommendation.getVirtualArray());
	    				virtualVolumeRecommendation.setVirtualPool(targetRecommendation.getVirtualPool());
	    				virtualVolumeRecommendation.setVPlexStorageSystem(targetStorageSystemURI);
	    				targetRecommendation.setVirtualVolumeRecommendation(virtualVolumeRecommendation);
	    			}
	    			targetRecommendation.setInternalSiteStorageSystem(targetStorageSystemURI);
	    			targetRecommendation.setSourcePool(candidateTargetPool.getId());
	    			targetRecommendation.setSourceDevice(candidateTargetPool.getStorageDevice());
	    			targetRecommendation.setResourceCount(requestedCount);
	    				    				    		
	    			
	    			boolean addTgtJournal = true;
	    			for(RPRecommendation targetJournalRec : rpProtectionRecommendation.getTargetJournalRecommendations()) {
	    				if (targetJournalRec.getVirtualArray().equals(targetJournalVarray.getId())){
	    					addTgtJournal = false;
	    				}
	    			}	    	
	    			
	    			if (addTgtJournal){
	    				ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, psUri);
	    				RPRecommendation targetJournalRecommendation = buildJournalRecommendation(rpProtectionRecommendation, targetInternalSiteName,
	    						protectionSettings.getJournalSize(), targetJournalVarray, targetJournalVpool, ps, candidateJournalPools, newCapabilities, requestedCount, null, false);

	    				_log.info(String.format("Setting recommendation for TARGET: Journal-varray [%s] -- Journal-vpool [%s]", targetJournalVarray.getLabel(), 
	    						targetJournalVpool.getLabel()));	    			    	
	    				rpProtectionRecommendation.getTargetJournalRecommendations().add(targetJournalRecommendation);
	    			}
	    			
	    			if (rpRecommendation.getTargetRecommendations() == null) {	    				
	    				rpRecommendation.setTargetRecommendations(new ArrayList<RPRecommendation>());;
    				}	
	    			rpRecommendation.getTargetRecommendations().add(targetRecommendation);
	    			
	    			if (targetPoolRecommendation.getResourceCount() >= requestedCount) {
	    				//Found a target solution
	    				break;
	    			}	    				    			
				}						        		        	 	        	     
    	       
				// Set the placement status to reference either the primary or secondary.
				PlacementStatus tmpPlacementStatus = placementStatus;
				if (primaryRecommendation != null) {
					tmpPlacementStatus = secondaryPlacementStatus;
				}
				
				// At this point we have found a target storage pool accessible to the protection vPool and protection vArray
				// that can be protected by an rp cluster site that is part of the same rp system that can protect the source storage pool
				rpProtectionRecommendation.setPlacementStepsCompleted(PlacementProgress.IDENTIFIED_SOLUTION_FOR_SUBSET_OF_TARGETS);
				if (tmpPlacementStatus.isBestSolutionToDate(rpProtectionRecommendation)) {                		
					tmpPlacementStatus.setLatestInvalidRecommendation(rpProtectionRecommendation);
	        	}  			        			
				
				if (isMetroPoint) {
					if (rpProtectionRecommendation.getSourceRecommendations() != null &&
							rpProtectionRecommendation.getSourceRecommendations().size() == protectionVarrays.size()) {
						finalizeTargetPlacement(rpProtectionRecommendation, tmpPlacementStatus);
						return true;
					}
				} else if (protectionVarrays.size() == 1) {
					finalizeTargetPlacement(rpProtectionRecommendation, tmpPlacementStatus);
					return true;
				}
			
				// Find a solution based on this recommendation object and the remaining target arrays
				// Make a new protection varray list
				List<VirtualArray> remainingVarrays = new ArrayList<VirtualArray>();
				remainingVarrays.addAll(protectionVarrays);
			    remainingVarrays.remove(protectionVarray);
				
				if (!remainingVarrays.isEmpty()) {
					_log.info("RP placement: Calling find solution on the next virtual array : " + remainingVarrays.get(0).getLabel() + " Current virtual array: " + protectionVarray.getLabel());
				} else {
					_log.info("Solution cannot be found, will try again with different pool combination");
					return false;
				}

				if (!this.findSolution(rpProtectionRecommendation, rpRecommendation, varray, vpool, remainingVarrays, newCapabilities, requestedCount, isMetroPoint, primaryRecommendation, project)) {
					// Remove the current recommendation and try the next site name, pool, etc.
					_log.info("RP Placement: Solution for remaining virtual arrays couldn't be found.  Trying different solution (if available) for varray: " + protectionVarray.getLabel());
					//TODO Bharath - fix the removal and retry
					//rpProtectionRecommendation.getRpRecommendations().get(rpRecommendation).remove(protectionVarray.getId());
					//rpRecommendation.getTargetRecommendations().remove(o)
				} else {
					// We found a good solution
					_log.info("RP Placement: Solution for remaining virtual arrays was found.  Returning to caller.  Varray: " + protectionVarray.getLabel());
					return true;
			}      
    	}     
        // If we get here, the recommendation object never got a new protection object, and we just return false, which will move onto the next possibility (in the case of a recursive call)
        _log.info("Solution cannot be found, will try again with different pool combination");
		return false;
	}

	
	/* 
    private boolean continueFindSolution(int requestedCount, List<Recommendation> candidateRecommendedPools, RPProtectionRecommendation rpProtectionRecommendation, RPRecommendation rpRecommendation, 
    							VirtualArray protectionVarray,
    							VirtualPool protectionVpool, String internalSiteName ) {
    	
    	Iterator<Recommendation> candidateRecommendedPoolsIter = candidateRecommendedPools.iterator();
    	
    	while (candidateRecommendedPoolsIter.hasNext()) {    		
	    	Recommendation candidatePoolRecommendation = candidateRecommendedPoolsIter.next();
	    	StoragePool candidateTargetPool = dbClient.queryObject(StoragePool.class, candidatePoolRecommendation.getSourcePool());
	    	List<String> associatedStorageSystems = getCandidateTargetVisibleStorageSystems(rpProtectionRecommendation.getProtectionDevice(), 
	                protectionVarray, rpProtectionRecommendation.getInternalSiteName(), 
	                candidateTargetPool, 
	                VirtualPool.vPoolSpecifiesHighAvailability(protectionVpool));
	
	    	 // We want to find an internal site name that isn't already in the solution
	    	for (String associatedStorageSystem : associatedStorageSystems) {
	    	    String targetInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(associatedStorageSystem);
	    	    if (!targetInternalSiteName.equalsIgnoreCase(internalSiteName)) {
	    	    	continue;
	    	    }
	    	    
	    	    URI targetStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(
	    	    								ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem), 
	    	    								dbClient, StorageSystemType.BLOCK);
	    	    	
				// Check to make sure the RP site is connected to the varray
				if (!isRpSiteConnectedToVarray(
				        targetStorageSystemURI, rpProtectionRecommendation.getProtectionDevice(), targetInternalSiteName, protectionVarray)) {
						_log.info(String.format("RP Placement: Disqualified RP site [%s] because its initiators are not in a network configured for use by the virtual array [%s]",
	    									targetInternalSiteName, protectionVarray.getLabel()));						
	    				continue;
				}
											
				Protection protection = rpRecommendation.getVarrayProtectionMap().get(protectionVarray.getId());
//				protection.setTargetVpool(protectionVpool);
//    			protection.setTargetInternalSiteName(targetInternalSiteName);
//    			protection.setTargetInternalSiteStorageSystem(targetStorageSystemURI);   		
    			protection.getProtectionPoolStorageMap().put(candidateTargetPool.getId(), candidateTargetPool.getStorageDevice()); 
    			rpRecommendation.getVarrayProtectionMap().put(protectionVarray.getId(), protection);
    			
				if (candidatePoolRecommendation.getResourceCount() >= requestedCount) {									
					return true;
				} else {
					candidateRecommendedPoolsIter.remove();
					continueFindSolution(requestedCount - candidatePoolRecommendation.getResourceCount(), candidateRecommendedPools, 
							rpProtectionRecommendation, rpRecommendation, protectionVarray, protectionVpool, targetInternalSiteName);
				}				
			}				
    	} 
    	return false;
    }
*/	
	private void updatePoolList(List<StoragePool> pools, StoragePool pool) {
		Iterator<StoragePool> poolsIter = pools.iterator();
		while (poolsIter.hasNext()) {
			if (pool.getId().equals(poolsIter.next().getId())) {
				pools.remove(pool);
			}
		}
	} 
	
	
	/**
	 * Gets the remote target internal RP site name for a recommendation.  This is used for MetroPoint
	 * in the case where we need to determine the remote target RP site use by the primary recommendation. 
	 * 
	 * @param recommendation the recommendation used to locate the remote target RP site. 
	 * @return the target RP site name.
	 */
	private String getMetroPointRemoteTargetRPSite(RPProtectionRecommendation recommendation) {
		String targetInternalSiteName = null;		
		for (RPRecommendation sourceRecommendation : recommendation.getSourceRecommendations()) {
			for(RPRecommendation targetRecommendation : sourceRecommendation.getTargetRecommendations()) {
				if (targetRecommendation.getProtectionType() == ProtectionType.REMOTE) { 
					targetInternalSiteName = targetRecommendation.getInternalSiteName();
					break;
				}				
			}
		}
		return targetInternalSiteName;
	}
	
	/**
	 * Determines if the given recommendation already has a certain MetroPoint protection type specified.
	 * 
	 * @param recommendation the recommendation to check.
	 * @param protectionType the protection type.
	 * @return true if the recommendation contains the protection type, false otherwise.
	 */
	private boolean isMetroPointProtectionSpecified(RPProtectionRecommendation recommendation, ProtectionType protectionType) {	
		if (recommendation.getSourceRecommendations() != null || !recommendation.getSourceRecommendations().isEmpty()) {
		for (RPRecommendation sourceRecommendation : recommendation.getSourceRecommendations()) {
			if (sourceRecommendation.getTargetRecommendations() != null) {
				for (RPRecommendation targetRecommendation : sourceRecommendation.getTargetRecommendations()) {					
					if (targetRecommendation.getProtectionType() == protectionType) {
							return true;
						}
					}
				}
			}
		}		
		return false;
	}
	
	/**
	 * Used in the case of MetroPoint local to determine if the source and protection virtual arrays have
	 * connectivity to the same VPlex system/cluster.
	 * 
	 * @param srcVarray the source virtual array.
	 * @param sourceStorageSystem the source VPlex storage system.
	 * @param protectionVarray the protection virtual array.
	 * @param protectionStorageSystem the protection VPlex storage system.
	 * @return true if the source and protection varrays have connectivity to the 
	 *              same VPlex system/cluster, false otherwise.
	 */
	private boolean isMetroPointLocalVarrayConnectivityValid(VirtualArray srcVarray, URI sourceVplexStorageSystem, VirtualArray protectionVarray, URI protectionVplexStorageSystem) {
		// Get the Vplex cluster associated with the HA leg virtual array - provided in the given recommendation
		String sourceCluster = ConnectivityUtil.getVplexClusterForVarray(srcVarray.getId(), sourceVplexStorageSystem, dbClient);
		// Get the Vplex cluster associated with the protection virtual array.
		String targetCluster = ConnectivityUtil.getVplexClusterForVarray(protectionVarray.getId(), protectionVplexStorageSystem, dbClient);
		
		return sourceCluster.equals(targetCluster);
	}
	
	/**
	 * Method used by findSolution only.  Finalizes the target protection placement by logging a message,
	 * setting the correct placement step and status.
	 * 
	 * @param recommendation the recommendation who's status we want to update. 
	 * @param placementStatus the placement status we want to update.
	 */
	private void finalizeTargetPlacement(RPProtectionRecommendation recommendation, PlacementStatus placementStatus) {
		_log.info("RP Placement: Found a solution for all target varrays");            			
		recommendation.setPlacementStepsCompleted(PlacementProgress.IDENTIFIED_SOLUTION_FOR_ALL_TARGETS);
		if (placementStatus.isBestSolutionToDate(recommendation)) {                		
			placementStatus.setLatestInvalidRecommendation(recommendation);
    	} 
	}
   
    /**
     * Executes a set of business rules against the <code>List</code> of 
     * <code>ProtectionPoolMapping</code> objects to determine if they are capable to perform 
     * volume protection.  The statistics are pulled from the <code>ProtectionSystem</code> 
     * and are used in executing the following business rules:
     * <p>
     * <ul>
     * <li>The RP cluster (ProtectionSystem) must have the capacity to create a single CG.</li>
     * <li>Each RP site must have the volume capacity to create the required number of volumes.</li>
     * </ul>
     * 
     * @param rpConfiguration
     * @param protectionPoolMappings
     * @param resourceCount number of volumes being requested for creation/protection
     * @return true if recommendation can be handled by protection system
     */
    private boolean fireProtectionPlacementRules(ProtectionSystem protectionSystem, RPProtectionRecommendation rpRec, Integer resourceCount) {
    	// Log messages used within this method - Use String.format()
    	final String cgCountLog = "CG count for Protection System %s is %s/%s";
    	final String cgNoCapacityLog = "Protection System %s does not have the CG capacity to protect volumes.";
    	final String sourceSiteVolumeCountLog = "Volume count for Protection System %s/site %s (source) is %s/%s";
    	final String destSiteVolumeCountLog = "Volume count for Protection System %s/site %s (destination) is %s/%s";
    	final String sourceSiteVolumeNoCapacityLog = "Protection System %s/site %s (source) does not have the volume capacity to protect volumes. Requires capacity for %s volume(s).";
    	final String destSiteVolumeNoCapacityLog = "Protection System %s/site %s (destination) does not have the volume capacity to protect volumes. Requires capacity for %s volume(s).";
    	final String parseSiteStatsLog = "A problem occurred parsing site volume statistics for Protection System %s.  " +
    			"Protection system is unable to protect volumes: %s"; 
    	final String missingProtectionSystemMetric = "RecoverPoint metric '%s' for Protection System %s cannot be found. " +
    			"Unable to determine if the protection system is capable of protection volumes.";
    	final String missingSiteMetric = "RecoverPoint metric '%s' for Protection System %s/Site %s cannot be found. Unable " +
    			"to determine if the protection system is capable of protection volumes.";
    	final String validProtectionSystem = "RecoverPoint Protection System '%s' is capable of protecting the requested volumes.";
    	final String inValidProtectionSystem = "RecoverPoint Protection System '%s' is not capable of protecting the requested volumes.";
    	final String validatingProtection = "Validating protection systems to ensure they are capable of handling a protection for %s production volume(s).";

    	_log.info(String.format(validatingProtection, resourceCount));

    	boolean isValid = true;

    	Long rpCGCapacity = protectionSystem.getCgCapacity();
    	Long rpCurrentCGCount = protectionSystem.getCgCount();

    	if (rpCGCapacity == null) {
    		_log.warn(String.format(missingProtectionSystemMetric, "CG Capacity", protectionSystem));
    		rpCGCapacity = -1L;
    	}

    	if (rpCurrentCGCount == null) {
    		_log.warn(String.format(missingProtectionSystemMetric, "CG Count", protectionSystem));
    		rpCurrentCGCount = -1L;
    	}	        	

    	long rpAvailableCGCapacity = rpCGCapacity - rpCurrentCGCount;

    	// Log the CG count.
    	_log.info(String.format(cgCountLog, protectionSystem.getLabel(), rpCurrentCGCount, rpCGCapacity));

    	// Is there enough CG capacity on the RP cluster?
    	if (rpAvailableCGCapacity < 1) {
    		isValid = false;    		
    		_log.info(String.format(cgNoCapacityLog, protectionSystem));
    		//rpRec.setProtectionSystemCriteriaError(String.format(cgNoCapacityLog, protectionSystem));
    	}

    	// Only process the site statistics if the Protection System statistics 
    	// are adequate for protection.
    	StringMap siteVolumeCapacity = protectionSystem.getSiteVolumeCapacity();
    	StringMap siteVolumeCount = protectionSystem.getSiteVolumeCount();
    	
    	List<RPRecommendation> sourceRecommendation = rpRec.getSourceRecommendations();
    	String sourceInternalSiteName = sourceRecommendation.iterator().next().getInternalSiteName();

    	if (siteVolumeCount != null && siteVolumeCount.size() > 0) {
    		String sourceSiteVolumeCount = siteVolumeCount.get(String.valueOf(sourceInternalSiteName));
    		String sourceSiteVolumeCapacity = siteVolumeCapacity.get(String.valueOf(sourceInternalSiteName));

    		if (sourceSiteVolumeCount == null) {
    			_log.warn(String.format(missingSiteMetric, "Source Site Volume Count", protectionSystem, rpRec.getResourceCount()));
    			sourceSiteVolumeCount = "-1";
    		}	

    		if (sourceSiteVolumeCapacity == null) {
    			_log.warn(String.format(missingSiteMetric, "Source Site Volume Capacity", protectionSystem,sourceInternalSiteName));
    			sourceSiteVolumeCapacity = "-1";
    		}	

    		try {
    			// Get the source site available capacity.
    			long sourceSiteAvailableVolCapacity = 
    					Long.parseLong(sourceSiteVolumeCapacity) - Long.parseLong(sourceSiteVolumeCount);

    			_log.debug(String.format(sourceSiteVolumeCountLog, 
    					protectionSystem, sourceInternalSiteName, sourceSiteVolumeCount, sourceSiteVolumeCapacity));

    			// If the source site available capacity is not adequate, log a message.
    			if (sourceSiteAvailableVolCapacity < rpRec.getNumberOfVolumes(sourceInternalSiteName)) {
    				isValid = false;
    				_log.info(String.format(sourceSiteVolumeNoCapacityLog, protectionSystem, sourceInternalSiteName, resourceCount));
    				rpRec.setProtectionSystemCriteriaError(String.format(sourceSiteVolumeNoCapacityLog, protectionSystem, sourceInternalSiteName, resourceCount));
    			}
    		} catch (NumberFormatException nfe) {
    			// Catch any exceptions that occur while parsing the site specific values
    			isValid = false;
    			_log.info(String.format(parseSiteStatsLog, protectionSystem, nfe.getMessage()));
    			rpRec.setProtectionSystemCriteriaError(String.format(parseSiteStatsLog, protectionSystem, nfe.getMessage()));
    		} 

    		for(RPRecommendation sourceRec : rpRec.getSourceRecommendations()) {
    			for (RPRecommendation targetRec : sourceRec.getTargetRecommendations()) {
    				String internalSiteName = targetRec.getInternalSiteName();
	    			String destSiteVolumeCount = siteVolumeCount.get(String.valueOf(internalSiteName));
	    			String destSiteVolumeCapacity = siteVolumeCapacity.get(String.valueOf(internalSiteName));
	
	    			if (destSiteVolumeCount == null) {
	    				_log.warn(String.format(missingSiteMetric, "Destination Site Volume Count", protectionSystem, internalSiteName));
	    				destSiteVolumeCount = "-1";
	    			}	
	
	    			if (destSiteVolumeCapacity == null) {
	    				_log.warn(String.format(missingSiteMetric, "Destination Site Volume Capacity", protectionSystem, internalSiteName));
	    				destSiteVolumeCapacity = "-1";
	    			}	
	
	    			try {
	    				// Get the destination site available capacity.
	    				long destSiteAvailableVolCapacity = 
	    						Long.parseLong(destSiteVolumeCapacity) - Long.parseLong(destSiteVolumeCount);
	
	    				_log.debug(String.format(destSiteVolumeCountLog, 
	    						protectionSystem, internalSiteName, destSiteVolumeCount, destSiteVolumeCapacity));
	
	    				// If the destination site available capacity is not adequate, log a message.
	    				if (destSiteAvailableVolCapacity < rpRec.getNumberOfVolumes(targetRec.getInternalSiteName())) {
	    					isValid = false;
	    					_log.info(String.format(destSiteVolumeNoCapacityLog, protectionSystem, internalSiteName, rpRec.getResourceCount()));
	    					rpRec.setProtectionSystemCriteriaError(String.format(destSiteVolumeNoCapacityLog, protectionSystem, internalSiteName, rpRec.getResourceCount()));
	    				}
	    			} catch (NumberFormatException nfe) {
	    				// Catch any exceptions that occur while parsing the site specific values
	    				isValid = false;
	    				_log.info(String.format(parseSiteStatsLog, protectionSystem, nfe.getMessage()));
	    				rpRec.setProtectionSystemCriteriaError(String.format(parseSiteStatsLog, protectionSystem, nfe.getMessage()));
	    			}     			
    			} 
    		}
    	} else {
    		// There are no site volume statistics available so assume volume
    		// protection cannot be achieved.
    		isValid = false;
    		_log.warn(String.format(missingProtectionSystemMetric, "Site Volume Capacity/Count", protectionSystem));
    		rpRec.setProtectionSystemCriteriaError(String.format(missingProtectionSystemMetric, "Site Volume Capacity/Count", protectionSystem));
    	}
    	
    	// log a message is the protection system is valid.
    	if (isValid) {
    		_log.debug(String.format(validProtectionSystem, protectionSystem));
    	} else {
    		_log.debug(String.format(inValidProtectionSystem, protectionSystem));
    	}

    	return isValid;
    }

	/**
     * Get protection systems and sites associated with the storage system
     * 
     * @param storageSystemId storage system id
     * @return map of protection set to sites that its visible to
     */
    protected Map<URI, Set<String>> getProtectionSystemSiteMap(URI storageSystemId) {
    	Map<URI, Set<String>> protectionSystemSiteMap = new HashMap<URI, Set<String>>();
    	for (URI protectionSystemId : dbClient.queryByType(ProtectionSystem.class, true)) {
    		ProtectionSystem protectionSystem = dbClient.queryObject(ProtectionSystem.class, protectionSystemId);
    		StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemId); 
    		StringSet associatedStorageSystems = protectionSystem.getAssociatedStorageSystemsWithString(storageSystem.getSerialNumber());
    		if (associatedStorageSystems != null) {
    			for (String associatedStorageSystem : associatedStorageSystems) {
    				if (protectionSystemSiteMap.get(protectionSystemId) == null) {
    					protectionSystemSiteMap.put(protectionSystemId,  new HashSet<String>());
    				}
    				protectionSystemSiteMap.get(protectionSystemId).add(ProtectionSystem.getAssociatedStorageSystemSiteName(associatedStorageSystem));
    			}
    		}
    	}
    	return protectionSystemSiteMap;
    }
     
	/**
	 * Determines if the RP site is connected to the passed virtual array.
	 * @param rpSiteArray the RP site 
	 * @param virtualArray the virtual array to check for RP site connectivity
	 * @return
	 */
	public boolean isRpSiteConnectedToVarray(URI storageSystemURI, URI protectionSystemURI, String siteId, VirtualArray virtualArray) {
        ProtectionSystem protectionSystem = 
        		dbClient.queryObject(ProtectionSystem.class, protectionSystemURI);
        StringSet siteInitiators = 
        		protectionSystem.getSiteInitiators().get(siteId);
        
        boolean connected = false;
        
        for (String wwn : siteInitiators) {
        	NetworkLite network = NetworkUtil.getEndpointNetworkLite(wwn, dbClient);
        	// The network is connected if it is assigned or implicitly connected to the varray
        	if (rpHelper.isNetworkConnectedToVarray(network, virtualArray)) {
        		connected = true;
        		break;
            }
        }
        
        // Check to make sure the RP site is connected to the varray
        return (connected && rpHelper.rpInitiatorsInStorageConnectedNework(
                        storageSystemURI, protectionSystemURI, siteId, virtualArray.getId()));
	}
	
	/**
	 * Custom Comparator used to sort ProtectionSystem objects by the
	 * cgLastCreatedTime field.
	 */
	class ProtectionSystemComparator implements Comparator<ProtectionSystem> {
        @Override
        public int compare(ProtectionSystem o1, ProtectionSystem o2) {
            if (o1.getCgLastCreatedTime() == null && o2.getCgLastCreatedTime() == null) {
                return 0;
            } else if (o1.getCgLastCreatedTime() == null && o2.getCgLastCreatedTime() != null) {
                return -1;
            } else if (o1.getCgLastCreatedTime() != null && o2.getCgLastCreatedTime() == null) {
                return 1;
            } else {
                return o1.getCgLastCreatedTime().compareTo(o2.getCgLastCreatedTime());
            }
        }
	}
	
    /**
     * Used in StoragePool selection.
     */	
    class StoragePoolFreeCapacityComparator implements Comparator<StoragePool> {
        @Override
        public int compare(StoragePool rhs, StoragePool lhs) {
            int result;
            
            // if avg port metrics was not computable, consider its usage is max out for sorting purpose
            double rhsAvgPortMetrics = rhs.getAvgStorageDevicePortMetrics() == null ? Double.MAX_VALUE : rhs.getAvgStorageDevicePortMetrics();
            double lhsAvgPortMetrics = lhs.getAvgStorageDevicePortMetrics() == null ? Double.MAX_VALUE : lhs.getAvgStorageDevicePortMetrics();
                        
            if ( rhs.getFreeCapacity() > 0 && rhsAvgPortMetrics < lhsAvgPortMetrics ) {
                result = -1;            
            } else if (rhs.getFreeCapacity() < lhs.getFreeCapacity()) {
                result = -1;
            } else if (rhs.getFreeCapacity() > lhs.getFreeCapacity()) {
                result = 1;
            } else {
                result = 0;
            }
            return result;
        }
    }   
	
	/**
	 * Sorts the Set of ProtectionSystem objects by the cgLastCreatedTime field.
	 * Objects will be sorted from oldest to most current timestamp.  The Set
	 * will also be converted to a List because of the use of a Comparator.
	 * 
	 * @param protectionSystems the Set of ProtectionSystem objects to sort.
	 * @return the sorted list of ProtectionSystem objects.
	 */
	private List<ProtectionSystem> sortProtectionSystems(Set<ProtectionSystem> protectionSystems) {
        // Convert the HashSet to an ArrayList so it can be sorted
        List<ProtectionSystem> protectionSystemsLst = 
                new ArrayList<ProtectionSystem>(protectionSystems);
	    
        // Only sort if there is more than 1 ProtectionSystem
	    if (protectionSystems.size() > 1) {
    	    _log.info("Sorting candidate protection systems by CG last created time.");
    	    _log.info("Before sort: " + protectionSystemsToString(protectionSystems));
            
            // Sort the protection systems from oldest to most current cgLastCreatedTime.
            ProtectionSystemComparator comparator = new ProtectionSystemComparator();
            Collections.sort(protectionSystemsLst, comparator);
            
            _log.info("After sort: " + protectionSystemsToString(protectionSystemsLst));
	    }
	    
        return protectionSystemsLst;
	}		
 
	/**
	 * Convenience method to create a String of protection system labels/CG last created
	 * timestamps.
	 * 
	 * @param protectionSystems The Collection of protection systems to create a String from.
	 * @return the String representation of the protection system Collection.
	 */
	private String protectionSystemsToString(Collection<ProtectionSystem> protectionSystems) {
	    List<String> temp = new ArrayList<String>();	    
	    StringBuffer buff = new StringBuffer();
	    for (ProtectionSystem ps : protectionSystems) {
	        buff.append(ps.getLabel());
	        buff.append(":");
	        buff.append(ps.getCgLastCreatedTime() != null ? ps.getCgLastCreatedTime().getTime().toString() : "No CGs created");
	        temp.add(buff.toString());
	        buff.delete(0, buff.length());
	    }
	    
	    return StringUtils.join(temp, ", ");
	}   
	    
	private class PlacementStatus {
		private String srcVArray;
		private String srcVPool;
		private HashMap<URI, Boolean> processedProtectionVArrays = new HashMap<URI, Boolean>();
		private RPProtectionRecommendation latestInvalidRecommendation = null;
				
		public HashMap<URI, Boolean> getProcessedProtectionVArrays() {
			return processedProtectionVArrays;
		}		
						
		public void setLatestInvalidRecommendation(RPProtectionRecommendation latestInvalidRecommendation) {
			if (latestInvalidRecommendation == null) {
				this.latestInvalidRecommendation = null;
			} else {
				this.latestInvalidRecommendation = new RPProtectionRecommendation(latestInvalidRecommendation);
			}
		}				
									
		public void setSrcVArray(String srcVArray) {
			this.srcVArray = srcVArray;
		}
		
		public void setSrcVPool(String srcVPool) {
			this.srcVPool = srcVPool;
		}

		boolean isBestSolutionToDate(RPProtectionRecommendation recommendation) {
			// In the case below we have identified the source configuration
			if (this.latestInvalidRecommendation == null) {
				return true;
			} else  {
				/* Bharath TODO fix
				if ((recommendation.getPlacementStepsCompleted().ordinal() >= latestInvalidRecommendation.getPlacementStepsCompleted().ordinal()) &&
			
					recommendation.getVirtualArrayProtectionMap().size() >= latestInvalidRecommendation.getVirtualArrayProtectionMap().size()) {
						return true;
				}
				*/
			}			
			return false;
		}
		
		public boolean containsProtectionToVarray(RPProtectionRecommendation recommendation, URI varrayUri) {
			for (RPRecommendation sourceRec : recommendation.getSourceRecommendations()) {
				for (RPRecommendation targetRec : sourceRec.getTargetRecommendations()) {
					if (targetRec.getVirtualArray().equals(varrayUri)) {
						return true;
					}
				}
			}
			return false;
		}
		
		public String toString(DbClient dbClient) {				
			StringBuffer buff = new StringBuffer("\n--------------------------------------------------------------------------------------------------------------------------------------------------------\n");
			 
			buff.append("RecoverPoint-Protected Placement Error:  It is possible that other solutions were available and equal in their level of success to the one listed below.\n");
			buff.append("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");
			if (this.latestInvalidRecommendation == null) {
				buff.append("Virtual pool " + this.srcVPool + " and virtual Array " + this.srcVArray + " do not have access to any storage pools for the source devices that can be protected.\n");
			} else {
				if (this.latestInvalidRecommendation.getPlacementStepsCompleted().ordinal() == PlacementProgress.NONE.ordinal()) {
					buff.append("Virtual pool " + this.srcVPool + " and virtual Array " + this.srcVArray + " do not have access to any storage pools for the source devices that can be protected.\n");
				}
				if (this.latestInvalidRecommendation.getPlacementStepsCompleted().ordinal() >= PlacementProgress.IDENTIFIED_SOLUTION_FOR_SOURCE.ordinal()) {				  
					buff.append("Placement was found for the source devices using the following configuration:\n");
					StoragePool pool = (StoragePool)dbClient.queryObject(StoragePool.class, this.latestInvalidRecommendation.getSourcePool());
					StoragePool jpool = (StoragePool)dbClient.queryObject(StoragePool.class, this.latestInvalidRecommendation.getSourceJournalRecommendation().getSourcePool());
					StorageSystem system = (StorageSystem)dbClient.queryObject(StorageSystem.class, this.latestInvalidRecommendation.getSourceDevice());
					ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, this.latestInvalidRecommendation.getProtectionDevice());
					String sourceInternalSiteName = this.latestInvalidRecommendation.getSourceRecommendations().get(0).getInternalSiteName();
					String sourceRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(sourceInternalSiteName) : sourceInternalSiteName; 
					buff.append("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");				   
					buff.append("\tSource Virtual Array: " + this.srcVArray + "\n");
					buff.append("\tSource Virtual Pool: " + this.srcVPool + "\n");
					buff.append("\tProtection System: " + ps.getLabel() + "\n");
					buff.append("\tSource RP Site: " + sourceRPSiteName + "\n");
					buff.append("\tSource Storage System: " + system.getLabel() + "\n");
					buff.append("\tSource Storage Pool: " + pool.getLabel() + "\n");
					buff.append("\tSource Journal Storage Pool: " + (jpool != null ? jpool.getLabel() : "null") + "\n");
					buff.append("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");
				}
				
				if (this.latestInvalidRecommendation.getPlacementStepsCompleted().ordinal() == PlacementProgress.IDENTIFIED_SOLUTION_FOR_SUBSET_OF_TARGETS.ordinal()) {
					buff.append("Placement determined protection is not possible to all " + this.processedProtectionVArrays.size() + " of the requested virtual arrays.\n");
				}
				if (this.latestInvalidRecommendation.getPlacementStepsCompleted().ordinal() >= PlacementProgress.IDENTIFIED_SOLUTION_FOR_ALL_TARGETS.ordinal()) {
					buff.append("Placement determined protection is possible to all the requested virtual arrays.\n");
				}
				buff.append("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");						

				for (URI varrayID : this.processedProtectionVArrays.keySet()) {
					VirtualArray varray = (VirtualArray)dbClient.queryObject(VirtualArray.class,  varrayID);	
					for (RPRecommendation rpRec : this.latestInvalidRecommendation.getSourceRecommendations()) {
						for(RPRecommendation targetRec : rpRec.getTargetRecommendations()) {							
							//StoragePool targetjPool = (StoragePool)dbClient.queryObject(StoragePool.class, protection.getTargetJournalRecommendation().getSourcePool());
							if (containsProtectionToVarray(latestInvalidRecommendation, varrayID)) {
							ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, this.latestInvalidRecommendation.getProtectionDevice());
							String targetInternalSiteName = targetRec.getInternalSiteName();
							String targetRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(targetInternalSiteName) : targetInternalSiteName; 
							buff.append("\tProtection to Virtual Array: " + varray.getLabel() + "\n");
							buff.append("\tProtection to RP Site: " + targetRPSiteName + "\n");																				
							StoragePool targetPool = dbClient.queryObject(StoragePool.class, targetRec.getSourcePool());								
							StorageSystem targetSystem = (StorageSystem)dbClient.queryObject(StorageSystem.class, targetRec.getSourceDevice());
							buff.append("\tProtection to Storage System: " + targetSystem.getLabel() + "\n");
							buff.append("\tProtection to Storage Pool: " + targetPool.getLabel() + "\n");
								
							//buff.append("\tProtection Journal Storage Pool: " + targetjPool.getLabel() + "\n");
						} else if (this.processedProtectionVArrays.get(varrayID)) {
							buff.append("Protection to virtual array " + varray.getLabel() + " is not possible.\n");
						} else {
							buff.append("Did not process protection to virtual array " + varray.getLabel() + " because protection was not possible to another virtual array in the request.\n");
						}
					buff.append("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");
					}
					   			
				if (this.latestInvalidRecommendation.getPlacementStepsCompleted().ordinal() == PlacementProgress.PROTECTION_SYSTEM_CANNOT_FULFILL_REQUEST.ordinal()) {
					buff.append("The protection system " + dbClient.queryObject(ProtectionSystem.class, this.latestInvalidRecommendation.getProtectionDevice()).getLabel() + 
							"cannot fulfill the protection request for the reason below:\n" + this.latestInvalidRecommendation.getProtectionSystemCriteriaError() + "\n");
				}	
			}
					buff.append("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");			
			}			   				
		}
			return buff.toString();	
		}
	}
}
