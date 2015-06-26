/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2013. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.db.client.model.StorageSystem.SupportedProvisioningTypes;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

public class ProvisioningTypeMatcher extends AttributeMatcher{

    private static final Logger _logger = LoggerFactory
    .getLogger(ProvisioningTypeMatcher.class);

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        if (null != attributeMap
                && attributeMap.containsKey(Attributes.provisioning_type.toString())
                && !VirtualPool.ProvisioningType.NONE.toString().equalsIgnoreCase(
                        attributeMap.get(Attributes.provisioning_type.toString()).toString()))
            return true;
        return false;
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
        String provisioningType = attributeMap.get(Attributes.provisioning_type.toString()).toString();
        _logger.info("Pools Matching provisioningType Started {}, {} :", provisioningType, Joiner.on("\t").join( getNativeGuidFromPools(pools)));
        Map<URI, StorageSystem> storageSystemMap = new HashMap<URI, StorageSystem>();
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(pools);
        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            StorageSystem system = getStorageSystem(storageSystemMap, pool);

            if (VirtualPool.ProvisioningType.Thick.name().equalsIgnoreCase(provisioningType)
                    && (StoragePool.SupportedResourceTypes.THIN_ONLY.name()
                            .equalsIgnoreCase(pool.getSupportedResourceTypes()) ||
                        SupportedProvisioningTypes.THIN.name()
                                    .equalsIgnoreCase(system.getSupportedProvisioningType()))) {
                // if pool's supported volume Type is thick, means we need to filter this pool
                _logger.info("Ignoring pool {} as thick resources are not supported :", pool.getNativeGuid());
                filteredPoolList.remove(pool);
            } else if (VirtualPool.ProvisioningType.Thin.name().equalsIgnoreCase(provisioningType)
                    && (StoragePool.SupportedResourceTypes.THICK_ONLY.name()
                            .equalsIgnoreCase(pool.getSupportedResourceTypes()) ||
                        SupportedProvisioningTypes.THICK.name()
                                    .equalsIgnoreCase(system.getSupportedProvisioningType()))) {
                _logger.info("Ignoring pool {} as thin resources are not supported :", pool.getNativeGuid());
                filteredPoolList.remove(pool);
            }
        }
        _logger.info("Pools Matching provisioningType Ended {}, {}", provisioningType, Joiner.on("\t").join( getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;
        
        
    }

}
