/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * Block virtual pools can support deduplication. 
 * This filter will get only those storage pools which support dedupliation. Such storage pools are marked during discovery.
 */

public class DedupMatcher extends AttributeMatcher {
	private static final Logger _logger = LoggerFactory.getLogger(DedupMatcher.class);
	
	@Override
	protected boolean isAttributeOn(Map<String, Object> attributeMap) {
		if (attributeMap != null
				&& attributeMap.containsKey(Attributes.dedup.toString())) {
			return true;
		}
		return false;
	}

	@Override
	protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> allPools,
			Map<String, Object> attributeMap, StringBuffer errorMessage) {
		Boolean dedupCapable = (Boolean) attributeMap.get(Attributes.dedup.toString());
		
		_logger.info("Pools matching Deduplication capablity Started : {}, {}", dedupCapable,
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(allPools);
        
        if (dedupCapable) {
        	Iterator<StoragePool> poolIterator = allPools.iterator();
        	while (poolIterator.hasNext()) {
        		StoragePool pool = poolIterator.next();
        		if (!pool.getDedupCapable()) {
        			_logger.info("Ignoring pool {} as dedup is not supported", pool.getNativeGuid());
        			filteredPoolList.remove(pool);
        		}
        	}
        	
            if (CollectionUtils.isEmpty(filteredPoolList)) {
                errorMessage.append(String.format("No matching block storage pool found supporting Dedup capability"));
                _logger.error(errorMessage.toString());
            }
        }
        
		_logger.info("Pools matching Deduplication capablity Ended : {}, {}", dedupCapable,
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        return filteredPoolList;
	}
}
