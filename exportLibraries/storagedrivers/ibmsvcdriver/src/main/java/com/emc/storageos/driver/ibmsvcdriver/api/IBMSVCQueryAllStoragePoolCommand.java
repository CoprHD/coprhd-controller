/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.List;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePool.PoolOperationalStatus;

public class IBMSVCQueryAllStoragePoolCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryAllStoragePoolResult> {

    public static final String STORAGE_POOL_PARAMS_INFO = "StoragePoolInfo";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", STORAGE_POOL_PARAMS_INFO)
    };

    public IBMSVCQueryAllStoragePoolCommand() {
        addArgument("svcinfo lsmdiskgrp -delim : -nohdr");
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setSuccess(false);
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    void beforeProcessing() {
        results = new IBMSVCQueryAllStoragePoolResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        
        switch (spec.getPropertyName()) {

            case STORAGE_POOL_PARAMS_INFO:
        
                String[] poolData = capturedStrings.get(0).split(":");
                
                String poolId = poolData[0];
                String poolName = poolData[1];
                String poolStatus = poolData[2];
                String totalCapacity = poolData[5];
                String freeCapacity = poolData[7];
                String poolSubscribedCapacity = poolData[8];
                
                StoragePool storPool = new StoragePool();
                storPool.setNativeId(poolId);
                storPool.setPoolName(poolName);
                storPool.setTotalCapacity(IBMSVCDriverUtils.extractFloat(totalCapacity));
                storPool.setFreeCapacity(IBMSVCDriverUtils.extractFloat(freeCapacity));
                storPool.setSubscribedCapacity(IBMSVCDriverUtils.extractFloat(poolSubscribedCapacity));
                if (poolStatus.equals("excluded")) {
                    storPool.setOperationalStatus(PoolOperationalStatus.NOTREADY);
                } else if (poolStatus.equals("online")) {
                    storPool.setOperationalStatus(PoolOperationalStatus.READY);
                } else {
                    storPool.setOperationalStatus(PoolOperationalStatus.NOTREADY);
                }
                storPool.setDisplayName(poolName);
                storPool.setDeviceLabel(poolName);
                results.addStoragePool(storPool);
                results.setSuccess(true);
                break;
        }
    }
}
