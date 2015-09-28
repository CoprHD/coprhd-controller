/*
 * Copyright (c) 2013 EMC Corporation
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

        // Check if VPlex MetroPoint has been enabled.
        Boolean metroPointEnabled = (Boolean) attributeMap.get(Attributes.metropoint.toString());

        // If MetroPoint is enabled, we still want to execute the protection attribute matcher.
        if (metroPointEnabled == null || !metroPointEnabled) {
            // First we need to check is if we have a RP+VPLEX setup where the user has chosen to use
            // the Highly Available varray as the RP Source. If so, we can ignore this Protection matching.
            String haRP = (String) attributeMap.get(Attributes.high_availability_rp.toString());

            if (NullColumnValueGetter.isNotNullValue(haRP)) {
                _logger.info("Using Highly Available Virtual Array as the RP Source, disregarding protection matching.");
                return allPools;
            }
        }

        StringMap rpMap = (StringMap) attributeMap.get(Attributes.recoverpoint_map.name());
        _logger.info("Pools matching protection attributes Started {}, {} :", rpMap,
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));

        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        Iterator<StoragePool> poolIterator = allPools.iterator();

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

            // If protection is not specified, we can allow this pool.
            if (rpMap == null || rpMap.isEmpty()) {
                _logger.info(String.format("Storage pool [%s] allowed.", storagePool.getLabel()));
                matchedPools.add(storagePool);
                continue;
            }

            // We already know that Protection has been specified, now check to see if VPLEX has been specified
            boolean isRPVPlex = NullColumnValueGetter.isNotNullValue(
                    (String) attributeMap.get(Attributes.high_availability_type.toString()));

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
                // If there were no virtual arrays associated with this RP system, then the pool
                // is not allowed
                if (ps.getVirtualArrays() == null || ps.getVirtualArrays().isEmpty()) {
                    _logger.warn(String.format("Protection System [%s] has no associated virtual arrays. " +
                            "Can not use storage pool [%s]. Please run Protection System discovery.", ps.getLabel(), storagePool.getLabel()));
                    continue;
                }

                // Make sure the virtual arrays in the virtual pool definition are in the list
                // of virtual arrays for this RP system. If not, move on.
                for (String vArray : rpMap.keySet()) {
                    // If the virtual array associated with this storage pool can't be seen by this RP system,
                    // then this pool is not allowed
                    if (!ps.getVirtualArrays().contains(vArray)) {
                        _logger.info(String.format("Virtual array [%s] of Storage Pool [%s] can't be seen by Protection System [%s]. " +
                                "Disregard storage pool.", vArray, storagePool.getLabel(), ps.getLabel()));
                        continue;
                    }

                    String vpoolId = rpMap.get(vArray);
                    URI vpoolURI = null;

                    if (vpoolId != null && !vpoolId.isEmpty()) {
                        vpoolURI = URI.create(vpoolId);
                    }

                    // If the virtual pool was not specified for this target virtual array, then it's the pool
                    // we're in right now. We already know that our target virtual array is in this RP system's
                    // domain, so we'll say yes to this pool.
                    if (vpoolURI == null) {
                        _logger.info(String.format("No target virtual pool defined. Using source virtual pool. " +
                                "Storage pool [%s] allowed.", storagePool.getLabel()));
                        matchedPools.add(storagePool);
                        break;
                    }

                    // If we can find pools that match the target virtual pool in the target virtual array that
                    // is already recognized as visible to the RP system, then this pool is accepted.
                    VirtualPool targetVpool = _objectCache.queryObject(VirtualPool.class, vpoolURI);
                    List<StoragePool> targetPoolList = VirtualPool.getValidStoragePools(targetVpool, _objectCache.getDbClient(), true);

                    boolean targetVpoolSpecifiesVPlex = VirtualPool.vPoolSpecifiesHighAvailability(targetVpool);

                    _logger.info(String.format("Check the storage pools from target virtual pool [%s] to find a " +
                            "match for storage pool [%s]", targetVpool.getLabel(), storagePool.getLabel()));
                    if (vpoolHasStoragePoolMatchingVarray(vArray, targetPoolList, targetVpoolSpecifiesVPlex)) {
                        _logger.info(String.format("Target virtual pool [%s] has a matched storage pool with virtual array [%s]. " +
                                "Storage pool [%s] allowed.", targetVpool.getLabel(), vArray, storagePool.getLabel()));
                        matchedPools.add(storagePool);
                        break;
                    }

                    _logger.info(String.format("No match for Storage pool [%s] for virtual array [%s].", storagePool.getLabel(), vArray));
                }
            }
        }

        if (!validProtectionSystems) {
            _logger.info("No valid protection system could be found for storage pools.  Please check data protection systems to ensure they are not inactive or invalid.");
        }

        _logger.info("Pools matching protection attributes Ended. " +
                "Matched pools are: {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;
    }

    /**
     * Determine if the varray with the passed id has a storage pool that
     * satisfies the RP Vpool.
     *
     * @param varrayId The id of a varray.
     * @param tgtVpoolPoolList The pool list for the Target Vpool.
     * @param isRPVPlex Flag indicate whether this is an RP+VPLEX request or not
     *
     * @return true if the varray has a storage pool that matches the HA
     *         VPool, false otherwise.
     */
    private boolean vpoolHasStoragePoolMatchingVarray(String varrayId,
            List<StoragePool> tgtVpoolPoolList, boolean isRPVPlex) {
        _logger.info(String.format("Checking storage pools from the target virtual pool."));
        for (StoragePool tgtStoragePool : tgtVpoolPoolList) {
            // If this is an RP+VPLEX/MetroPoint request we need to check connectivity to the
            // VPLEX for the target storage pools as well.
            if (isRPVPlex) {
                _logger.info(String.format("RP+VPLEX/MetroPoint request, first need to check the " +
                        "target storage pool [%s] is valid.", tgtStoragePool.getLabel()));
                if (!validRPVPlexStoragePool(tgtStoragePool)) {
                    _logger.info(String.format("Target storage pool [%s] is NOT valid for " +
                            "RP+VPLEX/MetroPoint request.", tgtStoragePool.getLabel()));
                    return false;
                }
                _logger.info(String.format("Target storage pool [%s] is valid for " +
                        "RP+VPLEX/MetroPoint request.", tgtStoragePool.getLabel()));
            }

            _logger.info(String.format("Checking if tagged virtual arrays from target " +
                    "storage pool [%s] contains virtual array [%s].", tgtStoragePool.getLabel(), varrayId));
            StringSet poolVarrays = tgtStoragePool.getTaggedVirtualArrays();
            if (poolVarrays != null && poolVarrays.contains(varrayId)) {
                _logger.info(String.format("Virtual array [%s] found in tagged virtual arrays " +
                        "of target pool..", varrayId));
                return true;
            }
            _logger.info(String.format("Virtual array [%s] NOT found in tagged virtual arrays " +
                    "of target pool.", varrayId));
        }
        return false;
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
        List<String> vplexSystemsForPool =
                VPlexHighAvailabilityMatcher.getVPlexStorageSystemsForStorageSystem(_objectCache.getDbClient(),
                        storagePool.getStorageDevice(), null);

        // The Storage Pool needs to have a VPLEX connected to it to be valid, and that's
        // all we really care about at this point.
        if (vplexSystemsForPool != null && !vplexSystemsForPool.isEmpty()) {
            return true;
        }

        return false;
    }
}
