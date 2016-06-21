package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

/**
 * Created by gang on 6/21/16.
 */
public interface OperationFactory {

    public Operation getInstance(Registry registry, LockManager lockManager, String name, Object... parameters);
}
