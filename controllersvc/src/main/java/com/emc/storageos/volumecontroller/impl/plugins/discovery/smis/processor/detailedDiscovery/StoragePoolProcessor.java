/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
/**
 * Processor to handle Storage Pools.
 * 
 */
public class StoragePoolProcessor extends StorageProcessor {
    private Logger _logger = LoggerFactory.getLogger(StoragePoolProcessor.class);
    private DbClient _dbClient;
    
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            
            while (it.hasNext()) {
                CIMObjectPath poolPath = it.next();
                String poolNativeGuid = NativeGUIDGenerator
                        .generateNativeGuidForPool(poolPath);
                StoragePool pool = checkStoragePoolExistsInDB(poolNativeGuid, _dbClient);
                if (pool != null && validPool(poolPath)) {
                    addPath(keyMap, operation.get_result(), poolPath);
                    // add VMAX2 Thin pools to get BoundVolumes later
                    if (poolPath.toString().contains(StoragePool.PoolClassNames.Symm_VirtualProvisioningPool.toString())) {
                        addPath(keyMap, Constants.VMAX2_THIN_POOLS, poolPath);
                    }
                    
                    String poolClass = poolPath.getObjectName();
                    if (poolClass.startsWith(SYMM_CLASS_PREFIX) &&
                        !poolClass.equals(StoragePool.PoolClassNames.Symm_SRPStoragePool.name())) {
                        addPath(keyMap, Constants.VMAX2POOLS, poolPath);
                    }
                }
            }
        } catch (Exception e) {
            _logger.error("Processing Storage Pool failed", e);
        }
    }

    private boolean validPool(CIMObjectPath poolPath) {
        if (poolPath.toString().contains(
                StoragePool.PoolClassNames.Clar_DeviceStoragePool.toString())
                || poolPath.toString().contains(
                        StoragePool.PoolClassNames.Clar_UnifiedStoragePool.toString())
                || poolPath.toString().contains(
                        StoragePool.PoolClassNames.Symm_DeviceStoragePool.toString())
                || poolPath.toString().contains(
                        StoragePool.PoolClassNames.Symm_VirtualProvisioningPool
                                .toString())
                || poolPath.toString().contains(
                        StoragePool.PoolClassNames.Symm_SRPStoragePool
                        .toString())) {
            return true;
        }
        return false;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}

