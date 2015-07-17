/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2013. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMapBuilder;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolPreCreateParamAttributeMapBuilder;
import com.google.common.base.Joiner;

/**
 * VPlexHighAvailabilityMatcher ensures a storage pool is on a storage system
 * that is associated with a VPlex storage storage system
 */
public class VPlexHighAvailabilityMatcher extends AttributeMatcher {
    
    // Logger reference.
    private static final Logger _logger = LoggerFactory
        .getLogger(VPlexHighAvailabilityMatcher.class);
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> allPools, Map<String, Object> attributeMap) {
        _logger.info("Pools Matching ha attribute Started:{}", Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        
        // Get the varrays to match.
        @SuppressWarnings("unchecked")
        Set<String> matchVarrays = (Set<String>) attributeMap.get(Attributes.varrays.toString());
                    	        
        // Get the HA type.
        String haType = (String) attributeMap.get(Attributes.high_availability_type
            .toString());

        // Get HA varray Id.
        String haVarrayId = (String) attributeMap.get(Attributes.high_availability_varray
            .toString());
        if (haVarrayId != null) {
            URI haVarrayURI = URI.create(haVarrayId);
            if (NullColumnValueGetter.isNullURI(haVarrayURI)) {
                haVarrayId = null;
            }
        }

        // Get HA Virtual Pool
        VirtualPool haVpool = null;
        List<StoragePool> haVpoolPoolList = null;
        String haVpoolId = (String) attributeMap.get(Attributes.high_availability_vpool
            .toString());
        if (haVpoolId != null) {
            URI haCosURI = URI.create(haVpoolId);
            if (NullColumnValueGetter.isNullURI(haCosURI)) {
                haVpoolId = null;
            } else {
                haVpool = _objectCache.queryObject(VirtualPool.class, URI.create(haVpoolId));
                haVpoolPoolList = VirtualPool.getValidStoragePools(haVpool, _objectCache.getDbClient(), true);
            }
        }

        // Iterate over the pools and add those to the matched list that match.
        Map<URI, List<String>> systemVPlexMap = new HashMap<URI, List<String>>();
        Map<String, List<URI>> vplexVarrayMap = new HashMap<String, List<URI>>(); 
        Iterator<StoragePool> allPoolsIter = allPools.iterator();
        while (allPoolsIter.hasNext()) {
            StoragePool storagePool = allPoolsIter.next();

            List<String> vplexSystemsForPool = null;
            URI poolSystemURI = storagePool.getStorageDevice();
            if (systemVPlexMap.containsKey(poolSystemURI)) {
                vplexSystemsForPool = systemVPlexMap.get(poolSystemURI);
            } else {
                vplexSystemsForPool = getVPlexStorageSystemsForStorageSystem( _objectCache.getDbClient(),
                    poolSystemURI, matchVarrays);
                systemVPlexMap.put(poolSystemURI, vplexSystemsForPool);
            }

            // Only pools connected to a VPlex system potentially match.
            if (vplexSystemsForPool.isEmpty()) {
                continue;
            }

            // For local HA, the pool must only be connected to a VPlex.
            if (VirtualPool.HighAvailabilityType.vplex_local.toString().equals(haType)) {
                matchedPools.add(storagePool);
                continue;
            }
            
            // Otherwise, must be distributed. Loop over the
            // VPlex systems for the pool to make sure one of
            // the systems satisfies the distributed HA CoS
            // attributes.
            for (String vplexSystemId : vplexSystemsForPool) {
               
                List<URI> vplexVarrays = null;
                
                if (vplexVarrayMap.containsKey(vplexSystemId)) {
                    vplexVarrays = vplexVarrayMap.get(vplexSystemId);
                } else {
                    vplexVarrays = ConnectivityUtil
                        .getVPlexSystemVarrays(_objectCache.getDbClient(), URI.create(vplexSystemId));
                    vplexVarrayMap.put(vplexSystemId, vplexVarrays);
                }
                
                // If there are not multiple varrays for the VPlex,
                // it cannot support distributed HA. Move on to the
                // next VPlex.
                if (vplexVarrays.size() < 2) {
                    continue;
                }
                
                // There are multiple varrays for the VPlex
                // so we know it can support distributed HA.
                if ((haVarrayId == null) && (haVpoolId == null)) {
                    // No specific HA varray or CoS was specified, the
                    // pool matches.
                    matchedPools.add(storagePool);
                    break;
                } else if (haVpoolId == null) {
                    // Only an HA varray was specified. If the
                    // VPlex is connected to the HA varray, the
                    // pool is a match.
                    if (vplexVarrays.contains(URI.create(haVarrayId))) {
                        matchedPools.add(storagePool);
                        break;
                    }
                } else if (haVarrayId == null) {
                    // Only an HA vpool was specified. There must be 
                    // a pool in the VPlex that satisfies the HA vpool.
                    boolean poolAdded = false;
                    for (URI varrayURI : vplexVarrays) {
                        if (varrayHasPoolMatchingHaVpool(varrayURI.toString(),
                            haVpoolPoolList)) {
                            matchedPools.add(storagePool);
                            poolAdded = true;
                            break;
                        }
                    }
                    if (poolAdded) {
                        break;
                    }
                } else if ((vplexVarrays.contains(URI.create(haVarrayId))) &&
                           (varrayHasPoolMatchingHaVpool(haVarrayId, haVpoolPoolList))) {
                    // Both and HA varray and vpool are specified.
                    // The VPlex must be connected to the HA varray
                    // and have a pool matching the HA vpool.
                    matchedPools.add(storagePool);
                    break;
                }
            }
        }       
        
        // RP+VPLEX
        if (haVarrayId != null) {
	        boolean validStoragePoolMatches = performRPVplexAttributeMatching(attributeMap, haVarrayId, haVpool);
	        if (!validStoragePoolMatches) {
	        	//TODO BH - For now return an empty list. Exception would be better I think.
	        	_logger.error("There were no pools found for protection macthes for VPlex HA leg, not a valid Virtual Pool configuration. Returning empty pool matches.");
	        	matchedPools = new ArrayList<StoragePool>();
	        }
        }
        
        _logger.info("Pools Matching ha attribute Matcher Ended:{}",
                Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        
        return matchedPools;
    }

    /**
     * Gets a list of the VPlex storage systems for the passed system in the
     * passed virtual arrays or any virtual array if none are passed.
     * 
     * @param dbClient Reference to a DB client.
     * @param systemURI Reference to a storage system.
     * @param matchVarrays the virtual arrays to match or null.
     * 
     * @return A list of the VPlex storage system ids.
     */
    public static List<String> getVPlexStorageSystemsForStorageSystem(DbClient dbClient,
        URI systemURI, Set<String> matchVarrays) {
        List<String> vplexStorageSystemIds = new ArrayList<String>();
        Set<URI> vplexSystemURIs = ConnectivityUtil
                .getVPlexSystemsAssociatedWithArray(dbClient, systemURI, matchVarrays, null);
        for (URI uri : vplexSystemURIs) {
            vplexStorageSystemIds.add(uri.toString());
        }
        return vplexStorageSystemIds;
    }
    
    /**
     * Determine if the varray with the passed id has a storage pool that
     * satisfies the HA CoS.
     * 
     * @param nhId The id of a varray.
     * @param haCosPoolList The pool list for the HA CoS.
     * 
     * @return true if the varray has a storage pool that matches the HA
     *         CoS, false otherwise.
     */
    private boolean varrayHasPoolMatchingHaVpool(String nhId,
        List<StoragePool> haCosPoolList) {

        for (StoragePool haCosPool : haCosPoolList) {
            StringSet poolNHs = haCosPool.getTaggedVirtualArrays();
            if (poolNHs != null && poolNHs.contains(nhId)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return (null != attributeMap && (VirtualPool.HighAvailabilityType.vplex_distributed
            .toString().equals(
                attributeMap.get(Attributes.high_availability_type.toString())) || VirtualPool.HighAvailabilityType.vplex_local
            .toString().equals(
                attributeMap.get(Attributes.high_availability_type.toString()))));

    }    
    
    /**
     * If the 'HA as RP Source' flag is set or MetroPoint is enabled, perform a separate matching
     * operation to see if the HA Varray has valid Protection Storage Pools.  The protection information 
     * supplied on the attribute map is used to determine this. 
     *   
     * @param attributeMap
     * @param haVarrayId
     * @param haVpool
     * @return true if there is at least 1 matching pool
     */
    private boolean performRPVplexAttributeMatching(Map<String, Object> attributeMap, String haVarrayId, VirtualPool haVpool) {
    	// If we have MetroPoint enabled and/or we have HA as RP source specified, we want to ensure the
    	// HA virtual array has valid RP protected storage pools.
    	
    	_logger.info("HA Virtual Array/RecoverPoint: Pool matching started...");

    	// Assume true unless proven otherwise
    	boolean validMatchingPools = true;
    	
    	// Check if HA as RP source has been selected
    	String haRP = (String) attributeMap.get(Attributes.high_availability_rp.toString());
    	
        // Check if VPlex MetroPoint has been enabled.
        Boolean metroPointEnabled = (Boolean) attributeMap.get(Attributes.metropoint.toString());
        
        if (metroPointEnabled != null && metroPointEnabled) {
            // Get the HA type.
            String haType = (String) attributeMap.get(Attributes.high_availability_type
                .toString());
        	
        	// If MetroPoint is enabled, we need to verify RP connectivity of the HA varray/vpool  
        	if (VirtualPool.HighAvailabilityType.vplex_distributed.toString().equals(haType)) {
        		_logger.info("MetroPoint: Verifying that HA Varray [{}] has RP protected pools.", haVarrayId );
        		validMatchingPools = matchHAVarrayRecoverPoint(attributeMap, haVarrayId, haVpool);
        	} else {
        		// VPlex distributed must be selected if MetroPoint is selected.  If this is not the case,
        		// we do not match any pools.
        		_logger.info("MetroPoint has been selected but VPlex distributed has not. Therefore no pools can be matched.");
        		validMatchingPools = false;
        	}
        } else if (NullColumnValueGetter.isNotNullValue(haRP)) {
        	_logger.info("HA as RP Source: HA Varray [{}] has been set to be used as RP Source.", haVarrayId );
        	// HA as RP source has been selected so we need to verify that there are RP protected
        	// pools on the HA side.
        	validMatchingPools = matchHAVarrayRecoverPoint(attributeMap, haVarrayId, haVpool);
    	}
        	        
        return validMatchingPools;
    }
    
    /**
     * Perform a matching operation to see if the HA vArray/vPool has valid protection Storage Pools 
     * using the protection information supplied on the attribute map. 
     * 
     * @param attributeMap
     * @param haVarrayId
     * @param haVpool
     * @return true if there is at least 1 matching pool
     */
    private boolean matchHAVarrayRecoverPoint(Map<String, Object> attributeMap, String haVarrayId, VirtualPool haVpool) {
    	boolean validMatchingPools = true;
    	
    	StringMap rpMap = (StringMap) attributeMap.get(Attributes.recoverpoint_map.name());
        
        // Ensure RecoverPoint protection has been specified before continuing.
        if (rpMap != null && !rpMap.isEmpty()) {
        	_logger.info("Checking for matching pools: RP protection on HA Varray [{}].", haVarrayId );
        	List<StoragePool> matchingPools = new ArrayList<StoragePool>();        	
        	Map<String, Object> haRpSourceAttributeMap = new HashMap<String, Object>();
        	
        	// We only want to consider the HA Varray
        	Set<String> varrays = new HashSet<String>();
    		varrays.add(haVarrayId);
    		
        	AttributeMapBuilder attrBuilder = new VirtualPoolPreCreateParamAttributeMapBuilder(
				null, null, null, null, null, null, null,
	            varrays,
	            null, null, null, null, null,
	            VirtualPool.Type.block.toString(),
	            null, null, null, 
	            rpMap,
	            null, null, null, null, false);
	
	        haRpSourceAttributeMap = attrBuilder.buildMap();
	        
	        List<URI> allStoragePoolURIs = new ArrayList<URI>();
	        
	        if (haVpool != null) {
	        	// Use the already assigned Storage Pools for consideration from the existing HA Vpool if 
	        	// it has been specified.
	        	if (haVpool.getUseMatchedPools()) {
	        		for (String storagePoolId : haVpool.getMatchedStoragePools()) {
		        		allStoragePoolURIs.add(URI.create(storagePoolId));
		        	}
	        	}
	        	else {	        	
		        	for (String storagePoolId : haVpool.getAssignedStoragePools()) {
		        		allStoragePoolURIs.add(URI.create(storagePoolId));
		        	}
	        	}
	        }
	        else {
	        	// Load all the StoragePools unfortunately.
	        	allStoragePoolURIs = _objectCache.getDbClient().queryByType(StoragePool.class, true);
	        }
	        
	        List<StoragePool> allStoragePools = _objectCache.queryObject(StoragePool.class, allStoragePoolURIs);
 	        
	        _logger.info("Starting new framework matcher, checking RP protection against VPlex HA leg...");
        	matchingPools = new AttributeMatcherFramework().matchAttributes(allStoragePools, haRpSourceAttributeMap, _objectCache.getDbClient(), _coordinator,
                    AttributeMatcher.VPOOL_MATCHERS);
        	        	        	
        	if (matchingPools != null && !matchingPools.isEmpty()) {        		        		        		        		
        		_logger.info("Pools matching RP protection on HA Varray: {}",
                        Joiner.on("\t").join(getNativeGuidFromPools(matchingPools)));        		
        	}
        	else {
        		_logger.warn("There were no pools found for protection matches HA Varray.");
        		// If there are no protection matches between the HA Varray and Vpool, we can't use the HA Varray
            	// as the RP Source. So we can't
        		validMatchingPools = false;        		        		
        	}        	        	
    	} else {
        	_logger.info("Pool matching for MetroPoint or 'HA as RP Source' on HA Varray is not enabled OR no protection specified.");        	
        }
        
        _logger.info("RP protection pool matching on VPlex HA leg has ended.");
        	        
        return validMatchingPools;
    }
}
