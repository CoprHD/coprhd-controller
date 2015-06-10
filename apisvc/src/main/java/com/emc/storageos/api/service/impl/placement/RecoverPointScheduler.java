/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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
import java.util.Date;
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
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
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
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.util.ConnectivityUtil.StorageSystemType;
import com.emc.storageos.vnxe.requests.PoolListRequest;
import com.emc.storageos.volumecontroller.Protection;
import com.emc.storageos.volumecontroller.Protection.ProtectionType;
import com.emc.storageos.volumecontroller.RPProtectionRecommendation;
import com.emc.storageos.volumecontroller.RPProtectionRecommendation.PlacementProgress;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.volumecontroller.impl.utils.ObjectLocalCache;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.CapacityMatcher;
import com.google.common.base.Joiner;

/**
 * Advanced RecoverPoint based scheduling function for block storage.  StorageScheduler is done based on desired
 * class-of-service parameters for the provisioned storage.
 */
public class RecoverPointScheduler implements Scheduler {
	
    public static final Logger _log = LoggerFactory.getLogger(RecoverPointScheduler.class);
    
    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    DbClient _dbClient;
    protected CoordinatorClient _coordinator;
    private RPHelper _rpHelper;
    private StorageScheduler _blockScheduler;
    private AttributeMatcherFramework _matcherFramework;
    private PlacementStatus placementStatus;
    private PlacementStatus secondaryPlacementStatus;
    
    public void setBlockScheduler(StorageScheduler blockScheduler) {
        _blockScheduler = blockScheduler;
    }

