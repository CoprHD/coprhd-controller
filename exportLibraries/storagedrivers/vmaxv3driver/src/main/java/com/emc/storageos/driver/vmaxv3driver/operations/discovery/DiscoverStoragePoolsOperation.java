package com.emc.storageos.driver.vmaxv3driver.operations.discovery;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;

import java.util.Map;

/**
 * Created by gang on 6/21/16.
 */
public class DiscoverStoragePoolsOperation extends OperationImpl {
    @Override
    public boolean isMatch(String name, Object... parameters) {
        return "discoverStoragePools".equals(name);
    }

    @Override
    public Map<String, Object> execute() {
        return null;
    }
}
