/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax;

import com.emc.storageos.driver.restvmax.rest.BackendType;
import com.emc.storageos.driver.restvmax.rest.RestAPI;
import com.emc.storageos.driver.restvmax.vmax.RestVmaxEndpoint;
import com.emc.storageos.driver.restvmax.vmax.type.*;
import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.protocol.HttpService;
import com.google.json.JsonSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.json.JsonSanitizer.*;

import java.util.List;
import java.util.UUID;

public class RestVMAXStorageDriver extends DefaultStorageDriver {
    static RestAPI restAPI;
    private static final Logger _log = LoggerFactory.getLogger(RestVMAXStorageDriver.class);

    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        // "num_of_vols": current value set to 1. TODO

        String restParam = "";

        for (StorageVolume volume : volumes) {
            VolumeAttributeType aType = new VolumeAttributeType(CapacityUnitType.MB,
                    String.valueOf(volume.getRequestedCapacity() / (1024 * 1024)));
            SloBasedStorageGroupParamType[] sloType = new SloBasedStorageGroupParamType[1];
            for(int i = 0; i < sloType.length; i++) {
                sloType[i] = new SloBasedStorageGroupParamType(new Long(1), aType);
            }
            CreateStorageGroupParamType groupParamType = new CreateStorageGroupParamType(volume.getStorageGroupId());
            groupParamType.setSrpId(volume.getStoragePoolId()).setSloBasedStorageGroupParam(sloType);

            restParam = groupParamType.toJsonString();
            /*
            String path = String.format(RestAPI.URI_HTTPS + RestVmaxEndpoint.SLOPROVISIONING_SYMMETRIX__STORAGEGROUP,
                    restAPI.getHost(), restAPI.getPort(), restAPI.getPathVendorPrefix(), volume.getStorageSystemId());
            String getPath = String.format(RestAPI.URI_HTTPS + RestVmaxEndpoint.SLOPROVISIONING_SYMMETRIX__VOLUME_QUERY,
                    restAPI.getHost(), restAPI.getPort(), restAPI.getPathVendorPrefix(), volume.getStorageSystemId(),
                    volume.getStorageGroupId());
            RestAPI.post(path, restParam, false, BackendType.VMAX,
                    restAPI.getUser(), restAPI.getPassword());
            ClientResponse response = RestAPI.get(path, false, BackendType.VMAX,
                    restAPI.getUser(), restAPI.getPassword());
                    */
        }
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discover-storage-provider", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);
        task.setMessage("restParam: " + restParam);
        return task;
    }

    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        restAPI = new RestAPI(storageProvider.getProviderHost(), storageProvider.getPortNumber(),
                storageProvider.getUsername(), storageProvider.getPassword(), BackendType.VMAX.getPathVendorPrefix());
        String path = String.format(RestAPI.URI_HTTPS + RestVmaxEndpoint.SYSTEM__VERSION,
                restAPI.getHost(), restAPI.getPort(), restAPI.getPathVendorPrefix());
        _log.info("path {}", path);
        ClientResponse response = RestAPI.get(path, false, BackendType.VMAX,
                restAPI.getUser(), restAPI.getPassword());
        String respnseString = response.getEntity(String.class);

        GetVersionResultType type = (new Gson().fromJson(sanitize(respnseString), GetVersionResultType.class));
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s------version:%s", driverName, "discover-storage-provider",
                UUID.randomUUID().toString(), "type.getVersion()");
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);
        String msg = String.format("%s: %s\nversion:%s", driverName, "discover-storage-provider", type.getVersion());
        task.setMessage(msg);
        return task;
    }

}
