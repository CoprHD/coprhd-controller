/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.discovery;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixSrpGet;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixSrpList;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.Srp;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of "DiscoverStoragePools" operation.
 *
 * Created by gang on 6/21/16.
 */
public class DiscoverStoragePoolsOperation extends OperationImpl {

    private static final Logger logger = LoggerFactory.getLogger(DiscoverStoragePoolsOperation.class);

    private StorageSystem storageSystem;
    private List<StoragePool> StoragePools;

    @Override
    public boolean isMatch(String name, Object... parameters) {
        if ("discoverStoragePools".equals(name)) {
            this.storageSystem = (StorageSystem) parameters[0];
            this.StoragePools = (List<StoragePool>) parameters[1];
            if (this.StoragePools == null) {
                this.StoragePools = new ArrayList<>();
            }
            this.setClient(this.storageSystem);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Perform the storage pool discovery operation. All the discovery information
     * will be set into the "StoragePools" instance.
     *
     * @return A map indicates if the operation succeeds or fails.
     */
    @Override
    public Map<String, Object> execute() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. Get storage pool list.
            List<String> srpIds= new SloprovisioningSymmetrixSrpList(this.storageSystem.getNativeId()).perform(this.getClient());
            for (String srpId : srpIds) {
                Srp bean = new SloprovisioningSymmetrixSrpGet(this.storageSystem.getNativeId(), srpId).perform(this.getClient());
                StoragePool storagePool = new StoragePool();
                storagePool.setStorageSystemId(this.storageSystem.getNativeId());
                storagePool.setNativeId(srpId);
                storagePool.setDeviceLabel(srpId);
                storagePool.setDisplayName(srpId);
                // Need to confirm: 1. Required fields to be set, 2. How to get the values of the required fields.
                this.StoragePools.add(storagePool);
            }
            result.put("success", true);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
