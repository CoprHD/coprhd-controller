/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.UnsignedInteger16;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StorageSystem.SupportedReplicationTypes;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;

public class SupportedAsynchronousActionsProcessor extends StorageProcessor {
    private static final Logger _log = LoggerFactory.getLogger(SupportedAsynchronousActionsProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
        	_log.info("***Inside SupportedAsynchronousActionsProcessor****");
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            Iterator<CIMInstance> iterator = (Iterator<CIMInstance>) resultObj;
            while (iterator.hasNext()) {
                CIMInstance instance = iterator.next();
                UnsignedInteger16[] supportedAsyncActions =
                        (UnsignedInteger16[]) instance.getPropertyValue(Constants.SUPPORTED_ASYNCHRONOUS_ACTIONS);
                StorageSystem device = getStorageSystem(_dbClient, profile.getSystemId());
                addSupportedAsynchronousActionsToStorageSystem(supportedAsyncActions, device);
                
                StringSet replicationTypes = new StringSet();
                replicationTypes.add(SupportedReplicationTypes.LOCAL.toString());
                device.setSupportedReplicationTypes(replicationTypes);
            }
        } catch (Exception e) {
            _log.error("Supported asynchronous action processing failed", e);
        }
    }

    private void addSupportedAsynchronousActionsToStorageSystem(UnsignedInteger16[] supportedAsyncActions, StorageSystem device) {
        StringSet set = new StringSet();
        if(supportedAsyncActions!= null){
            for (UnsignedInteger16 actionValue : supportedAsyncActions) {
                switch(actionValue.intValue()) {
                    case Constants.CREATE_ELEMENT_REPLICA_ASYNC_ACTION: set.add(StorageSystem.AsyncActions.CreateElementReplica.name());
                        break;
                    case Constants.CREATE_GROUP_REPLICA_ASYNC_ACTION: set.add(StorageSystem.AsyncActions.CreateGroupReplica.name());
                        break;
                    default:
                        _log.warn("Encountered unknown supported asynchronous action {} for StorageSystem {}", actionValue.intValue(), device.getId());
                }
            }
        }

        device.setSupportedAsynchronousActions(set);
        _dbClient.persistObject(device);
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        // do nothing
    }
}
