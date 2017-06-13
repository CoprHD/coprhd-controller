/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.AttributeMatcher;
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
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> allPools, Map<String, Object> attributeMap,
            StringBuffer errorMessage) {
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
                vplexSystemsForPool = getVPlexStorageSystemsForStorageSystem(_objectCache.getDbClient(),
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

        _logger.info("Pools Matching ha attribute Matcher Ended:{}",
                Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));

        if (CollectionUtils.isEmpty(matchedPools)) {
            String message = "No matching storage pool found for VPLEX high availability. ";
            if (errorMessage != null && !errorMessage.toString().contains(message)) {
                errorMessage.append(message);
            }
            _logger.error(errorMessage.toString());
        }
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
}