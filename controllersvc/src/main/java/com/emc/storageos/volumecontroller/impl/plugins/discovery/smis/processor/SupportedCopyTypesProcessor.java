/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.UnsignedInteger16;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SupportedCopyTypesProcessor extends PoolProcessor {
    private static final Logger _log = LoggerFactory.getLogger(SupportedCopyTypesProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            Map<URI, StoragePool> poolsToMatchWithVpool = (Map<URI, StoragePool>) keyMap
                    .get(Constants.MODIFIED_STORAGEPOOLS);
            StorageSystem device = getStorageSystem(_dbClient, profile.getSystemId());
            boolean isVmax3 = device.checkIfVmax3();
            Iterator<CIMInstance> iterator = (Iterator<CIMInstance>) resultObj;
            while (iterator.hasNext()) {
                CIMInstance instance = iterator.next();
                String instanceID = getCIMPropertyValue(instance, Constants.INSTANCEID);
                String thinProvisionedPreAllocateSupported = Boolean.FALSE.toString();
                if (!isVmax3){
                    thinProvisionedPreAllocateSupported = instance.getPropertyValue(
                            Constants.THIN_PROVISIONED_CLIENT_SETTABLE_RESERVE).toString();
                } else {
                    thinProvisionedPreAllocateSupported = Boolean.TRUE.toString();
                }
                UnsignedInteger16[] copyTypes =
                        (UnsignedInteger16[]) instance.getPropertyValue(Constants.SUPPORTED_COPY_TYPES);
                
                String nativeID = getNativeIDFromInstance(instanceID);
                StoragePool storagePool = checkStoragePoolExistsInDB(nativeID, _dbClient, device);

                if (storagePool == null) {
                    _log.warn("No storage pool");
                    continue;
                }

                addCopyTypesToStoragePool(copyTypes, storagePool, thinProvisionedPreAllocateSupported, poolsToMatchWithVpool);
            }
        } catch (Exception e) {
            _log.error("Supported copy types processing failed", e);
        }
    }

    private void addCopyTypesToStoragePool(UnsignedInteger16[] copyTypes, StoragePool storagePool,
            String thinProvisionedPreAllocateSupported, Map<URI, StoragePool> poolsToMatchWithVpool) {
        StringSet set = new StringSet();

        for (UnsignedInteger16 n : copyTypes) {
            switch(n.intValue()) {
                case Constants.ASYNC_COPY_TYPE: set.add(StoragePool.CopyTypes.ASYNC.name());
                    break;
                case Constants.SYNC_COPY_TYPE: set.add(StoragePool.CopyTypes.SYNC.name());
                    break;
                case Constants.UNSYNC_ASSOC_COPY_TYPE: set.add(StoragePool.CopyTypes.UNSYNC_ASSOC.name());
                    break;
                case Constants.UNSYNC_UNASSOC_COPY_TYPE: set.add(StoragePool.CopyTypes.UNSYNC_UNASSOC.name());
                    break;
                default:
                    _log.warn("Encountered unknown copy type {} for pool {}", n.intValue(), storagePool.getId());
            }
        }
        storagePool.setThinVolumePreAllocationSupported(Boolean.valueOf(thinProvisionedPreAllocateSupported));
        // add to modified pools list if pool's property which is required for vPool matcher, has changed.
        // If the modified list already has this pool, skip the check.
        if (!poolsToMatchWithVpool.containsKey(storagePool.getId()) && 
                ImplicitPoolMatcher.checkPoolPropertiesChanged(storagePool.getSupportedCopyTypes(), set)) {
            poolsToMatchWithVpool.put(storagePool.getId(), storagePool);
        }
        storagePool.setSupportedCopyTypes(set);
        _dbClient.persistObject(storagePool);
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        // do nothing
    }
}
