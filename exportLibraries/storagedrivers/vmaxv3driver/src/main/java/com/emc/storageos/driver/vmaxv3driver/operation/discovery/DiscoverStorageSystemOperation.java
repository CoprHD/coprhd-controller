/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.discovery;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of "DiscoverStorageSystem" operation.
 *
 * Created by gang on 6/21/16.
 */
public class DiscoverStorageSystemOperation extends OperationImpl {

    private static final Logger logger = LoggerFactory.getLogger(DiscoverStorageSystemOperation.class);

    private StorageSystem storageSystemInput;

    private String sloprovisioning_symmetrix = "/univmax/restapi/sloprovisioning/symmetrix/%1";

    @Override
    public boolean isMatch(String name, Object... parameters) {
        if ("discoverStorageSystem".equals(name)) {
            this.storageSystemInput = (StorageSystem) parameters[0];
            this.setClient(this.storageSystemInput);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<String, Object> execute() {
        String path = String.format(this.sloprovisioning_symmetrix, this.storageSystemInput.getNativeId());
        Map<String, Object> result = new HashMap<>();
        try {
            String responseBody = this.getClient().request(path);

            result.put("success", true);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
