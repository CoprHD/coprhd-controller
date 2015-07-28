/*
 * Copyright (c) 2015. EMC Corporation All Rights Reserved This software contains the intellectual
 * property of EMC Corporation or is licensed to EMC Corporation from third parties. Use of this
 * software and the intellectual property contained therein is expressly limited to the terms and
 * conditions of the License Agreement under which it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.executors;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class FailMechanismStorageSyncsStrategy implements ExecutorStrategy {
    private static final Logger log = LoggerFactory.getLogger(FailMechanismStorageSyncsStrategy.class);
    private SmisCommandHelper helper;

    public FailMechanismStorageSyncsStrategy(SmisCommandHelper helper) {
        this.helper = helper;
    }

    @Override
    public void execute(Collection<CIMObjectPath> objectPaths, StorageSystem provider) throws Exception {
        try {
            CIMInstance syncInstance = helper.checkExists(provider, objectPaths.iterator().next(), false, false);
            if (null != syncInstance) {
                String copyState = syncInstance.getPropertyValue(SmisConstants.CP_COPY_STATE).toString();
                CIMArgument[] args = null;
                if (String.valueOf(SmisConstants.FAILOVER_SYNC_PAIR).equalsIgnoreCase(copyState)) {
                    log.info("Already in failed over State, invoking failback.");
                    args = helper.getASyncPairFailBackInputArguments(objectPaths);
                } else {
                    args = helper.getFailoverSyncPairInputArguments(objectPaths);
                }
                helper.callModifyListReplica(provider, args);
            }

        } catch (Exception e) {
            log.error("problem executing Fail mechanism", e);
            throw e;
        }
    }
}
