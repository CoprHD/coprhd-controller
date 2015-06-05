/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

public class CompatiblePoolMatcher extends AttributeMatcher {

	private static final Logger _logger = LoggerFactory.getLogger(CompatiblePoolMatcher.class);
	
	@Override
	protected boolean isAttributeOn(Map<String, Object> attributeMap) {
		// Since this is a defaultMatcher, it should always return true.
        return true;
	}

	@Override
	protected List<StoragePool> matchStoragePoolsWithAttributeOn(
			List<StoragePool> allPools, Map<String, Object> attributeMap) {
		List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        //Filter out incompatible pools.
        _logger.info("Compatible Pools Matcher Started : {}", Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        Iterator<StoragePool> poolIterator = allPools.iterator();
        while(poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (null == pool) {
                continue;
            }else if (!CompatibilityStatus.INCOMPATIBLE.name().equalsIgnoreCase(pool.getCompatibilityStatus())) {
                matchedPools.add(pool);
            }
            
        }
        _logger.info("Compatible Pools Matcher Ended : {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;
	}

}
