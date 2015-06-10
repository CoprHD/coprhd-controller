/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.ibm.xiv;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger16;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.PoolProcessor;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;

public class XIVSupportedCopyTypesProcessor extends PoolProcessor {
    private static final Logger _log = LoggerFactory
            .getLogger(XIVSupportedCopyTypesProcessor.class);
    private DbClient _dbClient;
    private List<Object> _args;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap
                    .get(Constants.ACCESSPROFILE);
            Map<URI, StoragePool> poolsToMatchWithVpool = (Map<URI, StoragePool>) keyMap
                    .get(Constants.MODIFIED_STORAGEPOOLS);
            StorageSystem device = getStorageSystem(_dbClient,
                    profile.getSystemId());
            Iterator<CIMInstance> iterator = (Iterator<CIMInstance>) resultObj;
            while (iterator.hasNext()) {
                CIMInstance instance = iterator.next();

                CIMObjectPath poolPath = getObjectPathfromCIMArgument();
                String instanceId = poolPath.getKeyValue(Constants.INSTANCEID)
                        .toString();
                // instanceId is the pool's nativeId
                StoragePool storagePool = checkStoragePoolExistsInDB(
                        instanceId, _dbClient, device);
                if (storagePool == null) {
                    _log.warn("No storage pool");
                    continue;
                }

                String thinProvisionedPreAllocateSupported = instance
                        .getPropertyValue(
                                Constants.THIN_PROVISIONED_CLIENT_SETTABLE_RESERVE)
                        .toString();
                UnsignedInteger16[] copyTypes = (UnsignedInteger16[]) instance
                        .getPropertyValue(Constants.SUPPORTED_COPY_TYPES);

                addCopyTypesToStoragePool(copyTypes, storagePool,
                        thinProvisionedPreAllocateSupported,
                        poolsToMatchWithVpool);
            }
        } catch (Exception e) {
            _log.error("Supported copy types processing failed: ", e);
        }
    }

    private void addCopyTypesToStoragePool(UnsignedInteger16[] copyTypes,
            StoragePool storagePool,
            String thinProvisionedPreAllocateSupported,
            Map<URI, StoragePool> poolsToMatchWithVpool) {
        StringSet set = new StringSet();
        for (UnsignedInteger16 n : copyTypes) {
            switch (n.intValue()) {
            case Constants.ASYNC_COPY_TYPE:
                set.add(StoragePool.CopyTypes.ASYNC.name());
                break;
            case Constants.SYNC_COPY_TYPE:
                set.add(StoragePool.CopyTypes.SYNC.name());
                break;
            case Constants.UNSYNC_ASSOC_COPY_TYPE:
                set.add(StoragePool.CopyTypes.UNSYNC_ASSOC.name());
                break;
            case Constants.UNSYNC_UNASSOC_COPY_TYPE:
                set.add(StoragePool.CopyTypes.UNSYNC_UNASSOC.name());
                break;
            default:
                _log.warn("Encountered unknown copy type {} for pool {}",
                        n.intValue(), storagePool.getId());
            }
        }

        storagePool.setThinVolumePreAllocationSupported(Boolean
                .valueOf(thinProvisionedPreAllocateSupported));
        // add to modified pools list if pool's property which is required for
        // vPool matcher, has changed.
        // If the modified list already has this pool, skip the check.
        if (!poolsToMatchWithVpool.containsKey(storagePool.getId())
                && ImplicitPoolMatcher.checkPoolPropertiesChanged(
                        storagePool.getSupportedCopyTypes(), set)) {
            poolsToMatchWithVpool.put(storagePool.getId(), storagePool);
        }

        storagePool.setSupportedCopyTypes(set);
        _dbClient.persistObject(storagePool);
    }

    /**
     * return 1st Argument in inputArguments used to call this SMI-S call.
     */
    private CIMObjectPath getObjectPathfromCIMArgument() {
        Object[] arguments = (Object[]) _args.get(0);
        return (CIMObjectPath) arguments[0];
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> args)
            throws BaseCollectionException {
        _args = args;
    }
}
