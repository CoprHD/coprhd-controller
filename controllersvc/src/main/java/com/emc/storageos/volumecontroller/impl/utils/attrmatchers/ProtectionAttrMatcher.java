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

import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * ProtectionAttrMatcher is responsible to match all the pools with
 * the given CoS protection attributes.
 *
 */
public class ProtectionAttrMatcher extends AttributeMatcher {

    private static final Logger _logger = LoggerFactory
            .getLogger(ProtectionAttrMatcher.class);

    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> allPools, Map<String, Object> attributeMap) {
        StringMap rpMap = (StringMap) attributeMap.get(Attributes.recoverpoint_map.name());
        _logger.info("Pools matching protection attributes Started {}, {} :", rpMap,
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        
        // If protection is not specified, we can return all remaining pools.
        if (rpMap == null || rpMap.isEmpty()) {
            _logger.info("No protection specified, return all remaining pools.");
            return allPools;
        }

        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        Iterator<StoragePool> poolIterator = allPools.iterator();
        
        // Check if MetroPoint has been enabled.
        Boolean metroPointEnabled = (Boolean) attributeMap.get(Attributes.metropoint.toString());        
        boolean isRPVPlex = NullColumnValueGetter.isNotNullValue(
                (String) attributeMap.get(Attributes.high_availability_type.toString()));
        
        // Flag to indicate if this is an RP+VPLEX setup with HA as the Source.
        boolean haAsRPSource = false;
        
        if (isRPVPlex && (metroPointEnabled == null || !metroPointEnabled)) {
            _logger.info("Not a MetroPoint Virtual Pool");
            String haVarrayForRPSource = (String) attributeMap.get(Attributes.high_availability_rp.toString());
            haAsRPSource = NullColumnValueGetter.isNotNullValue(haVarrayForRPSource);
            if (haAsRPSource) {         
                _logger.info("Virtual Pool indicates to use HA as the RP Source");
                // Get HA Virtual Pool                                                
                String haVpoolId = (String) attributeMap.get(Attributes.high_availability_vpool
                        .toString());
                
                List<StoragePool> haVpoolPoolList = new ArrayList<StoragePool>();
                if (haVpoolId != null) {                    
                    VirtualPool haVpool = _objectCache.queryObject(VirtualPool.class, URI.create(haVpoolId));                    
                    haVpoolPoolList = VirtualPool.getValidStoragePools(haVpool, _objectCache.getDbClient(), true);
                    _logger.info(String.format("HA Virtual Pool exists [%s](%s), consider Storage Pools from it.", 
                            haVpool.getLabel(), haVpool.getId()));
                    
                } else {
                    _logger.info("No HA Virtual Pool exists, consider Storage Pools from main Virtual Pool.");
                    haVpoolPoolList.addAll(allPools);
                }
                                
                List<StoragePool> filterPools = new ArrayList<StoragePool>();
                _logger.info(String.format("Remove Storage Pools that are not tagged with Virtual Array (%s).", 
                        haVarrayForRPSource));
                for (StoragePool haPool : haVpoolPoolList) {
                    if (haPool.getTaggedVirtualArrays() != null 
                            && !haPool.getTaggedVirtualArrays().contains(haVarrayForRPSource)) {                            
                        filterPools.add(haPool);
                    }
                }
                haVpoolPoolList.removeAll(filterPools);
                poolIterator = haVpoolPoolList.iterator();
                
                _logger.info("HA Storage Pools to consider: {}", 
                        Joiner.on("\t").join(getNativeGuidFromPools(haVpoolPoolList)));                                
            }
        }

        boolean validProtectionSystems = false;

        while (poolIterator.hasNext()) {
            StoragePool storagePool = poolIterator.next();
            _logger.info(String.format("Checking storage pool [%s]", storagePool.getLabel()));
            
            // If there is a mirror attribute, and the the pool doesn't fit mirror capabilities,
            // then the pool is not allowed.
            if (!checkMirrorProtectionValidPool(storagePool, attributeMap)) {
                _logger.info(String.format("Storage pool [%s] has a mirror attribute, " +
                        "and the the pool doesn't fit mirror capabilities. Disregard pool.", storagePool.getLabel()));
                continue;
            }
            
            // Get the RP systems for this pool.
            Set<ProtectionSystem> protectionSystems = ConnectivityUtil.getProtectionSystemsForStoragePool(_objectCache.getDbClient(),
                    storagePool, null, isRPVPlex);

            // Only pools connected to an RP system can potentially match.
            if (protectionSystems.isEmpty()) {
                _logger.info(String.format("Storage pool [%s] not connected to any Protection System. Disregard storage pool.",
                        storagePool.getLabel()));
                continue;
            } else {
                validProtectionSystems = true;
            }

            // Scan each RP system and see if the pool is valid, given the target/copy attributes
            for (ProtectionSystem ps : protectionSystems) {
                // If there are no virtual arrays associated with this RP system, then the pool
                // is not allowed
                if (ps.getVirtualArrays() == null || ps.getVirtualArrays().isEmpty()) {
                    _logger.warn(String.format("Protection System [%s] has no associated virtual arrays. " +
                            "Can not use storage pool [%s]. Please run Protection System discovery.", ps.getLabel(),
                            storagePool.getLabel()));
                    continue;
                }

                Map<String, Boolean> validStoragePoolForProtection = new HashMap<String, Boolean>();
                
                // Make sure the target virtual arrays defined are in the list
                // of virtual arrays for this RP system. If not, move on.
                for (String targetVarrayId : rpMap.keySet()) {
                    // Assume false to start
                    validStoragePoolForProtection.put(targetVarrayId, Boolean.FALSE);
                    
                    // If the virtual array associated with this storage pool can't be seen by this RP system,
                    // then this pool is not allowed
                    if (!ps.getVirtualArrays().contains(targetVarrayId)) {
                        _logger.info(String.format("Virtual array [%s] of Storage Pool [%s](%s) can't be seen by Protection System [%s](%s). " +
                                "Disregard storage pool.", targetVarrayId, storagePool.getLabel(), storagePool.getId(), ps.getLabel(), ps.getId()));
                        continue;
                    }
                    
                    // If the virtual pool was not specified for this target virtual array, then use the virtual pool
                    // we're in right now. We already know that our target virtual array is in this RP system's
                    // domain, so we'll say yes to this pool.
                    String targetVpoolId = rpMap.get(targetVarrayId);
                    if (NullColumnValueGetter.isNullValue(targetVpoolId)) {
                        _logger.info(String.format("No target virtual pool defined. Using source virtual pool. " +
                                "Storage pool [%s](%s) allowed.", storagePool.getLabel(), storagePool.getId()));                        
                        validStoragePoolForProtection.put(targetVarrayId, Boolean.TRUE);
                        break;
                    }

                    VirtualPool targetVpool = _objectCache.queryObject(VirtualPool.class, URI.create(targetVpoolId));
                    List<StoragePool> targetPoolList = VirtualPool.getValidStoragePools(targetVpool, _objectCache.getDbClient(), true);
                    boolean targetVpoolSpecifiesVPlex = VirtualPool.vPoolSpecifiesHighAvailability(targetVpool);
                    
                    // Check the target virtual pool for all valid storage pools. But only consider
                    // storage pools for the target virtual array in question.
                    // 
                    // We want to ensure that at least one target storage pool is connected to the same 
                    // Protection System as the source/candidate storage pool. If so, we have a match. 
                    for (StoragePool tgtPool : targetPoolList) {
                        // Only consider target storage pools for the target varray in question
                        if (tgtPool.getTaggedVirtualArrays() != null 
                                && tgtPool.getTaggedVirtualArrays().contains(targetVarrayId)) {
                            _logger.info(String.format("Checking target storage pool [%s](%s)...", tgtPool.getLabel(), tgtPool.getId() ));                            
                            // Get the RP systems for this target pool                           
                            Set<ProtectionSystem> targetProtectionSystems = ConnectivityUtil.getProtectionSystemsForStoragePool(_objectCache.getDbClient(),
                                    tgtPool, URI.create(targetVarrayId), targetVpoolSpecifiesVPlex);
                            // Check to see if the protection systems line up between the source storage pool
                            // and at least one target storage pool.
                            boolean matchFound = false;
                            for (ProtectionSystem targetProtectionSystem : targetProtectionSystems) {
                                if (targetProtectionSystem != null 
                                        && targetProtectionSystem.getId().equals(ps.getId())) {
                                    _logger.info(String.format("Target storage pool [%s](%s) is connected to same "
                                            + "Protection system [%s](%s) as source storage pool [%s](%s). "
                                            + "Storage pool allowed.", 
                                            tgtPool.getLabel(), tgtPool.getId(), ps.getLabel(), ps.getId(),
                                            storagePool.getLabel(), storagePool.getId()));
                                    validStoragePoolForProtection.put(targetVarrayId, Boolean.TRUE);
                                    matchFound = true;
                                    break;
                                } 
                            }
                            if (matchFound) {
                                // No need to loop through all the target pools, 
                                // we found at least one match.
                                break;
                            }
                        }
                    }                                        
                }
                                
                // Iterate through the results of valid storage pools, if there are any false entries for this
                // storage pool, we can not consider it.
                boolean matchedPool = true;
                for (Boolean validStoragePool : validStoragePoolForProtection.values()) {
                    if (!validStoragePool) {
                        matchedPool = false;
                        break;
                    }
                }
                //TODO: THIS IS JUST FOR TESTING
                _logger.info("ADDING STORAGEPOOL " + storagePool.forDisplay());
                matchedPools.add(storagePool);
                if (matchedPool) {
                    matchedPools.add(storagePool);
                } else {
                    _logger.info(String.format("Storage Pool [%s](%s) is not valid for protection.",                             
                            storagePool.getLabel(), storagePool.getId()));
                }
            }
        }

        if (!validProtectionSystems) {
            _logger.warn("No valid protection system could be found for storage pools. "
                    + "Please check data protection systems to ensure they are not inactive or invalid.");
        }

        if (haAsRPSource) {
            if (!matchedPools.isEmpty()) {
                // If we found at least 1 matched pool using HA as the RP Source, return all pools.
                matchedPools = allPools;
            }            
        }
        
        _logger.info("Pools matching protection attributes ended. " +
                "Matched pools are: {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;
    }

    /**
     * Check if the pool is valid given the Vpool protection attributes
     *
     * @param pool
     *            The pool to verify.
     *
     * @return true if the pool is valid to use for a protection
     */
    private boolean checkMirrorProtectionValidPool(StoragePool pool, Map<String, Object> attributeMap) {
        Integer mirrorValue = (Integer) attributeMap.get(Attributes.max_native_continuous_copies.name());

        if (mirrorValue != null && mirrorValue != VirtualPool.MAX_DISABLED) {

            String highAvailabilityType = (String) attributeMap.get(Attributes.high_availability_type.name());
            if (highAvailabilityType != null && (VirtualPool.HighAvailabilityType.vplex_local.name().equals(highAvailabilityType)
                    || VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(highAvailabilityType))) {
                // For VPLEX we don't want to check storage pools copyTypes as it will filter out pools
                // if the native array do not support mirrors, which doesn't matter for VPLEX
                // mirrors as we use back-end array to create back-end volumes only.
                _logger.info("Pool {} is OK, matched with mirror-enabled VPLEX vpool", pool.getLabel());
                return true;
            }

            StringSet copyTypes = pool.getSupportedCopyTypes();
            if (copyTypes != null && copyTypes.contains(StoragePool.CopyTypes.SYNC.name())) {
                _logger.info("Pool {} is OK, matched with mirror-enabled vpool", pool.getLabel());
                return true;
            }
            _logger.info("Pool {} was not matched with mirror-enabled vpool", pool.getLabel());
            return false;
        }
        // Mirror value not set, pool is allowed
        return true;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        // Check whether Protections is set in CoS or not.
        // ProtectionsAttrMatcher will be executed only if the protection attribute is set.
        if (null != attributeMap) {
            StringMap rpMap = (StringMap) attributeMap.get(Attributes.recoverpoint_map.name());
            Integer mirrorValue = (Integer) attributeMap.get(Attributes.max_native_continuous_copies.name());

            if ((mirrorValue != null && mirrorValue != VirtualPool.MAX_DISABLED) ||
                    (rpMap != null && !rpMap.isEmpty())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check to determine if the Storage Pool in question is valid for RP+VPLEX.
     *
     * If the Protection System contains the VPLEX in question AND both the Protection System and VPLEX
     * contain the same varray, then we can consider this pool valid to use for placement.
     *
     * @param storagePool The Storage Pool in question, must check to see if RP+VPLEX/MetroPoint
     *
     * @return true, if the Storage Pool is valid for RP+VPLEX/MetroPoint
     */
    private boolean validRPVPlexStoragePool(StoragePool storagePool) {
        // Get the VPLEXs connected to this pool
        List<String> vplexSystemsForPool = VPlexHighAvailabilityMatcher.getVPlexStorageSystemsForStorageSystem(_objectCache.getDbClient(),
                storagePool.getStorageDevice(), null);

        // The Storage Pool needs to have a VPLEX connected to it to be valid, and that's
        // all we really care about at this point.
        if (vplexSystemsForPool != null && !vplexSystemsForPool.isEmpty()) {
            return true;
        }

        return false;
    }
}
