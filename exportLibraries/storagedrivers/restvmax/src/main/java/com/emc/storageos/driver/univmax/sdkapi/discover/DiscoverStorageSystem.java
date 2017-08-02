/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.sdkapi.discover;

import com.emc.storageos.driver.univmax.DriverDataUtil;
import com.emc.storageos.driver.univmax.DriverUtil;
import com.emc.storageos.driver.univmax.rest.EndPoint;
import com.emc.storageos.driver.univmax.rest.JsonUtil;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.SymmetrixType;
import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class DiscoverStorageSystem {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoverStorageSystem.class);

    public DriverTask discoverStorageSystem(DriverDataUtil driverDataUtil, StorageSystem storageSystem) {
        String taskId = String.format("%s+%s+%s",
                driverDataUtil.getDriverName(), "discoverStorageSystem", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);
        String msg = "Discover storage system: ";

        try {
            RestClient client = driverDataUtil.getRestClientByStorageSystemId(storageSystem.getNativeId());

            String resp = client.getJsonString(RestClient.METHOD.GET,
                    String.format(EndPoint.SLOPROVISIONING84_SYMMETRIX_ID, storageSystem.getNativeId()));

            SymmetrixType symmetrixType = JsonUtil.fromJson(resp, SymmetrixType.class);
            storageSystem.setSerialNumber(symmetrixType.getSymmetrixId());
            storageSystem.setNativeId(symmetrixType.getSymmetrixId());
            storageSystem.setFirmwareVersion(symmetrixType.getModel());
            storageSystem.setModel(symmetrixType.getModel());
            storageSystem.setIsSupportedVersion(symmetrixType.getLocal());

            msg += "success.";
            LOG.info(msg);
            task.setMessage(msg);
            task.setStatus(DriverTask.TaskStatus.READY);
        } catch (Exception e) {
            LOG.error(msg, e);
            task.setMessage(DriverUtil.getStackTrace(e));
        }

        return task;
    }
}
