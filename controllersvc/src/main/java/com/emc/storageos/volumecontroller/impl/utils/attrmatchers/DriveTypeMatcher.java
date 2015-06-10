/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool.SupportedDriveTypes;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

public class DriveTypeMatcher extends ConditionalAttributeMatcher {
    private static final Logger _logger = LoggerFactory
    .getLogger(DriveTypeMatcher.class);
    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        //same code is being used for 2 different code paths, placement & cos matchers, and both behaviors are different.
        // hence this flag is used
        if (isAutoTieringPolicyOn(attributeMap) && !attributeMap.containsKey(AttributeMatcher.PLACEMENT_MATCHERS)) {
            _logger.info("Skipping DriveType matcher, as VMAX FAST Policy is chosen");
            return false;
        }
        
        return (null != attributeMap && 
                attributeMap.containsKey(Attributes.drive_type.toString()) 
                && !SupportedDriveTypes.NONE.toString().equals(attributeMap.get(Attributes.drive_type.toString()).toString()));
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(
            List<StoragePool> allPools, Map<String, Object> attributeMap) {
        List<StoragePool> filteredPools = new ArrayList<StoragePool>();
        String desiredInitialDriveType = attributeMap.get(Attributes.drive_type.toString()).toString();
        _logger.info("Drive Type Matcher Started : {}, {}", desiredInitialDriveType,
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        for (StoragePool pool : allPools) {
            if (null == pool || null == pool.getSupportedDriveTypes())
                continue;
            StorageSystem system = _objectCache.getDbClient().queryObject(StorageSystem.class, pool.getStorageDevice());
            // If pool belongs to a HDS system and contains External storage, then add the pools to matched list.
            if (Type.isHDSStorageSystem(StorageSystem.Type.valueOf(system.getSystemType()))) {
                if (pool.getSupportedDriveTypes().contains(SupportedDriveTypes.UNKNOWN.toString())) {
                    filteredPools.add(pool);
                    continue;
                }
            }
            if (pool.getSupportedDriveTypes().contains(desiredInitialDriveType)) {
                filteredPools.add(pool);
            } else {
                _logger.info("Ignoring pool {} as it does not support Drive types.", pool.getId());
            }
        }
        _logger.info("Drive Type Matcher Ended : {}, {}", desiredInitialDriveType,
                Joiner.on("\t").join(getNativeGuidFromPools(filteredPools)));
        return filteredPools;
    }
    
    @Override
    public Map<String, Set<String>> getAvailableAttribute(List<StoragePool> neighborhoodPools,
                                        URI vArrayId) {
        try {
            Map<String, Set<String>> availableAttrMap = new HashMap<String, Set<String>>(1);
            Set<String> availableAttrValues = new HashSet<String>();
            for (StoragePool pool : neighborhoodPools) {
                StringSet driveTypes = pool.getSupportedDriveTypes(); 
                if(null != driveTypes && !driveTypes.isEmpty()) {
                    availableAttrValues.addAll(driveTypes);
                }
            }
            if (!availableAttrValues.isEmpty()) {
                availableAttrMap.put(Attributes.drive_type.toString(), availableAttrValues);
                return availableAttrMap;
            }
        } catch (Exception e) {
            _logger.error("Exception occurred while getting available attributes using DriveTypeMatcher.", e);
        }
        return Collections.emptyMap();
    }
}
