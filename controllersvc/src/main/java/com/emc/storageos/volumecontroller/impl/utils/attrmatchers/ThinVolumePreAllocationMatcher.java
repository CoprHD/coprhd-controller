/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;
/**
 * Matcher to filter out the pools based on the thin_volume_preallocation_percentage.
 * Run the matcher only if thin_volume_preallocation_percentage is set.
 */
public class ThinVolumePreAllocationMatcher extends AttributeMatcher{

    private static final Logger _logger = LoggerFactory
    .getLogger(ThinVolumePreAllocationMatcher.class);
  

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return (null != attributeMap
                && attributeMap.containsKey(Attributes.thin_volume_preallocation_percentage.toString()));
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
        Integer thinVolumePreAllocationPercentage = (Integer) attributeMap.get(Attributes.thin_volume_preallocation_percentage
                .toString());
        _logger.info("Pools Matching ThinVolumePreAllocationPercentage Started {}, {} :", thinVolumePreAllocationPercentage,
                Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(pools);
        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (!pool.getThinVolumePreAllocationSupported()) {
                if (!attributeMap.get(Attributes.vpool_type.toString()).equals(VirtualPool.Type.file)) {
                	_logger.info("Ignoring pool {} as it does not support thin Resource Preallocation.", pool.getNativeGuid());
                	filteredPoolList.remove(pool);
                }
            } else {
                StorageSystem storageDevice = _objectCache.getDbClient().queryObject(StorageSystem.class, pool.getStorageDevice());
                if(storageDevice.checkIfVmax3() && thinVolumePreAllocationPercentage != Constants.VMAX3_FULLY_ALLOCATED_VOLUME_PERCENTAGE){
                    _logger.info("Ignoring pool {} as it belongs to VMAX3 storage system and to qualify this pool, "
                            + "Virtual pool should have Thin Volume preallocation of {} but its set to {}. ",
                            new Object[]{pool.getNativeGuid(), Constants.VMAX3_FULLY_ALLOCATED_VOLUME_PERCENTAGE, thinVolumePreAllocationPercentage});
                    filteredPoolList.remove(pool);
                }
            }
        }
        _logger.info("Pools Matching ThinVolumePreAllocationPercentage Ended {}, {}", thinVolumePreAllocationPercentage, Joiner
                .on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;
    }
}
