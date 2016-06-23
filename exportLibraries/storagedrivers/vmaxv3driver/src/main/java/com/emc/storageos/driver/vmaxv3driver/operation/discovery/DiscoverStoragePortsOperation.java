/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.discovery;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;

import java.util.Map;

/**
 * Implementation of "DiscoverStoragePorts" operation.
 * 
 * Created by gang on 6/21/16.
 */
public class DiscoverStoragePortsOperation extends OperationImpl {
    @Override
    public boolean isMatch(String name, Object... parameters) {
        return "discoverStoragePorts".equals(name);
    }

    @Override
    public Map<String, Object> execute() {
        return null;
    }
}
