/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.ibm.xiv;

import java.util.Iterator;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.UnsignedInteger16;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedReplicationTypes;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

public class XIVSupportedAsynchronousActionsProcessor extends StorageProcessor {
    private static final Logger _log = LoggerFactory
            .getLogger(XIVSupportedAsynchronousActionsProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            _log.info("processResult");
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap
                    .get(Constants.ACCESSPROFILE);
            @SuppressWarnings("unchecked")
            Iterator<CIMInstance> iterator = (Iterator<CIMInstance>) resultObj;
            while (iterator.hasNext()) {
                CIMInstance instance = iterator.next();
                UnsignedInteger16[] supportedAsyncActions = (UnsignedInteger16[]) instance
                        .getPropertyValue(Constants.SUPPORTED_ASYNCHRONOUS_ACTIONS);
                StorageSystem device = getStorageSystem(_dbClient,
                        profile.getSystemId());

                StringSet supportedAsyncActionsSet = new StringSet();
                if (supportedAsyncActions != null) {
                    for (UnsignedInteger16 actionValue : supportedAsyncActions) {
                        switch (actionValue.intValue()) {
                        case Constants.CREATE_ELEMENT_REPLICA_ASYNC_ACTION:
                            supportedAsyncActionsSet
                                    .add(StorageSystem.AsyncActions.CreateElementReplica
                                            .name());
                            break;
                        case Constants.CREATE_GROUP_REPLICA_ASYNC_ACTION:
                            supportedAsyncActionsSet
                                    .add(StorageSystem.AsyncActions.CreateGroupReplica
                                            .name());
                            break;
                        default:
                        }
                    }
                }

                device.setSupportedAsynchronousActions(supportedAsyncActionsSet);

                StringSet replicationTypes = new StringSet();
                replicationTypes.add(SupportedReplicationTypes.LOCAL.name());
                device.setSupportedReplicationTypes(replicationTypes);

                _dbClient.persistObject(device);
            }
        } catch (Exception e) {
            _log.error("Supported asynchronous action processing failed: ", e);
        }
    }
}
