package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

public class MinDataCenterMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory.getLogger(MinDataCenterMatcher.class);
    
	@Override
	protected boolean isAttributeOn(Map<String, Object> attributeMap) {
		if (attributeMap != null
				&& attributeMap.containsKey(Attributes.min_data_center.toString())) {
			return true;
		}
		return false;
	}

	@Override
	protected List<StoragePool> matchStoragePoolsWithAttributeOn(
			List<StoragePool> allPools, Map<String, Object> attributeMap) {
		Integer minDataCenters = (Integer) attributeMap.get(Attributes.min_data_center.toString());
        _logger.info("Pools Matching Minimum Data Centers Started {}, {} :", minDataCenters,
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        Iterator<StoragePool> poolIterator = allPools.iterator();
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(allPools);
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (!(pool.getDataCenters() >= minDataCenters)) {
            	_logger.info("Ignoring pool {} as Data Centers is less :", pool.getNativeGuid());
            	allPools.remove(pool);
            }
        }
        _logger.info("Pools Matching Minimum Data Centers Started {}, {}", minDataCenters,
                Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;
	}

}