    public void setRpHelper(RPHelper rpHelper) {
        _rpHelper = rpHelper;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }
    
    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    public void setMatcherFramework(AttributeMatcherFramework matcherFramework) {
        _matcherFramework = matcherFramework;
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
	public List<Recommendation> getRecommendationsForResources(VirtualArray varray,
	        Project project, VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        _log.debug("Schedule storage for {} resource(s) of size {}.", capabilities.getResourceCount(), capabilities.getSize());

        List<VirtualArray> protectionVarrays = getProtectionVirtualArraysForVirtualPool(project, vpool, _dbClient, _permissionsHelper);

        // Get all storage pools that match the passed CoS params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        List<StoragePool> candidatePools = _blockScheduler.getMatchingPools(varray, vpool, capabilities);
        
        if (candidatePools == null || candidatePools.isEmpty()) {
        	_log.error("No matching storage pools found for the source varray: {0}. There are no storage pools that " +
            		"match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to " +
            		"hold at least one resource of the requested size.",varray.getLabel());
        	throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getId(), varray.getId());            
        }
        
        List<Recommendation> recommendations = buildCgRecommendations(capabilities, vpool, protectionVarrays, null);        

        if (recommendations.isEmpty()) {
            // Schedule storage based on the source pool constraint.
            recommendations = scheduleStorageSourcePoolConstraint(varray, protectionVarrays, vpool, capabilities, null, null, null);
        }
        
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
		List<StoragePool> pools = VirtualPool.getValidStoragePools(vpool, _dbClient, true);
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
			VpoolProtectionVarraySettings settings = _dbClient.queryObject(VpoolProtectionVarraySettings.class, URI.create(settingsURI));
			// If there was no vpool specified with the protection settings, use the base vpool for the new volume request
			URI protectionVpoolId = vpool.getId();
			if (settings.getVirtualPool()!=null) {
				protectionVpoolId = settings.getVirtualPool();
			}
			VirtualPool protectionVpool = _dbClient.queryObject(VirtualPool.class, protectionVpoolId);
			if (verifyStoragePoolAvailability(protectionVpool, targetVolume.getPool())) {
				return true;
			}     					    			    		
    	} 
		return false;
	}
	
	/** Returns the journal volume associated with the passed in volume
	 * @param volume - volume to determine the journal volume for 
	 * @return URI of the associated journal volume
	 */
	private URI getJournalStoragePool(Volume volume) {
		return _dbClient.queryObject(Volume.class, volume.getRpJournalVolume()).getPool();		
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
    	} else if (!verifyStoragePoolAvailability(vpool, getJournalStoragePool(srcVolume))) {
    		_log.warn("Unable to fully align placement with existing volumes in RecoverPoint consistency group {}.  " +
                    "The storage pool {} used by an existing source journal volume cannot be used.", cgName, getJournalStoragePool(srcVolume));
    		return false;
    	}
		
    	// Check if the storage pools used by the existing source RP targets and their journals are available in the current vpool
    	Iterator<String> targetVolumes = srcVolume.getRpTargets().iterator();
    	while (targetVolumes.hasNext()) {
    		Volume targetVolume = _dbClient.queryObject(Volume.class, URI.create(targetVolumes.next()));
    		if (!verifyTargetStoragePoolAvailability(targetVolume, vpool)) {
    			_log.warn("Unable to fully align placement with existing volumes in RecoverPoint consistency group {}.  " +
                        "The storage pool {} used by an existing target volumes cannot be used.", cgName, targetVolume.getPool());
        		return false;
    		}
    		
    		Volume targetJournal = _dbClient.queryObject(Volume.class, targetVolume.getRpJournalVolume());
    		if (!verifyTargetStoragePoolAvailability(targetJournal, vpool)) {
    			_log.warn("Unable to fully align placement with existing volumes in RecoverPoint consistency group {}.  " +
                        "The storage pool {} used by an existing target journal volume cannot be used.", cgName, getJournalStoragePool(targetVolume));
        		return false;
    		}
    	}
		return true;
	}
	
	protected List<Recommendation> buildCgRecommendations(
	        VirtualPoolCapabilityValuesWrapper capabilities, VirtualPool vpool, List<VirtualArray> protectionVarrays,
	        Volume vpoolChangeVolume) {
	    BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
	    _log.info("Attempting to align placement (protection system, storage pools, internal site names) with " +
	    		"existing volumes in RecoverPoint consistency group {}.", cg.getLabel());
        
	    List<Recommendation> recommendations = new ArrayList<Recommendation>();        
        
        // Find the first existing source volume
        List<Volume> sourceVolumes = _rpHelper.getCgVolumes(capabilities.getBlockConsistencyGroup(), Volume.PersonalityTypes.SOURCE.toString());
        
        
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
        
        StoragePool sourcePool = _dbClient.queryObject(StoragePool.class, sourceVolume.getPool());
        Volume sourceJournal = _dbClient.queryObject(Volume.class, sourceVolume.getRpJournalVolume());
        StoragePool sourceJournalPool = _dbClient.queryObject(StoragePool.class, sourceJournal.getPool());
        
        RPProtectionRecommendation recommendation = new RPProtectionRecommendation();
        recommendation.setSourceDevice(sourcePool.getStorageDevice());
        recommendation.setSourceInternalSiteName(sourceVolume.getInternalSiteName());                    
        recommendation.setSourcePool(sourcePool.getId());                  
        recommendation.setSourceJournalStoragePool(sourceJournalPool.getId()); 
        recommendation.setSourceJournalVarray(sourceJournal.getVirtualArray());
        recommendation.setSourceJournalVpool(sourceJournal.getVirtualPool());                  
        recommendation.setProtectionDevice(sourceVolume.getProtectionController());
        recommendation.setVpoolChangeVolume(vpoolChangeVolume != null ? vpoolChangeVolume.getId() : null);
        recommendation.setVpoolChangeVpool(vpoolChangeVolume != null ? vpoolChangeVolume.getVirtualPool() : null);
        recommendation.setVpoolChangeProtectionAlreadyExists(vpoolChangeVolume != null ? vpoolChangeVolume.checkForRp() : false);
        recommendation.setResourceCount(capabilities.getResourceCount());
                       
        for (VirtualArray protectionVarray : protectionVarrays) {                           
            // Find the existing source volume target that corresponds to this protection
            // virtual array.  We need to see if the storage pool has capacity for another
            // target volume.
        	Protection protection = new Protection();
            Volume targetVolume = getTargetVolumeForProtectionVirtualArray(sourceVolume, protectionVarray);
            StoragePool targetPool = _dbClient.queryObject(StoragePool.class, targetVolume.getPool());
            Volume targetJournal = _dbClient.queryObject(Volume.class, targetVolume.getRpJournalVolume());
            
            protection.setTargetInternalSiteName(targetVolume.getInternalSiteName());
            protection.setTargetStoragePool(targetVolume.getPool());
            protection.setTargetDevice(targetPool.getStorageDevice());
            protection.setTargetJournalVarray(targetJournal.getVirtualArray());
            protection.setTargetJournalVpool(targetJournal.getVirtualPool());
            protection.setTargetJournalStoragePool(targetJournal.getPool());   
            
            recommendation.getVirtualArrayProtectionMap().put(protectionVarray.getId(), protection);
        }
    
        _log.info(String.format("Produced recommendation based on existing source volume %s from " +
                "RecoverPoint consistency group %s: \n %s", sourceVolume.getLabel(), cg.getLabel(), 
                recommendation.toString(_dbClient)));
        
        recommendations.add(recommendation);
        
        return recommendations;
	}
	
	private boolean cgPoolsHaveAvailableCapacity(Volume sourceVolume, VirtualPoolCapabilityValuesWrapper capabilities, 
	        VirtualPool vpool, List<VirtualArray> protectionVarrays) {
	    boolean cgPoolsHaveAvailableCapacity = true;
	    Map<URI, Long> storagePoolRequiredCapacity = new HashMap<URI, Long>();
	    Map<URI, StoragePool> storagePoolCache = new HashMap<URI, StoragePool>();
	    
        if (sourceVolume != null) {
        	// TODO:  need to update code below to look like the stuff Bharath added for multiple resources
        	long sourceVolumesRequiredCapacity = getSizeInKB(capabilities.getSize() * capabilities.getResourceCount());
            StoragePool sourcePool = _dbClient.queryObject(StoragePool.class, sourceVolume.getPool());
            storagePoolCache.put(sourcePool.getId(), sourcePool);
            updateStoragePoolRequiredCapacityMap(storagePoolRequiredCapacity, sourcePool.getId(), sourceVolumesRequiredCapacity);
            
            Volume sourceJournal = _dbClient.queryObject(Volume.class, sourceVolume.getRpJournalVolume());
            long sourceJournalSizePerPolicy = RPHelper.getJournalSizeGivenPolicy(String.valueOf(capabilities.getSize()), vpool.getJournalSize(), capabilities.getResourceCount());
            long sourceJournalVolumesRequiredCapacity = getSizeInKB(sourceJournalSizePerPolicy); 
            StoragePool sourceJournalPool = _dbClient.queryObject(StoragePool.class, sourceJournal.getPool());
            storagePoolCache.put(sourceJournalPool.getId(), sourceJournalPool);
            updateStoragePoolRequiredCapacityMap(storagePoolRequiredCapacity, sourceJournalPool.getId(), sourceJournalVolumesRequiredCapacity);
            
            if (sourceVolume.getRpTargets() != null) {                
                for (VirtualArray protectionVarray : protectionVarrays) {            
                    // Find the pools that apply to this virtual
                    VpoolProtectionVarraySettings settings = _rpHelper.getProtectionSettings(vpool, protectionVarray);
                    // If there was no vpool specified with the protection settings, use the base vpool for this varray.
                    VirtualPool protectionVpool = vpool;
                    if (settings.getVirtualPool() != null) {
                        protectionVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
                    }
                    
                    // Find the existing source volume target that corresponds to this protection
                    // virtual array.  We need to see if the storage pool has capacity for another
                    // target volume.
                    Volume targetVolume = getTargetVolumeForProtectionVirtualArray(sourceVolume, protectionVarray);

                    // Target volumes will be the same size as the source
                    long targetVolumeRequiredCapacity = getSizeInKB(capabilities.getSize());
                    StoragePool targetPool = _dbClient.queryObject(StoragePool.class, targetVolume.getPool());
                    storagePoolCache.put(targetPool.getId(), targetPool);
                    updateStoragePoolRequiredCapacityMap(storagePoolRequiredCapacity, targetPool.getId(), targetVolumeRequiredCapacity);
                    
                    // Account for the target journal volumes.
                    Volume targetJournalVolume = _dbClient.queryObject(Volume.class, targetVolume.getRpJournalVolume());
                    long targetJournalSizePerPolicy =
                    		RPHelper.getJournalSizeGivenPolicy(
                    				String.valueOf(capabilities.getSize()), protectionVpool.getJournalSize(), capabilities.getResourceCount());
                    long targetJournalVolumeRequiredCapacity = getSizeInKB(targetJournalSizePerPolicy);
                    StoragePool targetJournalPool = _dbClient.queryObject(StoragePool.class, targetJournalVolume.getPool());
                    storagePoolCache.put(targetJournalPool.getId(), targetJournalPool);
                    updateStoragePoolRequiredCapacityMap(storagePoolRequiredCapacity, targetJournalPool.getId(), targetJournalVolumeRequiredCapacity);                    
                }
            }
            
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
            
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
            Volume targetVolume = _dbClient.queryObject(Volume.class, URI.create(targetVolumes.next()));
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
     * @param candidatePrimaryPools the candidate primary cluster source pools.
     * @param candidateSecondaryPools  the candidate secondary cluster source pools.
     * @param candidateProtectionPoolsMap pre-populated map for tgt varray to storage pools, use null if not needed
     * @param vpoolChangeParam The change param from a change vpool operation
     * @param vpoolChangeVolume The main volume for this change vpool operation
     * @return list of Recommendation objects to satisfy the request
     */
    protected Map<Recommendation, Recommendation> createMetroPointRecommendations(VirtualArray varray,
            List<VirtualArray> protectionVarrays, VirtualPool vpool, VirtualArray haVarray, VirtualPool haVpool, 
            VirtualPoolCapabilityValuesWrapper capabilities,
            List<StoragePool> candidatePrimaryPools,
            List<StoragePool> candidateSecondaryPools,
            Map<VirtualArray, List<StoragePool>> candidateProtectionPoolsMap, 
            Volume vpoolChangeVolume, VirtualPoolChangeParam vpoolChangeParam) {
        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
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
               
        List<URI> primarySourcePoolURIs = new ArrayList<URI>();
        Map<Long, List<URI>> sortedPrimarySourcePoolsMap = null;
        List<URI> secondarySourcePoolURIs = new ArrayList<URI>();
        Map<Long, List<URI>> sortedSecondarySourcePoolsMap = null;
        Map<Long, List<URI>> sortedActiveSourceJournalPoolsMap = null;        
        Map<Long, List<URI>> sortedStandbySourceJournalPoolsMap = null;
        Map<URI, Map<Long, List<URI>>> targetVarraySortedPoolMap = new HashMap<URI, Map<Long, List<URI>>>();
        
        // Sort the primary source candidate pools.
        VirtualArray journalVarray = varray;
        if (candidatePrimaryPools != null && !candidatePrimaryPools.isEmpty()) {
            _log.info(String.format("Primary Source varray [%s] sorted pools:", varray.getLabel()));
            sortedPrimarySourcePoolsMap = getSizeOrderedPoolsMap(candidatePrimaryPools);
           
            if (NullColumnValueGetter.isNotNullValue(vpool.getJournalVarray())) {
            	journalVarray = _dbClient.queryObject(VirtualArray.class, URI.create(vpool.getJournalVarray()));
            }
            
            VirtualPool activeSourceJournalVpool = (vpool.getJournalVpool() != null ?
            		_dbClient.queryObject(VirtualPool.class, URI.create(vpool.getJournalVpool())) : vpool);
            sortedActiveSourceJournalPoolsMap = getSizeOrderedPoolsMap(_blockScheduler.getMatchingPools(journalVarray, activeSourceJournalVpool, capabilities));            
        }
             
        // Sort the secondary source candidate pools.
        if (candidateSecondaryPools != null && !candidateSecondaryPools.isEmpty()) {
            _log.info(String.format("Secondary Source varray [%s] sorted pools:", haVarray.getLabel()));
            sortedSecondarySourcePoolsMap = getSizeOrderedPoolsMap(candidateSecondaryPools);  
            
            VirtualArray standbySourceJournalVarray = haVarray;
            if (NullColumnValueGetter.isNotNullValue(vpool.getStandbyJournalVarray())) {
            	standbySourceJournalVarray = _dbClient.queryObject(VirtualArray.class, URI.create(vpool.getStandbyJournalVarray()));
            }
            
            VirtualPool standbySourceJournalVpool = (vpool.getStandbyJournalVpool() != null ?
            		_dbClient.queryObject(VirtualPool.class, URI.create(vpool.getStandbyJournalVpool())) : haVpool);
            sortedStandbySourceJournalPoolsMap = getSizeOrderedPoolsMap(_blockScheduler.getMatchingPools(standbySourceJournalVarray, 
            											standbySourceJournalVpool, capabilities));
        }
      
        // Sort the target protection candidate pools.
        if (candidateProtectionPoolsMap != null 
                && !candidateProtectionPoolsMap.isEmpty()) {
            for (VirtualArray protectionVarray : candidateProtectionPoolsMap.keySet()) {
                _log.info(String.format("Target varray [%s] sorted pools:", protectionVarray.getLabel()));
            	targetVarraySortedPoolMap.put(protectionVarray.getId(), 
                		getSizeOrderedPoolsMap(candidateProtectionPoolsMap.get(protectionVarray)));
            }
        }
                
        List<VirtualArray> primaryProtectionVarrays = new ArrayList<VirtualArray>();
        if (haVarray != null) {
        	// Build the list of protection virtual arrays to consider for determining a
        	// primary placement solution.  Add all virtual arrays from the source virtual
        	// pool list of protection virtual arrays, except for the HA virtual array.  
        	// In the case of local and/or remote protection, the HA virtual array should 
        	// never be considered as a valid protection target for primary placement.
        	for (VirtualArray protectionVarray : protectionVarrays) {
        		if (!protectionVarray.getId().equals(haVarray.getId())) {
        			primaryProtectionVarrays.add(protectionVarray);
        		}
        	}
        }
        
        List<VirtualArray> secondaryProtectionVarrays = new ArrayList<VirtualArray>();
    	// Build the list of protection virtual arrays to consider for determining a
    	// secondary placement solution.  Add all virtual arrays from the source virtual
    	// pool list of protection virtual arrays, except for the source virtual array.  
    	// In the case of local and/or remote protection, the source virtual array should 
    	// never be considered as a valid protection target for secondary placement.
    	for (VirtualArray protectionVarray : protectionVarrays) {
    		if (!protectionVarray.getId().equals(varray.getId())) {
    			secondaryProtectionVarrays.add(protectionVarray);
    		}
    	}

        // The attributes below will not change throughout the placement process
        placementStatus.setSrcVArray(varray.getLabel());
        placementStatus.setSrcVPool(vpool.getLabel());
        
        Map<URI, Set<ProtectionSystem>> secondaryStoragePoolsToProtectionSystems = new HashMap<URI, Set<ProtectionSystem>>();
        
        while (totalSatisfiedCount < capabilities.getResourceCount()) {        	
        	boolean secondaryRecommendationSolution = false;
        	
        	int satisfiedSourceVolCount = 0;
        	
        	if (vpoolChangeVolume != null) {
        	    // If this is a change vpool operation, the source has already been placed and there is only 1
        	    // valid pool, the existing one. This is just to used to pass through the placement code.
        	    URI existingPrimarySourcePool = sortedPrimarySourcePoolsMap.entrySet().iterator().next().getValue().get(0);
                _log.info(String.format("Primary Source Pool already exists, reuse pool: [%s].", existingPrimarySourcePool.toString()));
        	    primarySourcePoolURIs.add(existingPrimarySourcePool);        	    
        	    satisfiedSourceVolCount = 1;
        	}
        	else {
        	    satisfiedSourceVolCount = getCandidatePoolsForSource(sortedPrimarySourcePoolsMap, capabilities, requestedResourceCount, 
        	    								vpool, recommendations, primarySourcePoolURIs);
        	}
        	
        	_log.info("Determining RP placement for the primary (active) MetroPoint cluster.");
        	while (!primarySourcePoolURIs.isEmpty()) {
        		// Start with the top of the list of source pools, find a solution based on that.            
        		// Given the candidatePools.get(0), what protection systems and internal sites protect it?
        	    Set<ProtectionSystem> primaryProtectionSystems = new HashSet<ProtectionSystem>();
        	    ProtectionSystem cgProtectionSystem = getCgProtectionSystem(capabilities.getBlockConsistencyGroup());
        		StoragePool sourcePool = _dbClient.queryObject(StoragePool.class, primarySourcePoolURIs.get(0));
        		// If we have an existing RP consistency group we want to use the same protection system
                // used by other volumes in it. 
                if (cgProtectionSystem != null) {
                    BlockConsistencyGroup cg = _dbClient.queryObject(
                            BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
                    _log.info("Narrowing down placement to use protection system {}, which is currently used by RecoverPoint consistency group {}.", 
                            cgProtectionSystem.getLabel(), cg);
                    primaryProtectionSystems.add(cgProtectionSystem);
                } else {
                    primaryProtectionSystems = 
                            getProtectionSystemsForStoragePool(sourcePool, varray, true);
                    if (primaryProtectionSystems.isEmpty()) {
                        primarySourcePoolURIs.remove(0);
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
        		    
        		    List<String> primaryAssociatedStorageSystems = getCandidateVisibleStorageSystems(sourcePool, primaryProtectionSystem, varray, primaryProtectionVarrays, true);
        		    	
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
        			    										_dbClient, StorageSystemType.BLOCK);
        			            			 
						VirtualPool journalVpool = (NullColumnValueGetter.isNotNullValue(vpool.getJournalVpool()) ? 
    						_dbClient.queryObject(VirtualPool.class, URI.create(vpool.getJournalVpool())) : haVpool);
        				RPProtectionRecommendation primaryRecommendation = buildSourceRecommendation(primaryAssociatedStorageSystem, 
        																		varray, vpool, journalVarray, journalVpool, primaryProtectionSystem, sourcePool, 
        																		sortedPrimarySourcePoolsMap,  sortedActiveSourceJournalPoolsMap, 
        																		capabilities, satisfiedSourceVolCount, placementStatus, vpoolChangeVolume, false);

        				if (primaryRecommendation == null) {
        					// No source placement found for the primaryAssociatedStorageSystem, so continue.
        					continue;
        				}
        				
        				_log.info("An RP source placement solution has been identified for the MetroPoint primary (active) cluster.");
        				
        				// Find a solution, given this vpool, and the target varrays
        				if (findSolution(targetVarraySortedPoolMap, recommendations, primaryRecommendation, varray, vpool, primaryProtectionVarrays, capabilities, satisfiedSourceVolCount, true, null)) {                     	
        					// Check to ensure the protection system can handle the new resources about to come down
        					if (!verifyPlacement(primaryProtectionSystem, primaryRecommendation, primaryRecommendation.getResourceCount())) {
        						continue;
        					}
        					
        					_log.info("An RP target placement solution has been identified for the MetroPoint primary (active) cluster.");
        					
        					_log.info(primaryRecommendation.toString());
	                        
                			// We have a primary cluster protection recommendation for the specified metroPointType.  We need to now determine if we can
                			// protect the secondary cluster for the given metroPointType.
                        	_log.info("Determining RP placement for the secondary (standby) MetroPoint cluster.");
                        	secondaryRecommendationSolution = false;
                        	
                        	// Get the candidate secondary cluster source pools - sets secondarySourcePoolURIs.
                        	if (vpoolChangeVolume != null) {
                                // If this is a change vpool operation, the source has already been placed and there is only 1
                                // valid pool for the secondary side, the existing one. This is just to used to pass through the placement code.
                        	    URI existingSecondarySourcePool = sortedSecondarySourcePoolsMap.entrySet().iterator().next().getValue().get(0);
                        	    _log.info(String.format("Secondary Source Pool already exists, reuse pool: [%s].", existingSecondarySourcePool.toString()));
                        	    secondarySourcePoolURIs.add(existingSecondarySourcePool);
                            }
                            else {
                                getCandidatePoolsForSource(sortedSecondarySourcePoolsMap, capabilities, requestedResourceCount, haVpool, recommendations, secondarySourcePoolURIs);
                            }                        	    
                        	
                            secondaryPlacementStatus.setSrcVArray(haVarray.getLabel());
                            secondaryPlacementStatus.setSrcVPool(haVpool.getLabel());
                            
                        	while (!secondarySourcePoolURIs.isEmpty()) {        	
                        		// Start with the top of the list of source pools, find a solution based on that.            
                        		// Given the candidatePools.get(0), what protection systems and internal sites protect it?
                        		StoragePool secondarySourcePool = _dbClient.queryObject(StoragePool.class, secondarySourcePoolURIs.get(0));        	        	        	
                        		
                        		// Lookup source pool protection systems in the cache first.
                        		if (secondaryStoragePoolsToProtectionSystems.containsKey(secondarySourcePool.getId())) {
                        			secondaryProtectionSystems = secondaryStoragePoolsToProtectionSystems.get(secondarySourcePool.getId());
                        		} else {
                            		secondaryProtectionSystems = getProtectionSystemsForStoragePool(secondarySourcePool, haVarray, true);
                            		
                            		if (secondaryProtectionSystems.isEmpty()) {
                            			secondarySourcePoolURIs.remove(0);
                            			continue;
                            		}
                            		// Cache the result for this pool
                            		secondaryStoragePoolsToProtectionSystems.put(secondarySourcePool.getId(), secondaryProtectionSystems);	
                        		}
                        		
                        		ProtectionSystem selectedSecondaryProtectionSystem = null;
                        		
                        		// Ensure the we have a secondary protection system that matches the primary protection system
                        		for (ProtectionSystem secondaryProtectionSystem : secondaryProtectionSystems) {
                        			if (secondaryProtectionSystem.getId().equals(primaryRecommendation.getProtectionDevice())) {
                        				// We have a protection system match for this pool, continue.
                        				selectedSecondaryProtectionSystem = secondaryProtectionSystem;
                        				break;
                        			}
                        		}
                        		
                        		if (selectedSecondaryProtectionSystem == null) {
                        			// There is no protection system for this pool that matches the selected primary
                        			// protection system.  So lets try another pool.
                        			_log.info("RP Placement: Secondary source storage pool " + secondarySourcePool.getLabel() + " does not have connectivity to the selected primary protection system.");
                        			secondarySourcePoolURIs.remove(0);
                        			continue;
                        		} else { 
                        		    // List of concatenated Strings that contain the RP site + associated storage system.
                        		    List<String> secondaryAssociatedStorageSystems = getCandidateVisibleStorageSystems(secondarySourcePool, selectedSecondaryProtectionSystem, 
                        		    														haVarray, primaryProtectionVarrays, true);
	            	
                        			// Get candidate internal site names and associated storage system, make sure you check RP topology to see if the sites can protect that many targets            	            	
                        			if (secondaryAssociatedStorageSystems.isEmpty()) {
                        				// no rp site clusters connected to this storage system, should not hit this, but just to be safe we'll catch it
                        				_log.info("RP Placement: Protection System " + selectedSecondaryProtectionSystem.getLabel() +  " does not have an rp site cluster connected to Storage pool " + secondarySourcePool.getLabel());
                        				continue;
                        			}
                        			
                        			Set<String> sortedSecondaryAssociatedStorageSystems = new LinkedHashSet<String>();
                    			    Set<String> sameAsPrimary = new HashSet<String>();
                    			    
                        			// Perform a preliminary sorting operation.  We want to only consider secondary associated storage systems
                    			    // that reference the same storage system as the primary recommendation.  Also, want to prefer RP sites
                    			    // that are different
                        			for (String secondaryAssociatedStorageSystem : secondaryAssociatedStorageSystems) {  
                        				String secondarySourceInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(secondaryAssociatedStorageSystem);
                        				URI secondarySourceStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(secondaryAssociatedStorageSystem), 
                        																		_dbClient, StorageSystemType.BLOCK);                        				                        				
                        			    
                        				if (secondaryAssociatedStorageSystem.equals(
                        						primaryRecommendation.getRpSiteAssociateStorageSystem())) {
                        					sameAsPrimary.add(secondaryAssociatedStorageSystem);
                        				} else if (secondarySourceStorageSystemURI.equals(primarySourceStorageSystemURI) 
                        						&& !secondarySourceInternalSiteName.equals(primarySourceInternalSiteName)) {
                        					sortedSecondaryAssociatedStorageSystems.add(secondaryAssociatedStorageSystem);
                        				}
                        			}
                        			
                        			sortedSecondaryAssociatedStorageSystems.addAll(sameAsPrimary);
                        			
                        			for (String secondaryAssociatedStorageSystem : sortedSecondaryAssociatedStorageSystems) {
                        				journalVarray = (NullColumnValueGetter.isNotNullValue(vpool.getStandbyJournalVarray()) ?
                        				        _dbClient.queryObject(VirtualArray.class, URI.create(vpool.getStandbyJournalVarray())) : haVarray);
                    					journalVpool = (NullColumnValueGetter.isNotNullValue(vpool.getStandbyJournalVpool()) ? 
                    					        _dbClient.queryObject(VirtualPool.class, URI.create(vpool.getStandbyJournalVpool())) : haVpool);
                     			   
                    					RPProtectionRecommendation secondaryRecommendation = buildSourceRecommendation(secondaryAssociatedStorageSystem, haVarray, haVpool, 
                    																				journalVarray, journalVpool, selectedSecondaryProtectionSystem, 
                    																				secondarySourcePool, sortedSecondarySourcePoolsMap, 
                    																				sortedStandbySourceJournalPoolsMap, capabilities,
                    																				satisfiedSourceVolCount, secondaryPlacementStatus, null, true);                        				
                        				
                        				if (secondaryRecommendation == null) {
                        					// No source placement found for the secondaryAssociatedStorageSystem, so continue.
                        					continue;
                        				}

                        				// Find a solution, given this vpool, and the target varrays
                        				if (findSolution(targetVarraySortedPoolMap, recommendations, secondaryRecommendation, haVarray, vpool, secondaryProtectionVarrays, capabilities, satisfiedSourceVolCount, true, primaryRecommendation)) {
                        					// Check to ensure the protection system can handle the new resources about to come down
                        					if (!verifyPlacement(primaryProtectionSystem, secondaryRecommendation, primaryRecommendation.getResourceCount())) {
                        						continue;
                        					}
                        					
                        					_log.info("An RP target placement solution has been identified for the MetroPoint secondary (standby) cluster.");
                        					
                        					metroPointRecommendations.put(primaryRecommendation, secondaryRecommendation);
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
	                        			secondarySourcePoolURIs.remove(0);
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

                        	return metroPointRecommendations;
        				} else {
        					// Not sure there's anything to do here.  Just go to the next candidate protection system or Protection System
        					_log.info("Could not find a solution against protection system {} and internal cluster name {}", 
        							primaryProtectionSystem.getLabel(),
        							primarySourceInternalSiteName);
        				}
        			} // end of for loop trying to find solution using possible rp cluster sites
        		} // end of protection systems for loop
        		
        		// Remove the pool we were trying to use.
        		primarySourcePoolURIs.remove(0);            
        	} // end of candidate source pool while loop
        	
        	//we went through all the candidate pools and there are still some of the volumes that haven't been placed, then we failed to find a solution
	        if (primarySourcePoolURIs.isEmpty() && totalSatisfiedCount < capabilities.getResourceCount()) {
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
     * @param sourceInternalSiteName internal site name
     * @param journalVarray virtual array of the journal
     * @return size sorted journal storage pools map
     */
    private Map<Long, List<URI>> filterJournalPoolsByRPSiteConnectivity(Map<Long, List<URI>> sortedSourceJournalStoragePoolsMap, URI psId,
										String sourceInternalSiteName, VirtualArray journalVarray) {
    		boolean hasRPSiteVisibleJournals = false;
    		Map<Long, List<URI>> filteredPoolMap = new HashMap<Long, List<URI>>();
    		StringBuilder journalPoolListStr = new StringBuilder();
    	
    		for (Map.Entry<Long, List<URI>> journalPoolMap : sortedSourceJournalStoragePoolsMap.entrySet()) {    			
    			for (URI journalPoolUri : journalPoolMap.getValue()) {
    				StoragePool journalPool = _dbClient.queryObject(StoragePool.class, journalPoolUri);
    				    				
    				if (!isRpSiteConnectedToVarray(journalPool.getStorageDevice(), psId, sourceInternalSiteName, journalVarray)) {
    					_log.info(String.format("RP Journal Placement : StoragePool [%s] does NOT have connectivity to RP site [%s], ignoring this pool",
    											journalPool.getLabel(), sourceInternalSiteName));
    					continue;
    				}
    				
    				Long key = journalPoolMap.getKey();    				
    				if (filteredPoolMap.get(key) == null) {
    					filteredPoolMap.put(key, new ArrayList<URI>());
    				}    				
    				journalPoolListStr.append(journalPool.getLabel());
    				journalPoolListStr.append(",");
    				
    				filteredPoolMap.get(key).add(journalPoolUri);  
    				hasRPSiteVisibleJournals = true;
    			}
    		}    	
    		
    	_log.info(String.format("Following pools are considered for Journal volumes on source site %s - [%s]", sourceInternalSiteName, journalPoolListStr.toString()));
		return (hasRPSiteVisibleJournals ? filteredPoolMap : null);
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
    private RPProtectionRecommendation buildSourceRecommendation(String associatedStorageSystem, VirtualArray vArray, 
								    		VirtualPool vPool, VirtualArray journalVarray, VirtualPool journalVpool, ProtectionSystem ps, 
								    		StoragePool sourcePool, Map<Long, List<URI>> sortedSourcePoolsMap, Map<Long, List<URI>> 
    										sortedSourceJournalStoragePoolsMap, VirtualPoolCapabilityValuesWrapper capabilities,
								    		int satisfiedSourceVolCount, PlacementStatus placementStat, Volume vpoolChangeVolume, boolean isMPStandby) {
	    String sourceInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(associatedStorageSystem);
	    URI sourceStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem),
	    											_dbClient, StorageSystemType.BLOCK);
	    
		if (!isRpSiteConnectedToVarray(
				sourceStorageSystemURI, ps.getId(), sourceInternalSiteName, vArray)) {
			_log.info(String.format("RP Placement: Disqualified RP site [%s] because its initiators are not in a network configured for use by the virtual array [%s]",
					sourceInternalSiteName, vArray.getLabel()));
			return null;
		}            	
				  
        Map<Long, List<URI>> rpSiteVisibleJournalPools = filterJournalPoolsByRPSiteConnectivity(sortedSourceJournalStoragePoolsMap, ps.getId(), 
        																	sourceInternalSiteName, journalVarray);
		if (null == rpSiteVisibleJournalPools) {			
			_log.info(String.format("RP Journal Placement: Disqualified RP site [%s] because its initiators are not in a network configured for use by the virtual array [%s]", 
					sourceInternalSiteName, journalVarray.getLabel()));
			return null;
		}
        
		// Start over with the recommendation object
		RPProtectionRecommendation recommendation = new RPProtectionRecommendation();
		recommendation.setSourceDevice(sourcePool.getStorageDevice());
		recommendation.setSourceInternalSiteName(sourceInternalSiteName);  
		recommendation.setSourceInternalSiteStorageSystem(sourceStorageSystemURI);
		recommendation.setSourcePool(sourcePool.getId());
		recommendation.setRpSiteAssociateStorageSystem(associatedStorageSystem);
		
		// Primary source journal remains what it was before the change Vpool operation.  
		URI sourceJournalPoolURI;
		if (vpoolChangeVolume != null && vpoolChangeVolume.getRpJournalVolume() != null && !isMPStandby) {
			Volume existingJournalVolume = _dbClient.queryObject(Volume.class, vpoolChangeVolume.getRpJournalVolume());
			sourceJournalPoolURI = existingJournalVolume.getPool();
		} else {
			sourceJournalPoolURI = placeJournalStoragePool(rpSiteVisibleJournalPools, capabilities, satisfiedSourceVolCount,
															null, recommendation, vPool.getJournalSize());
		}
		recommendation.setSourceJournalStoragePool(sourceJournalPoolURI);		
    	
    	if (!isMPStandby) {
			recommendation.setSourceJournalVarray(journalVarray.getId());
			recommendation.setSourceJournalVpool(journalVpool.getId());
			_log.info(String.format("Setting recommendation for source (active in case of Metropoint): journal-varray [%s], journal-vpool [%s]", journalVarray.getLabel(), journalVpool.getLabel()));	
    	} else {
    		recommendation.setStandbySourceJournalVarray(journalVarray.getId());
    		recommendation.setStandbySourceJournalVpool(journalVpool.getId());
    		_log.info(String.format("Setting recommendation for metropoint stand-by source: journal-varray [%s], journal-vpool [%s]", journalVarray.getLabel(), journalVpool.getLabel()));	
    	}
		recommendation.setProtectionDevice(ps.getId());    
		recommendation.setVpoolChangeVolume(vpoolChangeVolume != null ? vpoolChangeVolume.getId() : null);
		recommendation.setVpoolChangeVpool(vpoolChangeVolume != null ? vPool.getId() : null);
		recommendation.setVpoolChangeProtectionAlreadyExists(vpoolChangeVolume != null ? vpoolChangeVolume.checkForRp() : false);
		recommendation.setResourceCount(satisfiedSourceVolCount);
		
		// If we made it this far we know that our source virtual pool and associated source virtual array 
		// has a storage pool with enough capacity for the requested resources and which is accessible to an rp cluster site                   
		recommendation.setPlacementStepsCompleted(PlacementProgress.IDENTIFIED_SOLUTION_FOR_SOURCE);
		if (placementStat.isBestSolutionToDate(recommendation)){
			placementStat.setLatestInvalidRecommendation(recommendation);
		}		
		return recommendation;
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
	    	placementStatusBuf.append(placementStatus.toString(_dbClient));
    	}
    	
    	if (secondaryPlacementStatus != null) {
	    	placementStatusBuf.append("\nSecondary Cluster");
	    	placementStatusBuf.append(secondaryPlacementStatus.toString(_dbClient));
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
		protectionSystems = ConnectivityUtil.getProtectionSystemsForStoragePool(_dbClient, storagePool, vArray.getId(), isRpVplex);
		
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
     * Schedule storage based on the incoming storage pools for source volumes. (New version)
     * 
     * @param varray varray requested for source
     * @param protectionVarrays Neighborhood to protect this volume to.
     * @param vpool vpool requested
     * @param capabilities parameters
     * @param preSelectedCandidatePools List of StoragePools already populated to choose from. RP+VPLEX. 
     * @param vpoolChangeVolume vpool change volume, if applicable
     * @param vpoolChangeParam 
     * @param protectionStoragePoolsMap pre-populated map for tgt varray to storage pools, use null if not needed
     * @return list of Recommendation objects to satisfy the request
     */
    protected List<Recommendation> scheduleStorageSourcePoolConstraint(VirtualArray varray,
            List<VirtualArray> protectionVarrays, VirtualPool vpool, VirtualPoolCapabilityValuesWrapper capabilities,
            List<StoragePool> preSelectedCandidatePools, Volume vpoolChangeVolume, 
            Map<VirtualArray, List<StoragePool>> preSelectedCandidateProtectionPoolsMap) {
        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        placementStatus = new PlacementStatus();
                
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
        // 
        // We can pretty much just work with the first storage pool in the candidate pool list per storage system.
        // 
        // Go through each storage pool, map to storage system to find connectivity
        // Try with the storagePoolList as it currently is.
        // If we get through the process and couldn't achieve full protection, we should
        // take out the matched pool from the storagePoolList and try again.
        
        int requestedResourceCount = capabilities.getResourceCount();
        int totalSatisfiedCount = 0;
        
        List<URI> protectionVarrayURIs = new ArrayList<URI>();
        for (VirtualArray vArray : protectionVarrays) {
        	protectionVarrayURIs.add(vArray.getId());
        	placementStatus.getProcessedProtectionVArrays().put(vArray.getId(), false);
        }
               
        List<URI> candidateSourcePoolURIs = new ArrayList<URI>();
        Map<Long, List<URI>> sortedPoolsMap = null;
        Map<Long, List<URI>> sourceJournalSortedPoolsMap = null;
        Map<URI, Map<Long, List<URI>>> varraySortedPoolMap = new HashMap<URI, Map<Long, List<URI>>>();
        
        // If there are any pre selected Source candidate pools, use these (example RP+VPLEX/MetroPoint).
        // Otherwise get the matching Source pools.
        if (preSelectedCandidatePools != null && !preSelectedCandidatePools.isEmpty()) {   
            _log.info(String.format("Source varray [%s] sorted pools:", varray.getLabel()));
            sortedPoolsMap = getSizeOrderedPoolsMap(preSelectedCandidatePools);
        }
        else {                    
            // Fetch the list of pools visible to the source varray
    		List<StoragePool> unsortedMatchingPools = _blockScheduler.getMatchingPools(varray, vpool, capabilities);
    		 _log.info(String.format("Source varray [%s] sorted pools:", varray.getLabel()));
    		sortedPoolsMap = getSizeOrderedPoolsMap(unsortedMatchingPools);
        }
        
        //Fetch the list of pools for the source journal if a journal virtual pool is specified to be used for journal volumes.
        VirtualArray journalVarray = varray;
        if (NullColumnValueGetter.isNotNullValue(vpool.getJournalVarray())) {
        	journalVarray = _dbClient.queryObject(VirtualArray.class, URI.create(vpool.getJournalVarray()));
        }
               
        VirtualPool journalVpool = vpool; 
        if (NullColumnValueGetter.isNotNullValue(vpool.getJournalVpool())) {
        	journalVpool = _dbClient.queryObject( VirtualPool.class, URI.create(vpool.getJournalVpool()));
        }
        List<StoragePool> unsortedSourceJournalStoragePools = _blockScheduler.getMatchingPools(journalVarray, journalVpool, capabilities);
		sourceJournalSortedPoolsMap = getSizeOrderedPoolsMap(unsortedSourceJournalStoragePools);
				
		StringBuffer journalPlacementLog = new StringBuffer();
		journalPlacementLog.append(String.format("Source varray : [%s--%s] , Source vpool [%s--%s] ", varray.getLabel(), varray.getId(), vpool.getLabel(), vpool.getId()));
		journalPlacementLog.append(String.format("Journal varray : [%s--%s], Journal vpool [%s--%s]", journalVarray.getLabel(), journalVarray.getId(), journalVpool.getLabel(), journalVpool.getId()));
		journalPlacementLog.append("Dumping journal storage pools:");
		
		for (Long journalSize : sourceJournalSortedPoolsMap.keySet()) {
			journalPlacementLog.append(String.format("Size: %s - pools [%s]", journalSize, Joiner.on("-").join(sourceJournalSortedPoolsMap.get(journalSize))));
		}
		
		_log.info(journalPlacementLog.toString());
  	
        // If there are any pre selected Target/Protection candidate pools, use these (example RP+VPLEX/MetroPoint).
        // Otherwise get the matching Target/Protection pools.
        if (preSelectedCandidateProtectionPoolsMap != null 
                && !preSelectedCandidateProtectionPoolsMap.isEmpty()) {
            for (VirtualArray protectionVarray : preSelectedCandidateProtectionPoolsMap.keySet()) {                
                _log.info(String.format("Target varray [%s] sorted pools:", protectionVarray.getLabel()));
                varraySortedPoolMap.put(protectionVarray.getId(), 
                                        getSizeOrderedPoolsMap(preSelectedCandidateProtectionPoolsMap.get(protectionVarray)));                
            }
        }
        else {
            // Construct a  Map of varrays to the list of pools that they can protect to and and pools are sorted in size ascending order.
            varraySortedPoolMap = getVarrayStoragePoolsBySize(protectionVarrayURIs, varray.getId(), vpool, capabilities);
        }
                               	        
        // The attributes below will not change throughout the placement process
        placementStatus.setSrcVArray(varray.getLabel());
        placementStatus.setSrcVPool(vpool.getLabel());
               
        BlockConsistencyGroup cg = _dbClient.queryObject(
                BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());

        while (totalSatisfiedCount < capabilities.getResourceCount()) {        	
        	boolean recommendationSolution = false;
        	int satisfiedSourceVolCount = getCandidatePoolsForSource(sortedPoolsMap, capabilities, requestedResourceCount, vpool, recommendations, candidateSourcePoolURIs);        
        	while (!candidateSourcePoolURIs.isEmpty()) {        	
        		// Start with the top of the list of source pools, find a solution based on that.            
        		// Given the candidatePools.get(0), what protection systems and internal sites protect it?
        	    Set<ProtectionSystem> protectionSystems = new HashSet<ProtectionSystem>();
        	    ProtectionSystem cgProtectionSystem = getCgProtectionSystem(capabilities.getBlockConsistencyGroup());
        		StoragePool sourcePool = _dbClient.queryObject(StoragePool.class, candidateSourcePoolURIs.get(0));        	        	        	
        		// If we have an existing RP consistency group we want to use the same protection system
        		// used by other volumes in it. 
        		if (cgProtectionSystem != null) {
        		    _log.info("Narrowing down placement to use protection system {}, which is currently used by RecoverPoint consistency group {}.", 
        		            cgProtectionSystem.getLabel(), cg);
        		    protectionSystems.add(cgProtectionSystem);
        		} else {
        		    protectionSystems = getProtectionSystemsForStoragePool(sourcePool, varray, VirtualPool.vPoolSpecifiesHighAvailability(vpool));
            		// Verify that the candidate pool can be protected
            		if (protectionSystems.isEmpty()) {
            			candidateSourcePoolURIs.remove(0);
            			continue;
            		}
        		}      	

                // Sort the ProtectionSystems based on the last time a CG was created. Always use the
        		// ProtectionSystem with the oldest cgLastCreated timestamp to support a round-robin
        		// style of load balancing.
                List<ProtectionSystem> protectionSystemsLst = 
                        sortProtectionSystems(protectionSystems);
        		
        		for (ProtectionSystem candidateProtectionSystem : protectionSystemsLst) {
                    Calendar cgLastCreated = candidateProtectionSystem.getCgLastCreatedTime();
                    _log.info("Attempting to use protection system {}, which was last used to create a CG on {}.", 
                            candidateProtectionSystem.getLabel(), cgLastCreated != null ? cgLastCreated.getTime().toString() : "N/A");        		    
        		    
        		    List<String> associatedStorageSystems = new ArrayList<String>();
        		    String internalSiteNameandAssocStorageSystem = getCgSourceInternalSiteNameAndAssociatedStorageSystem(capabilities.getBlockConsistencyGroup());
                
        		    // If we have existing source volumes in the RP consistency group, we want to use the same
        		    // source internal site.
        		    if (internalSiteNameandAssocStorageSystem != null) {
        		        _log.info("Narrowing down placement to use source internal site {}, which is currently used by RecoverPoint consistency group {}.", 
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
        			    String candidateInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(associatedStorageSystem);
        			
        				RPProtectionRecommendation recommendation = buildSourceRecommendation(associatedStorageSystem, varray, vpool, journalVarray, journalVpool, 
        																		candidateProtectionSystem, sourcePool, sortedPoolsMap, sourceJournalSortedPoolsMap,
        																		capabilities, satisfiedSourceVolCount, placementStatus, vpoolChangeVolume, false);
        				
        				if (recommendation == null) {
        					// No placement found for the associatedStorageSystem, so continue.
        					continue;
        				}

        				// Find a solution, given this vpool, and the target varrays
        				if (findSolution(varraySortedPoolMap, recommendations, recommendation, varray, vpool, protectionVarrays, 
        							capabilities, satisfiedSourceVolCount, false, null)) {                     	
        					// Check to ensure the protection system can handle the new resources about to come down
        					if (!verifyPlacement(candidateProtectionSystem, recommendation, recommendation.getResourceCount())) {
        						continue;
        					}

        					// We need to create recommendations for one or more pools
        					// that can accommodate the number of requested resources.
        					// We start by trying to place all resources in a single
        					// pool if one exists that can accommodate all requested
        					// resources and work our way down as necessary trying to
        					// minimize the number of pools used to satisfy the request.        					
        					requestedResourceCount = requestedResourceCount - satisfiedSourceVolCount;
	                        totalSatisfiedCount += satisfiedSourceVolCount;
	                        recommendations.add(recommendation);	                    
	                        recommendationSolution = true;
	                        
	                        // Joe:  need this when we are creating multiple recommendations
        					// placementStatus.setLatestInvalidRecommendation(null);
	                        
	                        if ((totalSatisfiedCount >= capabilities.getResourceCount())) {	                        
	                        	for (Recommendation rec : recommendations) {
	                        		 _log.info("RP Placement: Found a recommendation for the request:\n" + ((RPProtectionRecommendation)rec).toString(_dbClient)); 
	                        	}
	                        	// for testing	                        	
                                // if (true) throw APIException.badRequests.cannotFindSolutionForRP(placementStatus.toString(_dbClient));
	                        	return recommendations;
	                        }
        				} else {
        					// Not sure there's anything to do here.  Just go to the next candidate protection system or Protection System
        					_log.info("Could not find a solution against protection system {} and internal cluster name {}", 
        							candidateProtectionSystem.getLabel(),
        							candidateInternalSiteName);
        				}
        			} // end of for loop trying to find solution using possible rp cluster sites
        		} // end of protection systems for loop
        		
        		//if we found a solution for the requestedResourceCount number of source volumes, then break, try for remaining resource count if any
        		if (recommendationSolution) {
        			break;
        		} else {
        			// Remove the pool we were trying to use.
        			candidateSourcePoolURIs.remove(0);
        		}            
        	} // end of candidate source pool while loop
        	
        	//we went through all the candidate pools and there are still some of the volumes that haven't been placed, then we failed to find a solution
	        if (candidateSourcePoolURIs.isEmpty() && totalSatisfiedCount < capabilities.getResourceCount()) {
	        	_log.info("Couldnt find a solution");
	        	throw APIException.badRequests.cannotFindSolutionForRP(placementStatus.toString(_dbClient));	        	
	        }        	
        }
        _log.error("ViPR could not find matching target storage pools that could be protected via RecoverPoint"); 
    	throw APIException.badRequests.cannotFindSolutionForRP(placementStatus.toString(_dbClient));
    }    

    /**
     * Print out the sorted storage pools
     * 
     * @param varraySortedPoolMap Sorted Storage Pools
     */
    private void printSortedStoragePools(Map<Long, List<URI>> sortedPoolMap) {                            
        for (Map.Entry<Long, List<URI>> sortedPoolEntry : sortedPoolMap.entrySet()) {
            StringBuffer sortedPoolNames = new StringBuffer(); 
            for (URI sortedPoolURI : sortedPoolEntry.getValue()) {
                StoragePool sortedPool = _dbClient.queryObject(StoragePool.class, sortedPoolURI);
                sortedPoolNames.append(sortedPool.getLabel() + " ");
            }
            _log.info(String.format("Free capacity: %s, Storage Pools: %s", 
                                        sortedPoolEntry.getKey().toString(), 
                                        sortedPoolNames.toString()));
        }  
        
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
        List<Volume> cgVolumes = _rpHelper.getCgVolumes(blockConsistencyGroupUri);
        
        if (cgVolumes != null && !cgVolumes.isEmpty()) {
        	for (Volume cgVolume : cgVolumes) {
        		if (cgVolume.getProtectionController() != null) {
        			return _dbClient.queryObject(
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
        List<Volume> cgSourceVolumes = _rpHelper.getCgVolumes(blockConsistencyGroupUri, Volume.PersonalityTypes.SOURCE.toString());
        
        if (!cgSourceVolumes.isEmpty()) {
            Volume cgVol = cgSourceVolumes.get(0);
            String sourceInternalSiteName = cgVol.getInternalSiteName();
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, cgVol.getStorageController());

        	// Special check for VPLEX
            if (ConnectivityUtil.isAVPlex(storageSystem)) {
            	// Determine the proper serial number for the volume object provided.
                String clusterId = ConnectivityUtil.getVplexClusterForVarray(cgVol.getVirtualArray(), storageSystem.getId(), _dbClient);
                
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
    private int getCandidatePoolsForSource(Map<Long, List<URI>> orderedPools, VirtualPoolCapabilityValuesWrapper capabilities, int resourceCount, VirtualPool vpool,
    		List<Recommendation> recommendations, List<URI>candidatePoolsForSource) {    
    	long volSize = 0L;     	
    	Set<URI> poolsInRecommendation = poolsInAllRecommendations(recommendations); 
    	while (resourceCount > 0) {
    		candidatePoolsForSource.clear();
    		for (Long poolSize : orderedPools.keySet()) {
    			volSize = getSizeInKB(capabilities.getSize() * resourceCount);    	
    			if (poolSize > volSize) {					
    				for (URI candidatePool : orderedPools.get(poolSize) ) {
    					if (poolsInRecommendation.contains(candidatePool)) {
    						continue;				
    					}
    					candidatePoolsForSource.add(candidatePool);						
    				}			
    			}				
    		}    	
    		if (candidatePoolsForSource.isEmpty()) {
    			resourceCount--;	    		
    		}
    		else {
    			return resourceCount;
    		}
    	}    	
    	return 0;
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
    private URI placeJournalStoragePool(Map<Long, List<URI>> candidatePoolsBySize, VirtualPoolCapabilityValuesWrapper capabilities, 
    									int satisfiedResourceCount, List<Recommendation> recommendations, 
    									RPProtectionRecommendation recommendation, String journalPolicy) {    	
    	
    	Map<Long, List<URI>> candidatePools = new HashMap<Long, List<URI>>();
    	//calculate the total size requested
    	long journalSizePerPolicy = RPHelper.getJournalSizeGivenPolicy(String.valueOf(capabilities.getSize()), journalPolicy, satisfiedResourceCount);
    	long totalCapacityRequestedInKB = getSizeInKB(journalSizePerPolicy * satisfiedResourceCount);
    	
    	Set<URI> poolsAlreadyInRecommendation = null;       	
    	//Eliminate storage pools in the candidate pool list that cannot satisfy this requested size.    
		for (Long poolSize : candidatePoolsBySize.keySet()) {
			if (poolSize > totalCapacityRequestedInKB) {
				candidatePools.put(poolSize, candidatePoolsBySize.get(poolSize));
			}
		}

    	//Next, lets get all the pools that are already consumed by the recommendation(s)
    	poolsAlreadyInRecommendation = getAllPoolsInRecommendations(recommendations, recommendation);    	
    	for (Long poolSize : candidatePools.keySet()) {
    		for(URI poolUri : candidatePools.get(poolSize)) {
	    		if (poolsAlreadyInRecommendation.contains(poolUri))  {
	    			continue;
	    		}	    		
	    		StoragePool pool = _dbClient.queryObject(StoragePool.class, poolUri);
	    		_log.info(String.format("found pool [%s] for journal", pool.getLabel()));
	    		return poolUri;
    		}
    	}
    	
    	//TODO: We never had a way to handle the case when journal pools didnt have enough capacity for the given count # of volumes and journal policy
    	//Need to handle that better. This has been the case since day-1, so we need to fix that.
    	    	        
    	//If we got here, then we couldnt find a pool that is not already in the recommendation.
    	//return the first pool that is big enough (assuming its big enough for now, from the largest to smallest ordered pool list) to satisfy the request. 
    	
    	//candidatePoolsBySize is not empty in this method, since if there were no pools available, we wouldnt enter this method and fail much earlier.
    	URI poolUri = candidatePoolsBySize.get(candidatePoolsBySize.keySet().iterator().next()).get(0);
    	StoragePool pool = _dbClient.queryObject(StoragePool.class, poolUri);
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
    private Map<URI, Map<Long, List<URI>>> getVarrayStoragePoolsBySize(List<URI> protectionVarrayURIs, URI sourceVarrayURI, VirtualPool vpool, VirtualPoolCapabilityValuesWrapper capabilities) {
    	Map<URI, Map<Long, List<URI>>> varrayPoolsSortedBySize = new HashMap<URI, Map<Long, List<URI>>>(); 
    	
    	//start with all the protection varrays
    	for (URI varrayURI : protectionVarrayURIs) {    		
    		VirtualArray targetVarray = _dbClient.queryObject(VirtualArray.class, varrayURI);
    		// Find the pools that apply based on the varray protection settings
        	VpoolProtectionVarraySettings settings = _rpHelper.getProtectionSettings(vpool, targetVarray);
            // If there was no vpool specified with the protection settings, use the base vpool for this varray.
            VirtualPool targetVpool = vpool;
            if (settings.getVirtualPool()!=null) {
                targetVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
            }
            
            //Get all the storage pools visible to this varray and vpool
            List<StoragePool> unsortedMatchingPools = _blockScheduler.getMatchingPools(targetVarray, targetVpool, capabilities);                                 
            // If there are existing volumes in the RP consistency group, we need to make sure we only consisder
            // those storage pools associated with CG protection system.
            ProtectionSystem protectionSystem = getCgProtectionSystem(capabilities.getBlockConsistencyGroup());
            
            if (protectionSystem != null) {
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
                _log.info("RecoverPoint consistency group {} is already tied to protection system {}.  Removing target storage pools " +
                		"that are not protected by this protection system.", cg.getLabel(), protectionSystem.getLabel());
                Iterator<StoragePool> unsortedMatchingPoolsItr = unsortedMatchingPools.iterator();
                StoragePool storagePool = null;
                while (unsortedMatchingPoolsItr.hasNext()) {
                    storagePool = unsortedMatchingPoolsItr.next();
                    StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storagePool.getStorageDevice()); 
                    StringSet associatedStorageSystems = protectionSystem.getAssociatedStorageSystemsWithString(storageSystem.getSerialNumber());
                    if (associatedStorageSystems.isEmpty()) {
                        _log.info("Removing storage pool {} from list of protection virtual array candidate pools.  " +
                        		"The storage pool is not protected by {}", storagePool.getLabel(), protectionSystem.getLabel());
                        unsortedMatchingPoolsItr.remove();
                    }
                }
            }
            // We have all the pools now, sort them based on size.
            _log.info(String.format("Target varray [%s] sorted pools:", targetVarray.getLabel()));
            Map<Long, List<URI>> sortedPoolsMap = getSizeOrderedPoolsMap(unsortedMatchingPools);
            if (varrayPoolsSortedBySize.get(varrayURI) == null) {
            	varrayPoolsSortedBySize.put(varrayURI, new HashMap<Long, List<URI>>());            	
            }
            
            varrayPoolsSortedBySize.put(varrayURI, sortedPoolsMap);                                 
    	}
    		    	
    	return varrayPoolsSortedBySize;
    }
        

    /**
     * Construct a sorted pool list, sorted by size with size 
     * as the key and List<StoragePool> as value.
     * 
     * Sorting will be Highest (most free capacity) to Lowest (least amount of capacity).
     * 
     * @param unsortedPools
     * @return Map of List of pools sorted by free capacity as the key
     */
    private Map<Long, List<URI>> getSizeOrderedPoolsMap(List<StoragePool> unsortedPools) {
    	Map<Long, List<URI>> sortedPoolsMap = new TreeMap<Long, List<URI>>(Collections.reverseOrder());
    	for (StoragePool storagePool : unsortedPools) {
    		Long freeCapacity = storagePool.getFreeCapacity();
    		if (sortedPoolsMap.get(freeCapacity) == null) {
    			sortedPoolsMap.put(freeCapacity, new ArrayList<URI>());
    		}
    		sortedPoolsMap.get(freeCapacity).add(storagePool.getId());
    	}       
    	
    	printSortedStoragePools(sortedPoolsMap);

    	return sortedPoolsMap;
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
    private URI getTargetPool(URI candidateTargetPoolURI, List<URI> candidatePoolsURI, VirtualPoolCapabilityValuesWrapper capabilities, List<Recommendation> recommendations, RPProtectionRecommendation recommendation, VirtualPool protectionVpool, int count) { 
    	URI poolURI = null;
    	long totalCapacityRequestedInKB = getSizeInKB(capabilities.getSize() * count);    	
    	    	
    	//TODO (Brad/Bharath): ChangeVPool doesnt add any new targets. If new targets are requested as part of the changeVpool, then this code needs to be enhanced
    	//to be able to handle that. 
    	
    	//First, lets get all the pools that are already consumed by the recommendation(s)
    	Set<URI> poolsAlreadyInRecommendation = getAllPoolsInRecommendations(
    			recommendations, recommendation);

    	if (poolsAlreadyInRecommendation.contains(candidateTargetPoolURI)) {    		 
    		List<URI> availablePools = new ArrayList<URI>();   		    
    		for (URI  storagePoolURI : candidatePoolsURI) {
    			if (poolsAlreadyInRecommendation.contains(storagePoolURI))  {
    				continue;
    			}					
    			StoragePool storagePool = _dbClient.queryObject(StoragePool.class, storagePoolURI);
    			if (storagePool.getFreeCapacity() > totalCapacityRequestedInKB ) {
    				availablePools.add(storagePoolURI);							
    			}
    		}

    		if (!availablePools.isEmpty()) {
    			poolURI = availablePools.get(0);
    		} else {							
    			for (URI usedPoolURI : poolsAlreadyInRecommendation ) {
    				if (usedPoolURI.equals(recommendation.getSourcePool()) || usedPoolURI.equals(recommendation.getSourceJournalStoragePool())) {
    					continue;
    				} else {
    					//Find a pool that is already being used in the recommendation, we need a pool that was used as a Target storage pool.
    					//we dont want to use a pool that was selected previously for Source/SourceJournal.
    					poolURI = usedPoolURI;
    					break;
    				}
    			}
    			//Last resort : if no viable pool has been found up until now, then use the same pool as the source storage pool
    			if (poolURI == null) {
    				poolURI = recommendation.getSourcePool();
    			}
    		}
    	} else {
    		//not used in any recommendation already
            StoragePool candidateTargetPool = _dbClient.queryObject(StoragePool.class, candidateTargetPoolURI);
            if (candidateTargetPool.getFreeCapacity() > totalCapacityRequestedInKB) {
            	poolURI = candidateTargetPoolURI;
            }
            else {
            	poolURI = null;
            }
    	}
    	return poolURI;
    }
    
	/** Returns a list of all the pools that are already in recommendations as well as part of the current recommendation that we are populating. 
	 * @param recommendations
	 * @param recommendation
	 * @return
	 */
	private Set<URI> getAllPoolsInRecommendations(List<Recommendation> recommendations,
							RPProtectionRecommendation recommendation) {
		Set<URI> poolsAlreadyInRecommendation;
		
		if (recommendations != null && !recommendations.isEmpty()) {
			poolsAlreadyInRecommendation = poolsInAllRecommendations(recommendations);
		} else {
    		poolsAlreadyInRecommendation = poolsInThisRecommendation(recommendation); 
    	}
		
		return poolsAlreadyInRecommendation;
	}
    
    
    /** List of pools that are already used and in the recommendations
     * 
     * @param recommendations
     * @return
     */
    private Set<URI> poolsInAllRecommendations(List<Recommendation> recommendations)  {
    	Set<URI> poolsAlreadyInRecommendation = new HashSet<URI>();
    	
    	for (Recommendation recommendation : recommendations) {    		
    		poolsAlreadyInRecommendation.add(recommendation.getSourcePool());    		
    		RPProtectionRecommendation protectionRecommendation = (RPProtectionRecommendation)recommendation;    		
    		poolsAlreadyInRecommendation.add(protectionRecommendation.getSourceJournalStoragePool());
    		 Map<URI, Protection> protectionMap = protectionRecommendation.getVirtualArrayProtectionMap();
    		 for (URI varrayURI : protectionMap.keySet()){
    			 Protection protection = protectionMap.get(varrayURI);
    			 poolsAlreadyInRecommendation.add(protection.getTargetJournalStoragePool());
    			 poolsAlreadyInRecommendation.add(protection.getTargetStoragePool());    			 
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
    private Set<URI> poolsInThisRecommendation(RPProtectionRecommendation recommendation)  {
    	Set<URI> poolsAlreadyInRecommendation = new HashSet<URI>();
    	
    	poolsAlreadyInRecommendation.add(recommendation.getSourcePool());
    	poolsAlreadyInRecommendation.add(recommendation.getSourceJournalStoragePool());
    	for (URI varrayURI : recommendation.getVirtualArrayProtectionMap().keySet()) {
    		Protection protection = recommendation.getVirtualArrayProtectionMap().get(varrayURI);
    		poolsAlreadyInRecommendation.add(protection.getTargetStoragePool());
    		poolsAlreadyInRecommendation.add(protection.getTargetJournalStoragePool());    		
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
                .getVPlexSystemsAssociatedWithArray(_dbClient, srcPool.getStorageDevice(),
                    new HashSet<String>(Arrays.asList(sourceVarray.getId().toString())), null);
        }
    	
    	for (String associatedStorageSystem : candidateProtectionSystem.getAssociatedStorageSystems()) {
    	    URI storageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem), _dbClient, StorageSystemType.BLOCK);
            
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
	    _log.info("RP Placement: Trying to find the RP cluster candidates for the target...");
	    
    	List<String> validAssociatedStorageSystems = new ArrayList<String>();
		ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, protectionDevice);
		
		Set<URI> vplexs = null;
        // If this is an RP+VPLEX or MetroPoint request, we need to find the VPLEX(s). We are only interested in 
		// connectivity between the RP Sites and the VPLEX(s). The backend Storage Systems are irrelevant in this case. 
        if (isRPVPlex) {
            _log.info("RP Placement: This is an RP+VPLEX/MetroPoint request.");
            // Find the VPLEX(s) associated to the Storage System (derived from Storage Pool) and varray
           vplexs = ConnectivityUtil.getVPlexSystemsAssociatedWithArray(_dbClient, targetPool.getStorageDevice(),
               new HashSet<String>(Arrays.asList(targetVarray.getId().toString())), null);           
        }		
		
    	for (String associatedStorageSystem : protectionSystem.getAssociatedStorageSystems()) {
    	    boolean validAssociatedStorageSystem = false;
    	    URI storageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem), _dbClient, StorageSystemType.BLOCK);
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
    	
    	StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, ConnectivityUtil.findStorageSystemBySerialNumber(serialNumber, _dbClient, StorageSystemType.BLOCK));
    	if (storageSystem == null) {
    		return false;
    	}
    	
    	// Special check for VPLEX
        if (ConnectivityUtil.isAVPlex(storageSystem)) {           
            String clusterId = storageSystem.getVplexAssemblyIdtoClusterId().get(serialNumber);            
            
            // Check to see if this varray and VPLEX cluster match up, if so we're Ok to use it.
            return VPlexUtil.checkIfVarrayContainsSpecifiedVplexSystem(varray.getId().toString(), clusterId, storageSystem.getId(), _dbClient);
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
			
			if (_rpHelper.isInitiatorInVarray(varray, endpoint)) {
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
     * @param satisfiedResourceCount the resource count.
     * @param metroPointType the MetroPoint type.
     * @param primaryRecommendation the primary recommendation in the case of a MetroPoint request.  This will be populated only
     *                              when the request pertains to the secondary recommendation, so we can pull information from the
     *                              primary recommendation.
     * @return true if a protection recommendation can be found, false otherwise.
     */
	private boolean findSolution(Map<URI, Map<Long, List<URI>>>  varrayOrderedPoolList, List<Recommendation> recommendations, RPProtectionRecommendation recommendation,
			VirtualArray varray, VirtualPool vpool, List<VirtualArray> protectionVarrays, VirtualPoolCapabilityValuesWrapper capabilities, int satisfiedResourceCount, boolean isMetroPoint, 
			RPProtectionRecommendation primaryRecommendation) {
		
		if (protectionVarrays.isEmpty()) {
			_log.info("Could not find target solution because there are no protection virtual arrays specified.");
			return false;
		}
    	// Find the virtual pool that applies to this protection virtual array		
    	VirtualArray protectionVarray = protectionVarrays.get(0);    	    
    	placementStatus.getProcessedProtectionVArrays().put(protectionVarray.getId(), true);
    	
    	// Find the pools that apply to this virtual
    	VpoolProtectionVarraySettings protectionSettings = _rpHelper.getProtectionSettings(vpool, protectionVarray);
        // If there was no vpool specified with the protection settings, use the base vpool for this varray.
        VirtualPool protectionVpool = vpool;
        if (protectionSettings.getVirtualPool() != null) {
            protectionVpool = _dbClient.queryObject(VirtualPool.class, protectionSettings.getVirtualPool());          
        }

        _log.info("Current varray : " + protectionVarray.getLabel());
        
        // Find matching pools for the protection varray
        Map<Long, List<URI>> sortedPoolList = varrayOrderedPoolList.get(protectionVarray.getId());  
                
        // Create a list of storage pool URIs
        List<URI> targetStoragePoolsURI = new ArrayList<URI>();
        for (Long size : sortedPoolList.keySet()) {
        	targetStoragePoolsURI.addAll(sortedPoolList.get(size));
        }
                
        VirtualArray targetJournalVarray = protectionVarray;
        if (!NullColumnValueGetter.isNullURI(protectionSettings.getJournalVarray())) {
        	 targetJournalVarray = _dbClient.queryObject(VirtualArray.class, protectionSettings.getJournalVarray());
        }
        
        VirtualPool targetJournalVpool = _dbClient.queryObject(VirtualPool.class, protectionVpool.getId());
        if(!NullColumnValueGetter.isNullURI(protectionSettings.getJournalVpool())) {
        	targetJournalVpool = _dbClient.queryObject(VirtualPool.class, protectionSettings.getJournalVpool());
        }
        
        List<StoragePool> targetJournalPools = new ArrayList<StoragePool> ();        
        for (StoragePool targetJournalPool :  _blockScheduler.getMatchingPools(targetJournalVarray, targetJournalVpool, capabilities)) {        	
        	targetJournalPools.add(targetJournalPool);
        }
        Map<Long, List<URI>> sortedJournalPoolsMap = getSizeOrderedPoolsMap(targetJournalPools);
        
        // TODO: Distill the storage pools into their storage systems so we can determine protection system and internal site name connectivity        
        for (URI targetPoolURI: targetStoragePoolsURI) {       

        	targetPoolURI = getTargetPool(targetPoolURI, targetStoragePoolsURI, capabilities, recommendations, recommendation, protectionVpool, satisfiedResourceCount);
        	
        	// If targetPoolURI is null, meaning capacity doesn't match request, continue to the next pool in the list
        	if (targetPoolURI == null) { 
        		continue;
        	}
        	
        	StoragePool targetPool = _dbClient.queryObject(StoragePool.class, targetPoolURI);        	        	
        	            	
        	List<String> associatedStorageSystems = getCandidateTargetVisibleStorageSystems(recommendation.getProtectionDevice(), 
        	                                                                                protectionVarray, recommendation.getSourceInternalSiteName(), 
        	                                                                                targetPool, VirtualPool.vPoolSpecifiesHighAvailability(protectionVpool));
        	
        	if (associatedStorageSystems.isEmpty()) {
        		_log.info("Solution cannot be found using target pool " + targetPool.getLabel() + " there is no connectivity to rp cluster sites.");
        		continue;
        	}
        	
        	// We want to find an internal site name that isn't already in the solution
        	for (String associatedStorageSystem : associatedStorageSystems) {
        	    String targetInternalSiteName = ProtectionSystem.getAssociatedStorageSystemSiteName(associatedStorageSystem);
        	    URI targetStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystem), _dbClient, StorageSystemType.BLOCK);
                
        		if (!recommendation.containsTargetInternalSiteName(targetInternalSiteName)) {
        		    StoragePool tgtPool = _dbClient.queryObject(StoragePool.class, targetPoolURI);
        		    ProtectionType protectionType = null;
        		    
        		    // MetroPoint has been specified so process the MetroPoint targets accordingly.
        		    if (isMetroPoint) {
                        if (targetInternalSiteName.equals(recommendation.getSourceInternalSiteName())) {
                            // A local protection candidate.
                            if (isMetroPointProtectionSpecified(recommendation, ProtectionType.LOCAL)) {
                                // We already have protection specified for the local type
                                // so continue onto the next candidate RP site.
                                continue;
                            }
                            
                            // Add the local protection
                            protectionType = ProtectionType.LOCAL;
                        } else if (!targetInternalSiteName.equals(recommendation.getSourceInternalSiteName())) {
                            if (isMetroPointProtectionSpecified(recommendation, ProtectionType.REMOTE)) {
                            	// We already have remote protection specified so continue onto the next 
                            	// candidate RP site.
                                continue;
                            } else {
                            	if (primaryRecommendation != null) {
                                    String primaryTargetInternalSiteName = getMetroPointRemoteTargetRPSite(primaryRecommendation);
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
        		    
        			// Check to make sure the RP site is connected to the varray
        			if (!isRpSiteConnectedToVarray(
					        targetStorageSystemURI, recommendation.getProtectionDevice(), targetInternalSiteName, protectionVarray)) {
							_log.info(String.format("RP Placement: Disqualified RP site [%s] because its initiators are not in a network configured for use by the virtual array [%s]",
		    									targetInternalSiteName, protectionVarray.getLabel()));
		    				continue;
        			}
        			
        			_log.info(String.format("RP Placement identified RP Site [%s], verify if journal storage has visiblity to the same RP Site",  targetInternalSiteName));        			
     			   Map<Long, List<URI>> rpSiteVisibleJournalPools = filterJournalPoolsByRPSiteConnectivity(
     					   												sortedJournalPoolsMap, recommendation.getProtectionDevice(), 
     					   												targetInternalSiteName, targetJournalVarray);     			   										 
 					if (null == rpSiteVisibleJournalPools) {
 						_log.info(String.format("RP Journal Placement: Disqualified RP site [%s] because its initiators are not in a network configured for use by the virtual array [%s]", 
 														targetInternalSiteName, targetJournalVarray.getLabel()));
 						continue;
 					}
        			
        			// Maybe make a topology check in here?  Or is the source topology check enough?        			
        			// Add the protection object to the recommendation
        			Protection protection = new Protection();
        			if (protectionType != null) {
        				protection.setProtectionType(protectionType);
        			}
        			protection.setTargetInternalSiteName(targetInternalSiteName);
        			protection.setTargetInternalSiteStorageSystem(targetStorageSystemURI);
        			protection.setTargetStoragePool(targetPoolURI);
        			protection.setTargetDevice(tgtPool.getStorageDevice());
        			URI  tgtJournalPoolURI  = placeJournalStoragePool(rpSiteVisibleJournalPools, capabilities, satisfiedResourceCount,  
        																recommendations, recommendation, protectionSettings.getJournalSize());
        			_log.info(String.format("Setting recommendation for TARGET: Journal-varray [%s] -- Journal-vpool [%s]", targetJournalVarray.getLabel(), targetJournalVpool.getLabel()));
        			protection.setTargetJournalVarray(targetJournalVarray.getId());
        			protection.setTargetJournalVpool(targetJournalVpool.getId());
        			protection.setTargetJournalStoragePool(tgtJournalPoolURI);         	
        			protection.setTargetJournalDevice(_dbClient.queryObject(StoragePool.class, tgtJournalPoolURI).getStorageDevice());
        			recommendation.getVirtualArrayProtectionMap().put(protectionVarray.getId(), protection);
        			
        			// Set the placement status to reference either the primary or secondary.
        			PlacementStatus tmpPlacementStatus = placementStatus;
        			if (primaryRecommendation != null) {
        				tmpPlacementStatus = secondaryPlacementStatus;
        			}
        			
        			// At this point we have found a target storage pool accessible to the protection vPool and protection vArray
        			// that can be protected by an rp cluster site that is part of the same rp system that can protect the source storage pool
        			recommendation.setPlacementStepsCompleted(PlacementProgress.IDENTIFIED_SOLUTION_FOR_SUBSET_OF_TARGETS);
        			if (tmpPlacementStatus.isBestSolutionToDate(recommendation)) {                		
        				tmpPlacementStatus.setLatestInvalidRecommendation(recommendation);
                	}  			        			
        			
        			if (isMetroPoint) {
        				if (recommendation.getVirtualArrayProtectionMap() != null &&
        						recommendation.getVirtualArrayProtectionMap().size() == protectionVarrays.size()) {
        					finalizeTargetPlacement(recommendation, tmpPlacementStatus);
        					return true;
        				}
        			} else if (protectionVarrays.size() == 1) {
        				finalizeTargetPlacement(recommendation, tmpPlacementStatus);
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

        			if (!this.findSolution(varrayOrderedPoolList, recommendations, recommendation, varray, vpool, remainingVarrays, capabilities, satisfiedResourceCount, isMetroPoint, primaryRecommendation)) {
        				// Remove the current recommendation and try the next site name, pool, etc.
        				_log.info("RP Placement: Solution for remaining virtual arrays couldn't be found.  Trying different solution (if available) for varray: " + protectionVarray.getLabel()); 
        				recommendation.getVirtualArrayProtectionMap().remove(protectionVarray.getId());
        			} else {
        				// We found a good solution
        				_log.info("RP Placement: Solution for remaining virtual arrays was found.  Returning to caller.  Varray: " + protectionVarray.getLabel());
        				return true;
        			}
        		}
        	}
        }
        
        // If we get here, the recommendation object never got a new protection object, and we just return false, which will move onto the next possibility (in the case of a recursive call)
        _log.info("Solution cannot be found, will try again with different pool combination");
		return false;
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
		for (URI protectionVarrayUri : recommendation.getVirtualArrayProtectionMap().keySet()) {
			Protection protection = recommendation.getVirtualArrayProtectionMap().get(protectionVarrayUri);
			if (protection.getProtectionType() == ProtectionType.REMOTE) { 
				targetInternalSiteName = protection.getTargetInternalSiteName();
				break;
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
		if (recommendation.getVirtualArrayProtectionMap() != null
				&& recommendation.getVirtualArrayProtectionMap().size() > 0) { 
			for (URI protectionVarrayUri : recommendation.getVirtualArrayProtectionMap().keySet()) {
				Protection existingProtection = 
						recommendation.getVirtualArrayProtectionMap().get(protectionVarrayUri);
				if (existingProtection.getProtectionType() == protectionType) {
					return true;
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
		String sourceCluster = ConnectivityUtil.getVplexClusterForVarray(srcVarray.getId(), sourceVplexStorageSystem, _dbClient);
		// Get the Vplex cluster associated with the protection virtual array.
		String targetCluster = ConnectivityUtil.getVplexClusterForVarray(protectionVarray.getId(), protectionVplexStorageSystem, _dbClient);
		
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
     * Scheduler for a Vpool change from an unprotected volume to a protected volume.
     *
     * @param volume volume that is being changed to a protected vpool
     * @param vpool vpool requested to change to (must be protected)
     * @param protectionVarrays Varrays to protect this volume to.
     * @return list of Recommendation objects to satisfy the request
     */
    public List<Recommendation> scheduleStorageForVpoolChangeUnprotected(Volume volume, VirtualPool vpool,
        List<VirtualArray> protectionVarrays, VirtualPoolChangeParam param) {
        _log.debug("Schedule storage for vpool change to vpool {} for volume {}.", String.valueOf(vpool.getId()), String.valueOf(volume.getId()));
        CapacityMatcher capacityMatcher = new CapacityMatcher();
        capacityMatcher.setCoordinatorClient(this._coordinator);
        capacityMatcher.setObjectCache(new ObjectLocalCache(this._dbClient,false));
        List<StoragePool> matchedPoolsForVpool = VirtualPool.getValidStoragePools(vpool, _dbClient, true);
        List<StoragePool> sourcePools = new ArrayList<StoragePool>();
        
    	// Find out how much space we'll need to fit the Journals for the source
        // Bharath - is 1, right? for the number of volumes in this kind of request. check with Brad later.
    	long journalSize = RPHelper.getJournalSizeGivenPolicy(String.valueOf(volume.getCapacity()), vpool.getJournalSize(), 1);

        // Make sure our pool is in this list; this is a check to ensure the pool is in our existing varray and new CoS
        // and can fit the new sizes needed.  If it can not, send down pools that can.
        Iterator<StoragePool> iter = matchedPoolsForVpool.iterator();
        while (iter.hasNext()) {
            StoragePool pool = (StoragePool)iter.next();
            if (pool.getId().equals(volume.getPool())) {
            	// Make sure there's enough space for this journal volume in the current pool; it's preferred to use it.
                // TODO I know this ugly to pass null value and need to refactor.
                if (capacityMatcher.poolMatchesCapacity(pool, journalSize, journalSize, false, false, null)) {
                	sourcePools.add(pool);
                	break;
                } else {
                	_log.warn(String.format("Not enough capacity found to place RecoverPoint journal volume on pool: %s, searching for other less preferable pools.", pool.getNativeGuid()));
                	break;
                }
            }
        }

        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());
        if (!sourcePools.isEmpty()) {
            VirtualPoolCapabilityValuesWrapper wrapper = new VirtualPoolCapabilityValuesWrapper();
            wrapper.put(VirtualPoolCapabilityValuesWrapper.SIZE, journalSize);
        	// Fall-back: if the existing source pool couldn't be used, let's find a different pool.
        	sourcePools.addAll(_blockScheduler.getMatchingPools(varray, vpool, wrapper));

        	if (sourcePools.isEmpty()) {
            	// We could not verify the source pool exists in the CoS, return appropriate error
        		_log.error("No matching storage pools found for the source varray: {0}. There are no storage pools that " +
        				"match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to " +
        				"hold at least one resource of the requested size.", varray.getLabel());
        		throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getId(), varray.getId());        		
        	}
        }
        VirtualPoolCapabilityValuesWrapper wrapper = new VirtualPoolCapabilityValuesWrapper();
        wrapper.put(VirtualPoolCapabilityValuesWrapper.SIZE, volume.getCapacity());
        wrapper.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        wrapper.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, param.getConsistencyGroup());
        
        List<Recommendation> recommendations = buildCgRecommendations(wrapper, vpool, protectionVarrays, volume);
        
        if (recommendations.isEmpty()) {
            // Schedule storage based on the determined storage pools.
            recommendations = scheduleStorageSourcePoolConstraint(
    			_dbClient.queryObject(VirtualArray.class, volume.getVirtualArray()),
            protectionVarrays, vpool,
            wrapper, null, volume, null);
        }
        
    	return recommendations;
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
    private boolean fireProtectionPlacementRules(ProtectionSystem protectionSystem, RPProtectionRecommendation rec, Integer resourceCount) {
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
    		rec.setProtectionSystemCriteriaError(String.format(cgNoCapacityLog, protectionSystem));
    	}

    	// Only process the site statistics if the Protection System statistics 
    	// are adequate for protection.
    	StringMap siteVolumeCapacity = protectionSystem.getSiteVolumeCapacity();
    	StringMap siteVolumeCount = protectionSystem.getSiteVolumeCount();

    	if (siteVolumeCount != null && siteVolumeCount.size() > 0) {
    		String sourceSiteVolumeCount = siteVolumeCount.get(String.valueOf(rec.getSourceInternalSiteName()));
    		String sourceSiteVolumeCapacity = siteVolumeCapacity.get(String.valueOf(rec.getSourceInternalSiteName()));

    		if (sourceSiteVolumeCount == null) {
    			_log.warn(String.format(missingSiteMetric, "Source Site Volume Count", protectionSystem, rec.getResourceCount()));
    			sourceSiteVolumeCount = "-1";
    		}	

    		if (sourceSiteVolumeCapacity == null) {
    			_log.warn(String.format(missingSiteMetric, "Source Site Volume Capacity", protectionSystem, rec.getSourceInternalSiteName()));
    			sourceSiteVolumeCapacity = "-1";
    		}	

    		try {
    			// Get the source site available capacity.
    			long sourceSiteAvailableVolCapacity = 
    					Long.parseLong(sourceSiteVolumeCapacity) - Long.parseLong(sourceSiteVolumeCount);

    			_log.debug(String.format(sourceSiteVolumeCountLog, 
    					protectionSystem, rec.getSourceInternalSiteName(), sourceSiteVolumeCount, sourceSiteVolumeCapacity));

    			// If the source site available capacity is not adequate, log a message.
    			if (sourceSiteAvailableVolCapacity < rec.getNumberOfVolumes(rec.getSourceInternalSiteName())) {
    				isValid = false;
    				_log.info(String.format(sourceSiteVolumeNoCapacityLog, protectionSystem, rec.getSourceInternalSiteName(), resourceCount));
    				rec.setProtectionSystemCriteriaError(String.format(sourceSiteVolumeNoCapacityLog, protectionSystem, rec.getSourceInternalSiteName(), resourceCount));
    			}
    		} catch (NumberFormatException nfe) {
    			// Catch any exceptions that occur while parsing the site specific values
    			isValid = false;
    			_log.info(String.format(parseSiteStatsLog, protectionSystem, nfe.getMessage()));
    			rec.setProtectionSystemCriteriaError(String.format(parseSiteStatsLog, protectionSystem, nfe.getMessage()));
    		} 

    		for (URI protectionVarrayID : rec.getVirtualArrayProtectionMap().keySet()) {
    			Protection protection = rec.getVirtualArrayProtectionMap().get(protectionVarrayID);

    			String destSiteVolumeCount = siteVolumeCount.get(String.valueOf(protection.getTargetInternalSiteName()));
    			String destSiteVolumeCapacity = siteVolumeCapacity.get(String.valueOf(protection.getTargetInternalSiteName()));

    			if (destSiteVolumeCount == null) {
    				_log.warn(String.format(missingSiteMetric, "Destination Site Volume Count", protectionSystem, protection.getTargetInternalSiteName()));
    				destSiteVolumeCount = "-1";
    			}	

    			if (destSiteVolumeCapacity == null) {
    				_log.warn(String.format(missingSiteMetric, "Destination Site Volume Capacity", protectionSystem, protection.getTargetInternalSiteName()));
    				destSiteVolumeCapacity = "-1";
    			}	

    			try {
    				// Get the destination site available capacity.
    				long destSiteAvailableVolCapacity = 
    						Long.parseLong(destSiteVolumeCapacity) - Long.parseLong(destSiteVolumeCount);

    				_log.debug(String.format(destSiteVolumeCountLog, 
    						protectionSystem, protection.getTargetInternalSiteName(), destSiteVolumeCount, destSiteVolumeCapacity));

    				// If the destination site available capacity is not adequate, log a message.
    				if (destSiteAvailableVolCapacity < rec.getNumberOfVolumes(protection.getTargetInternalSiteName())) {
    					isValid = false;
    					_log.info(String.format(destSiteVolumeNoCapacityLog, protectionSystem, protection.getTargetInternalSiteName(), rec.getResourceCount()));
    					rec.setProtectionSystemCriteriaError(String.format(destSiteVolumeNoCapacityLog, protectionSystem, protection.getTargetInternalSiteName(), rec.getResourceCount()));
    				}
    			} catch (NumberFormatException nfe) {
    				// Catch any exceptions that occur while parsing the site specific values
    				isValid = false;
    				_log.info(String.format(parseSiteStatsLog, protectionSystem, nfe.getMessage()));
    				rec.setProtectionSystemCriteriaError(String.format(parseSiteStatsLog, protectionSystem, nfe.getMessage()));
    			} 
    		} 
    	} else {
    		// There are no site volume statistics available so assume volume
    		// protection cannot be achieved.
    		isValid = false;
    		_log.warn(String.format(missingProtectionSystemMetric, "Site Volume Capacity/Count", protectionSystem));
    		rec.setProtectionSystemCriteriaError(String.format(missingProtectionSystemMetric, "Site Volume Capacity/Count", protectionSystem));
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
    	for (URI protectionSystemId : _dbClient.queryByType(ProtectionSystem.class, true)) {
    		ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, protectionSystemId);
    		StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId); 
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
        		_dbClient.queryObject(ProtectionSystem.class, protectionSystemURI);
        StringSet siteInitiators = 
        		protectionSystem.getSiteInitiators().get(siteId);
        
        boolean connected = false;
        
        for (String wwn : siteInitiators) {
        	NetworkLite network = NetworkUtil.getEndpointNetworkLite(wwn, _dbClient);
        	// The network is connected if it is assigned or implicitly connected to the varray
        	if (_rpHelper.isNetworkConnectedToVarray(network, virtualArray)) {
        		connected = true;
        		break;
            }
        }
        
        // Check to make sure the RP site is connected to the varray
        return (connected && _rpHelper.rpInitiatorsInStorageConnectedNework(
                        storageSystemURI, protectionSystemURI, siteId, virtualArray.getId()));
	}
	
	/**
     * Sorts the list of candidate storage pools.
     * 
     * @param candidatePools
     * @param capabilities
     */
    protected void sortCandidatePools(List<StoragePool> candidatePools,
    		VirtualPoolCapabilityValuesWrapper capabilities) {
        if (capabilities.getResourceCount() == 1) {
            // For single resource request, select storage pool randomly from all candidate pools (to minimize collisions).
            Collections.shuffle(candidatePools);
        } else {
            /**
             * Sort all pools in ascending order of its storage system's average port usage metrics (first order), 
             * descending  order by free capacity (second order) and  in ascending order by ratio
             * of pool's subscribed capacity to total capacity(suborder).
             * This order is kept through the selection procedure.
             */
            _blockScheduler.sortPools(candidatePools);
        }
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
			} else if ((recommendation.getPlacementStepsCompleted().ordinal() >= latestInvalidRecommendation.getPlacementStepsCompleted().ordinal()) &&
					recommendation.getVirtualArrayProtectionMap().size() >= latestInvalidRecommendation.getVirtualArrayProtectionMap().size()) {
						return true;
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
					StoragePool jpool = (StoragePool)dbClient.queryObject(StoragePool.class, this.latestInvalidRecommendation.getSourceJournalStoragePool());
					StorageSystem system = (StorageSystem)dbClient.queryObject(StorageSystem.class, this.latestInvalidRecommendation.getSourceDevice());
					ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, this.latestInvalidRecommendation.getProtectionDevice());
					String sourceInternalSiteName = this.latestInvalidRecommendation.getSourceInternalSiteName();
					String sourceRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(sourceInternalSiteName) : sourceInternalSiteName; 
					buff.append("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");				   
					buff.append("\tSource Virtual Array: " + this.srcVArray + "\n");
					buff.append("\tSource Virtual Pool: " + this.srcVPool + "\n");
					buff.append("\tProtection System: " + ps.getLabel() + "\n");
					buff.append("\tSource RP Site: " + sourceRPSiteName + "\n");
					buff.append("\tSource Storage System: " + system.getLabel() + "\n");
					buff.append("\tSource Storage Pool: " + pool.getLabel() + "\n");
					buff.append("\tSource Journal Storage Pool: " + jpool.getLabel() + "\n");
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
					if (this.latestInvalidRecommendation.getVirtualArrayProtectionMap().get(varrayID) != null) {					   
						StoragePool targetPool = (StoragePool)dbClient.queryObject(StoragePool.class, this.latestInvalidRecommendation.getVirtualArrayProtectionMap().get(varrayID).getTargetStoragePool());
						StoragePool targetjPool = (StoragePool)dbClient.queryObject(StoragePool.class, this.latestInvalidRecommendation.getVirtualArrayProtectionMap().get(varrayID).getTargetJournalStoragePool());
						StorageSystem targetSystem = (StorageSystem)dbClient.queryObject(StorageSystem.class, this.latestInvalidRecommendation.getVirtualArrayProtectionMap().get(varrayID).getTargetDevice());
						ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, this.latestInvalidRecommendation.getProtectionDevice());
						String targetInternalSiteName = this.latestInvalidRecommendation.getVirtualArrayProtectionMap().get(varrayID).getTargetInternalSiteName();
						String targetRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(targetInternalSiteName) : targetInternalSiteName; 
						buff.append("\tProtection to Virtual Array: " + varray.getLabel() + "\n");
						buff.append("\tProtection to RP Site: " + targetRPSiteName + "\n");
						buff.append("\tProtection to Storage System: " + targetSystem.getLabel() + "\n");
						buff.append("\tProtection to Storage Pool: " + targetPool.getLabel() + "\n");
						buff.append("\tProtection Journal Storage Pool: " + targetjPool.getLabel() + "\n");					   
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
			return buff.toString();
		}			   			   
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
}
