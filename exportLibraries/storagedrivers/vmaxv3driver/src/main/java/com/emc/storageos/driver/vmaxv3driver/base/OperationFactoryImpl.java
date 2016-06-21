package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.driver.vmaxv3driver.operations.discovery.DiscoverStoragePoolsOperation;
import com.emc.storageos.driver.vmaxv3driver.operations.discovery.DiscoverStoragePortsOperation;
import com.emc.storageos.driver.vmaxv3driver.operations.discovery.DiscoverStorageSystemOperation;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gang on 6/21/16.
 */
public class OperationFactoryImpl implements OperationFactory {

    private List<Operation> operations = new ArrayList<>();

    public OperationFactoryImpl() {
        operations.add(new DiscoverStorageSystemOperation());
        operations.add(new DiscoverStoragePoolsOperation());
        operations.add(new DiscoverStoragePortsOperation());
    }

    @Override
    public Operation getInstance(Registry registry, LockManager lockManager, String name, Object... parameters) {
        for(Operation operation : this.operations) {
            if(operation.isMatch(name, parameters)) {
                operation.setRegistry(registry);
                operation.setLockManager(lockManager);
                return operation;
            }
        }
        return null;
    }
}
