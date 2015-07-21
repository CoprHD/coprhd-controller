/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

public class HDSShadowImagePairCountMatcher extends AttributeMatcher {

	private static final Logger logger = LoggerFactory.getLogger(HDSShadowImagePairCountMatcher.class);
	
	@Override
	protected boolean isAttributeOn(Map<String, Object> attributeMap) {
		return (null != attributeMap
                && (attributeMap.containsKey(Attributes.max_native_continuous_copies.name())
                		|| attributeMap.containsKey(Attributes.max_native_snapshots.name())));
	}

	@Override
	protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
		int mirrorCount = 0;
		if (attributeMap.containsKey(Attributes.max_native_continuous_copies.name())) {
			mirrorCount = (int) attributeMap.get(Attributes.max_native_continuous_copies.name());
		}
		int snapshotCount = 0;
		if (attributeMap.containsKey(Attributes.max_native_snapshots.name())) {
			snapshotCount = (int) attributeMap.get(Attributes.max_native_snapshots.name());
		}
		
		logger.info("Pools Matching Maximum Snapshot/Mirror Count for HDS Started. Snapshot : {} Mirror : {}",
				snapshotCount, mirrorCount);
		logger.info(Joiner.on("\t").join(getNativeGuidFromPools(pools)));
		Map<URI, StorageSystem> storageSystemMap = new HashMap<URI, StorageSystem>();
		List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(pools);
		Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
        	StoragePool pool = poolIterator.next();
        	StorageSystem system = getStorageSystem(storageSystemMap, pool);
        	if (DiscoveredDataObject.Type.hds.name().equalsIgnoreCase(system.getSystemType()) 
        			&& (mirrorCount > HDSConstants.MAX_SHADOWIMAGE_PAIR_COUNT || 
        					snapshotCount > HDSConstants.MAX_SNAPSHOT_COUNT)) {
        		logger.info("Ignoring pool {} since mirror/snapshot count exceeded the limit supported by HDS", pool.getNativeGuid());
        		filteredPoolList.remove(pool);
        	}
        }
        logger.info("Pools Matching Maximum Snapshot/Mirror Count for HDS Ended. Snapshot : {} Mirror : {}",
				snapshotCount, mirrorCount);
		logger.info(Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
		return filteredPoolList;
	}
	
}
