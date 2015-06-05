/**
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.AttributeMatcher;

/**
 * StorageSystemMatcher is responsible to match the storage systems of Storage
 * Pools.
 * 
 */
public class StorageSystemMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory
            .getLogger(StorageSystemMatcher.class);

    /**
     * Check if the CoS and pools are in the same varray and filter out the pools
     * if they are not in the same varray.
     * 

     * @param pools
     *            matches neighborhoods of all pools with vpool neighborhoods.
     * @param compareWithObj : vpool
     * @param isMatcherValid : flag to identify whether to run this matcher or not.
     * 

     * @return list of pools matching if both pool & vpool are part of same varray.
     */
    @Override
	@SuppressWarnings("unchecked")
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap){
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        
		Set<String> systems = (Set<String>) attributeMap.get(Attributes.storage_system
		        .toString());
        
        Iterator<StoragePool> poolIterator = pools.iterator();
        while(poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
			if (systems.contains(pool.getStorageDevice().toString())) {
                matchedPools.add(pool);
            }
        }
		_logger.info("{} pools are matching with systems after matching.",
                matchedPools.size());
        return matchedPools;
    }
    
    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
		return (null != attributeMap && attributeMap.containsKey(Attributes.storage_system
		        .toString()));
    }
    
    @Override
    protected  List<StoragePool> matchStoragePoolsWithAttributeOff(List<StoragePool> pools, Map<String, Object> attributeMap) {
        
        // If multiVolumeConsistency is null or false, return the list of pools
        final Boolean multiVolumeConsistency = (Boolean) attributeMap.get(Attributes.multi_volume_consistency.name());
        if(pools == null || pools.isEmpty() || multiVolumeConsistency == null || !multiVolumeConsistency ){
               return pools;
        }
        
        // If multiVolumeConsistency is TRUE then all the matching pools should be in the same StorageSystem so that all the Volumes are created in the same array
        final List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        final URI bestMatchingStorageDevice = getBestMatchingStorageSystem(pools);
        
        for (StoragePool pool : pools) {
                      if(pool.getStorageDevice().equals(bestMatchingStorageDevice)){
                            matchedPools.add(pool);
                      }
               }
        
        _logger.info("{} pools are matching with systems after matching with attribute off.",
                 matchedPools.size());
        
        return pools;
     }

     private URI getBestMatchingStorageSystem(List<StoragePool> pools) {
               
        final Map<URI, Long> storageSystemFreeCapacityMap = new HashMap<URI, Long>();
        
        // Build the free capacity map
        for (StoragePool pool : pools) {
                      final URI storageSystemId = pool.getStorageDevice();
               Long freeCapacity = pool.getFreeCapacity();
                      
                      if(storageSystemFreeCapacityMap.containsKey(storageSystemId)){
                            freeCapacity += storageSystemFreeCapacityMap.get(storageSystemId);
                      }
                      
                      storageSystemFreeCapacityMap.put(storageSystemId, freeCapacity);
               }
        
        // Return the Storage System with highest free capacity
        final Iterator<Entry<URI, Long>> mapIterator = storageSystemFreeCapacityMap.entrySet().iterator();
        Entry<URI, Long> bestMatchingStorageDeviceEntry = mapIterator.next();
        while(mapIterator.hasNext()){
               final Entry<URI, Long> candidateStorageDeviceEntry = mapIterator.next();
               if(candidateStorageDeviceEntry.getValue() > bestMatchingStorageDeviceEntry.getValue()){
                      bestMatchingStorageDeviceEntry = candidateStorageDeviceEntry;
               }
        }
        
        return bestMatchingStorageDeviceEntry.getKey();
    }

}
