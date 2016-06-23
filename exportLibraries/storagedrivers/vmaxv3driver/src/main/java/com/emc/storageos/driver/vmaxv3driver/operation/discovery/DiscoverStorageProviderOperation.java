/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.driver.vmaxv3driver.operation.discovery;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;
import com.emc.storageos.driver.vmaxv3driver.rest.GetVersion;
import com.emc.storageos.driver.vmaxv3driver.rest.ListArray;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of "DiscoverStorageProvider" operation.
 *
 * Created by gang on 6/22/16.
 */
public class DiscoverStorageProviderOperation extends OperationImpl {

    private static final Logger logger = LoggerFactory.getLogger(DiscoverStorageSystemOperation.class);

    private StorageProvider storageProvider;
    private List<StorageSystem> storageSystems;

    @Override
    public boolean isMatch(String name, Object... parameters) {
        if("discoverStorageSystem".equals(name)) {
            this.storageProvider = (StorageProvider)parameters[0];
            this.storageSystems = (List<StorageSystem>)parameters[1];
            this.setClient(this.storageProvider);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Perform the storage provider discovery operation. All the discovery information
     * will be set into the "storageProvider" and "storageSystems" instances.
     *
     * @return A map indicates if the operation succeeds or fails.
     */
    @Override
    public Map<String, Object> execute() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> arrayIds = new ListArray().execute(this.getClient());
            for(String arrayId : arrayIds) {
                StorageSystem storageSystem = new StorageSystem();
                storageSystem.setNativeId(arrayId);
                storageSystem.setIpAddress(this.storageProvider.getProviderHost());
                storageSystem.setPortNumber(this.storageProvider.getPortNumber());
                storageSystem.setUsername(this.storageProvider.getUsername());
                storageSystem.setPassword(this.storageProvider.getPassword());
                List<String> protocols = new ArrayList<>();
                protocols.add(this.storageProvider.getUseSSL() ? "https" : "http");
                storageSystem.setProtocols(protocols);
                this.storageSystems.add(storageSystem);
            }
            String version = new GetVersion().execute(this.getClient());
            this.storageProvider.setIsSupportedVersion((version.compareTo("V8.2.0.0") >= 0));
            result.put("success", true);
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
