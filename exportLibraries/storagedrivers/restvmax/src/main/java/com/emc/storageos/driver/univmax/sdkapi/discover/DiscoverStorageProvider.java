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
import com.emc.storageos.driver.univmax.rest.type.system.GetSymmetrixResultType;
import com.emc.storageos.driver.univmax.rest.type.system.GetVersionResultType;
import com.emc.storageos.driver.univmax.rest.type.system.ListSymmetrixResultType;
import com.emc.storageos.driver.univmax.rest.type.system.SymmetrixType;
import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiscoverStorageProvider  {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoverStorageProvider.class);

    public DriverTask discoverStorageProvider(DriverDataUtil driverDataUtil, StorageProvider storageProvider,
                                              List<StorageSystem> storageSystems) {
        String taskId = String.format("%s+%s+%s",
                driverDataUtil.getDriverName(), "discover-storage-provider", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        try {
            RestClient restClient = new RestClient(storageProvider.getUseSSL(), storageProvider.getProviderHost(),
                    storageProvider.getPortNumber(), storageProvider.getUsername(), storageProvider.getPassword());

            // storage provider info
            String resp = restClient.getJsonString(EndPoint.SYSTEM_VERSION);
            GetVersionResultType getVersionResultTypeType = JsonUtil.fromJson(resp, GetVersionResultType.class);
            storageProvider.setProviderVersion(getVersionResultTypeType.getVersion());
            storageProvider.setIsSupportedVersion(true);

            // storage system info
            resp = restClient.getJsonString(EndPoint.SYSTEM_SYMMETRIX);
            ListSymmetrixResultType listSymmetrixResultType = JsonUtil.fromJson(resp, ListSymmetrixResultType.class);
            List<SymmetrixType> supportedSymmetrix = new ArrayList<>();
            for (String symmetrix : listSymmetrixResultType.getSymmetrixId()) {
                resp = restClient.getJsonString(EndPoint.SYSTEM_SYMMETRIX + "/" + symmetrix);
                GetSymmetrixResultType getSymmetrixResultType = JsonUtil.fromJson(resp, GetSymmetrixResultType.class);
                if (getSymmetrixResultType.getSymmetrix().length > 1) {
                    throw new InternalError("VMAX RESTful API bug: more than 1 symmetrix for id " + symmetrix);
                }
                SymmetrixType sym = getSymmetrixResultType.getSymmetrix()[0];
                if (sym.getLocal()) {
                    supportedSymmetrix.add(sym);
                } else {
                    LOG.info("Ignoring unsupported remote symmetrix: {}", symmetrix);
                }
            }
            for (SymmetrixType sym : supportedSymmetrix) {
                StorageSystem system = new StorageSystem();
                system.setSystemType(sym.getModel());
                system.setNativeId(sym.getUcode());
                system.setSystemName(sym.getSymmetrixId());
                system.setSerialNumber(sym.getSymmetrixId());
                system.setFirmwareVersion(sym.getModel());

                storageSystems.add(system);
                driverDataUtil.addRestClient(sym.getSymmetrixId(), restClient);
            }
            driverDataUtil.setStorageProvider(storageProvider, storageSystems);

            task.setMessage("Discover storage provider success.");
            task.setStatus(DriverTask.TaskStatus.READY);
        } catch (Exception e) {
            LOG.error("discover storage provider: ", e);
            task.setMessage(DriverUtil.getStackTrace(e));
        }

        return task;
    }
}
