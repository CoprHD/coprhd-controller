/*
 * Copyright (c) 2013 EMC Corporation
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
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

public class DeviceTypeMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory.getLogger(DeviceTypeMatcher.class);

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        if (null != attributeMap
                && attributeMap.containsKey(Attributes.system_type.toString())
                && !((StringSet) attributeMap.get(Attributes.system_type.toString()))
                        .contains(VirtualPool.SystemType.NONE.toString())) {
            return true;
        }
        return false;
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap,
            StringBuffer errorMessage) {

        StringSet deviceTypes = (StringSet) attributeMap.get(Attributes.system_type.toString());
        _logger.info(" Device Type {} Matcher Started {} :", deviceTypes, Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        List<StoragePool> filteredPools = new ArrayList<StoragePool>();

        Set<URI> systems = new HashSet<>();
        Map<URI, List<StoragePool>> systemMap = new HashMap<>();
        for (StoragePool pool : pools) {
            if (pool.getStorageDevice() != null) {
                boolean added = systems.add(pool.getStorageDevice());
                if (added) {
                    systemMap.put(pool.getStorageDevice(), new ArrayList<StoragePool>());
                }
                systemMap.get(pool.getStorageDevice()).add(pool);
            }
        }

        List<StorageSystem> devices = _objectCache.queryObject(StorageSystem.class, systems);
        for (StorageSystem system : devices) {
            if (deviceTypes.contains(system.getSystemType())) {
                filteredPools.addAll(systemMap.get(system.getId()));
            }
        }
        _logger.info("Device Type {} Matcher Ended {} :", deviceTypes,
                Joiner.on("\t").join(getNativeGuidFromPools(filteredPools)));
        
        if(CollectionUtils.isEmpty(filteredPools)){
            errorMessage.append(String.format("No matching storage pool available for the given device type %s. ", deviceTypes));
            _logger.error(errorMessage.toString());
        }
        return filteredPools;
    }

    @Override
    public Map<String, Set<String>> getAvailableAttribute(List<StoragePool> neighborhoodPools,
            URI vArrayId) {
        try {
            Map<String, Set<String>> availableAttrMap = new HashMap<String, Set<String>>(1);
            Set<String> availableAttrValues = new HashSet<String>();
            Set<URI> systems = new HashSet<>();
            for (StoragePool pool : neighborhoodPools) {
                if (pool.getStorageDevice() != null) {
                    systems.add(pool.getStorageDevice());
                }
            }

            List<StorageSystem> devices = _objectCache.queryObject(StorageSystem.class, systems);
            for (StorageSystem system : devices) {
                availableAttrValues.add(system.getSystemType());
            }
            if (!availableAttrValues.isEmpty()) {
                availableAttrMap.put(Attributes.system_type.toString(), availableAttrValues);
                return availableAttrMap;
            }
        } catch (Exception e) {
            _logger.error("Exception occurred while getting available attributes using DeviceTypeMatcher.", e);
        }
        return Collections.emptyMap();
    }

}
