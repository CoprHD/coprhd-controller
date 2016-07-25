/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.discovery;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;
import com.emc.storageos.driver.vmaxv3driver.registry.RegistryHandler;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixGet;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.Symmetrix;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
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

    private StorageSystem storageSystem;

    @Override
    public boolean isMatch(String name, Object... parameters) {
        if ("discoverStorageSystem".equals(name)) {
            this.storageSystem = (StorageSystem) parameters[0];
            logger.debug("Storage system discovery: nativeId={}, serialNumber={}, ipAddress={}, portNumber={}, " +
                "userName={}, password={}, protocols={}, ", storageSystem.getNativeId(),
                storageSystem.getSerialNumber(), storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword(), storageSystem.getProtocols());
            this.setClient(this.storageSystem);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<String, Object> execute() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. Get the storage system information and set the discovery info into the storage system bean.
            String arrayId = this.storageSystem.getSerialNumber();
            Symmetrix bean = new SloprovisioningSymmetrixGet(arrayId).perform(this.getClient());
            this.storageSystem.setIsSupportedVersion(true);
            this.storageSystem.setSystemName(arrayId);
            this.storageSystem.setNativeId(arrayId);
            this.storageSystem.setFirmwareVersion(bean.getUcode());
            this.storageSystem.setModel(bean.getModel());
            this.storageSystem.setProvisioningType(StorageSystem.SupportedProvisioningType.THIN);
            this.storageSystem.setSerialNumber(bean.getSymmetrixId());
            if (this.storageSystem.getDeviceLabel() == null) {
                this.storageSystem.setDeviceLabel(bean.getSymmetrixId());
            }
            // 2. Save/update the storage system access information into the Registry for later operations.
            RestClient client = this.getClient();
            new RegistryHandler(this.getRegistry()).setAccessInfo(arrayId, client.getScheme(),
                client.getHost(), client.getPort(), client.getUser(), client.getPassword());
            // 3. Return result.
            result.put("success", true);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
