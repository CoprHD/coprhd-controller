/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * Responsible to match the storage pools that known to a host/clusters.
 * 
 */
public class HostArrayAffinityPolicyMatcher extends AttributeMatcher {

    private static final Logger _logger = LoggerFactory
            .getLogger(HostArrayAffinityPolicyMatcher.class);

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        if (attributeMap != null && attributeMap.get(Attributes.host_array_affinity.name()) != null) {
            Boolean hostArrayAffinity = (Boolean) attributeMap.get(Attributes.host_array_affinity.name());
            return (hostArrayAffinity != null && hostArrayAffinity);
        }

        return false;
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(
            List<StoragePool> pools, Map<String, Object> attributeMap) {
        _logger.info("Matching array affinity started:" + Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        List<StoragePool> matchedPools = new ArrayList<StoragePool>(pools);

        if (attributeMap != null && attributeMap.get(Attributes.affilated_arrays_pools.name()) != null) {
            Map<URI, Set<URI>> affilatedArraysAndPools = (Map<URI, Set<URI>>) attributeMap.get(Attributes.affilated_arrays_pools.name());
            Set<URI> affilatedArrays = affilatedArraysAndPools.keySet();

            if (!affilatedArrays.isEmpty()) {
                Iterator<StoragePool> poolIterator = matchedPools.iterator();
                while (poolIterator.hasNext()) {
                    StoragePool pool = poolIterator.next();
                    if (!affilatedArrays.contains(pool.getStorageDevice())) {
                        poolIterator.remove();
                    }
                }
            }
        }

        _logger.info("Matching array affinity ended:" + Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        return matchedPools;
    }
}
