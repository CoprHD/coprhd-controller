/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

import static com.emc.storageos.db.client.model.StorageSystem.AsyncActions.CreateGroupReplica;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnStoragePoolToStorageSystemURI;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;

/**
 * StorageSystemMatcher is responsible to match the storage systems of Storage
 * Pools.
 * 
 */
public class MultiVolumeConsistencyMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory
            .getLogger(MultiVolumeConsistencyMatcher.class);

    /**
     * Check whether the given StoragePool supports ConsistencyGroups
     */
    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
        final List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        final Iterator<StoragePool> poolIterator = pools.iterator();
        Map<URI, StorageSystem> storageSystems = getStorageSystems(pools);
        _logger.info("Pools Matching MultiVolumeConsisistency attribute Started:{}",
                Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        while (poolIterator.hasNext()) {
            final StoragePool pool = poolIterator.next();
            final StorageSystem system = storageSystems.get(pool.getStorageDevice());

            // Add pool if any of the check* methods return true
            if (checkAsynchronousActions(system) || checkStorageSystemType(system)) {
                matchedPools.add(pool);
            } else {
                _logger.info("Ignoring pool {} as it does not support Consistency Groups", pool.getNativeGuid());
                continue;
            }
        }
        _logger.info("Pool Matching MultiVolumeConsistency Matcher Ended:{}",
                Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));

        return matchedPools;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        final Boolean multiVolumeConsistency = (Boolean) attributeMap.get(Attributes.multi_volume_consistency.name());
        return (null != attributeMap && multiVolumeConsistency != null && multiVolumeConsistency);
    }

    private Map<URI, StorageSystem> getStorageSystems(List<StoragePool> pools) {
        // Convert list of StoragePool to list of associated StorageSystem URI's
        Collection<URI> systemURIs = transform(pools, fctnStoragePoolToStorageSystemURI());
        // Remove duplicates using a Set
        ImmutableSet<URI> uniqueURIs = ImmutableSet.copyOf(systemURIs);
        // Query all systems in one call (need to convert back to a List)
        List<StorageSystem> storageSystems = _objectCache.queryObject(StorageSystem.class, newArrayList(uniqueURIs));

        // Finally, create a map of StorageSystemURI -> StorageSystem
        Map<URI, StorageSystem> result = new HashMap<>();
        for (StorageSystem system : storageSystems) {
            result.put(system.getId(), system);
        }
        return result;
    }

    private boolean checkAsynchronousActions(StorageSystem system) {
        StringSet asyncSet = system.getSupportedAsynchronousActions();
        // Only add pools that support ConsistencyGroups (i.e. CreateGroupReplica is present within asynchronous actions)
        return asyncSet != null && !asyncSet.isEmpty() && asyncSet.contains(CreateGroupReplica.name());
    }

    private boolean checkStorageSystemType(StorageSystem system) {
        if (StorageSystem.Type.scaleio.name().equalsIgnoreCase(system.getSystemType())
                || StorageSystem.Type.xtremio.name().equalsIgnoreCase(system.getSystemType())
                || StorageSystem.Type.hds.name().equalsIgnoreCase(system.getSystemType())
                || StorageSystem.Type.openstack.name().equalsIgnoreCase(system.getSystemType())
                || StorageSystem.Type.ceph.name().equalsIgnoreCase(system.getSystemType())) {
            return true;
        }
        return false;
    }

}
