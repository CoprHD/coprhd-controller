/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;




import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
/*
 * In case of VMAX, if drive Type is not selected then always prefer FC Pool for provisioning.
 * If FC Pool not available, then preferred order "FC","SAS","NL_SAS","SATA","SSD"
 */
public class PoolPreferenceBasedOnDriveMathcer extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory.getLogger(PoolPreferenceBasedOnDriveMathcer.class);
    private static final String[] driveTypeOrder = {"FC","SAS","NL_SAS","SATA","SSD"};
    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        //run only if System Type is VMAX.
        return (null != attributeMap
                && attributeMap.containsKey(Attributes.system_type.toString())
                && ((StringSet) attributeMap.get(Attributes.system_type.toString()))
                        .contains(VirtualPool.SystemType.vmax.toString()));
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> allPools, Map<String, Object> attributeMap) {
        List<StoragePool> optimalPools = new ArrayList<StoragePool>();
        ListMultimap<String, StoragePool> groupSPByDriveType =  ArrayListMultimap.create();
        _logger.info("Optimal Pool Selection Matcher Started {} :", Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        for (StoragePool pool : allPools) {
            if (null != pool.getSupportedDriveTypes()) {
                for (String driveTypes : pool.getSupportedDriveTypes()) {
                    String[] driveTypeArr = driveTypes.split(",");
                    for (String driveType : driveTypeArr) {
                        groupSPByDriveType.put(driveType, pool);
                    }
                }
            }
            
        }
        _logger.info("Grouping Storage Pools by Drive Types : {}",Joiner.on("\t").join(groupSPByDriveType.asMap().entrySet()));
        for (String driveType : driveTypeOrder) {
            _logger.info("Processing Pools with Drive Type {}", driveType);
            Collection<StoragePool> pools =  groupSPByDriveType.asMap().get(driveType);
            if (null != pools && !pools.isEmpty()) {
                optimalPools.addAll(pools);
                break;
            } else {
                _logger.info("Not able to find any Pools with Drive Type {}",driveType);
            }
        }
        _logger.info("Optimal Pool Selection Matcher Ended {} :", Joiner.on("\t").join(getNativeGuidFromPools(optimalPools)));
        return optimalPools;
    }
    
    

}
