/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedReplicationTypes;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * ActivePoolMatcher is responsible to check pool activeness, ready state
 * and its registration status.
 * 
 */
public class SRDFMetroMatcher extends AttributeMatcher {

    private static final Logger _logger = LoggerFactory
            .getLogger(SRDFMetroMatcher.class);

    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
        return filterPoolsForSRDFActiveMode(pools);
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        Map<String, List<String>> remoteCopySettings = (Map<String, List<String>>)
                attributeMap.get(Attributes.remote_copy.toString());
        Set<String> copyModes = getSupportedCopyModesFromGivenRemoteSettings(remoteCopySettings);
        return (null != copyModes && copyModes.contains(SupportedCopyModes.ACTIVE.toString()));            
    }

    private Set<String> getSupportedCopyModesFromGivenRemoteSettings(Map<String, List<String>> remoteCopySettings) {
        Set<String> copyModes = new HashSet<String>();
        if (null != remoteCopySettings) {
            for (Entry<String, List<String>> entry : remoteCopySettings.entrySet()) {
                copyModes.addAll(entry.getValue());
            }
        }
        return copyModes;
    }

    public List<StoragePool> filterPoolsForSRDFActiveMode(List<StoragePool> pools) {
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        Map<URI, StorageSystem> storageSystemMap = new HashMap<URI, StorageSystem>();
        _logger.info("SRDF Metro Pools Matcher Started : {}", Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            StorageSystem storageSystem = null;
            if (null == pool) {
                continue;
            } else {
                if (storageSystemMap.get(pool.getStorageDevice()) == null) {
                    storageSystem = _objectCache.queryObject(StorageSystem.class, pool.getStorageDevice());
                    storageSystemMap.put(pool.getStorageDevice(), storageSystem);
                }
                storageSystem = storageSystemMap.get(pool.getStorageDevice());
            }

            if (null != storageSystem && null != storageSystem.getSupportedReplicationTypes()
                    && storageSystem.getSupportedReplicationTypes().contains(SupportedReplicationTypes.SRDFMetro.toString())) {
                matchedPools.add(pool);
            }
        }
        _logger.info("SRDF Metro Pools Matcher Ended : {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;
    }
}
