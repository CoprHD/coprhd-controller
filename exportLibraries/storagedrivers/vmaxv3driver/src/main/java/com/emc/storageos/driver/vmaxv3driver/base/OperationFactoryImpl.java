/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.driver.vmaxv3driver.operation.discovery.DiscoverStoragePoolsOperation;
import com.emc.storageos.driver.vmaxv3driver.operation.discovery.DiscoverStoragePortsOperation;
import com.emc.storageos.driver.vmaxv3driver.operation.discovery.DiscoverStorageProviderOperation;
import com.emc.storageos.driver.vmaxv3driver.operation.discovery.DiscoverStorageSystemOperation;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

import java.util.ArrayList;
import java.util.List;

/**
 * The default "OperationFactory" implementation.
 *
 * Created by gang on 6/21/16.
 */
public class OperationFactoryImpl implements OperationFactory {

    private List<Class<? extends Operation>> operationClasses = new ArrayList<>();

    public OperationFactoryImpl() {
        operationClasses.add(DiscoverStorageProviderOperation.class);
        operationClasses.add(DiscoverStorageSystemOperation.class);
        operationClasses.add(DiscoverStoragePoolsOperation.class);
        operationClasses.add(DiscoverStoragePortsOperation.class);
    }

    @Override
    public Operation getInstance(Registry registry, LockManager lockManager, String name, Object... parameters) {
        for(Class<? extends Operation> clazz : this.operationClasses) {
            try {
                Operation operation = clazz.newInstance();
                if(operation.isMatch(name, parameters)) {
                    operation.setRegistry(registry);
                    operation.setLockManager(lockManager);
                    return operation;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
