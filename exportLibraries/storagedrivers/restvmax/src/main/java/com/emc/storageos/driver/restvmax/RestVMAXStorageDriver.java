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
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.jersey.api.client.ClientResponse;
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
        // initialize task
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discover-storage-provider", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        for (StorageVolume volume : volumes) {
            // post
            postCreateVolume(volume);
            // send getRequest to get volume id
            ClientResponse getResponse = doGetVolumeId(volume);
            // use volume id get from last function ,and then call get request again
            ClientResponse getVolumeResponse = doGetVolumeDetails(volume, getResponse);
            // set value into volume
            String getVolumeResponseString = getVolumeResponse.getEntity(String.class);
            GetVolumeResultType getVolumeType = (new Gson().fromJson(sanitize(getVolumeResponseString), GetVolumeResultType.class));
            VolumeType typeArray = getVolumeType.getVolume()[0];
            volume.setAllocatedCapacity(new Long(0));
            volume.setProvisionedCapacity((new Double(typeArray.getCap_mb() * 1024 * 1024)).longValue());
            volume.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
            volume.setNativeId(typeArray.getVolumeId());
            volume.setWwn(typeArray.getWwn());
        }
        task.setStatus(DriverTask.TaskStatus.READY);
        return task;
    }

    private void postCreateVolume(StorageVolume volume) {
        // do post http://10.247.97.150:8443/univmax/restapi/sloprovisoning/symmetrix/{symmetrixid}/storagegroup
        // create a volume with storageGroup
        VolumeAttributeType aType = new VolumeAttributeType(CapacityUnitType.MB,
                String.valueOf(volume.getRequestedCapacity() / (1024 * 1024)));
        SloBasedStorageGroupParamType[] sloType = new SloBasedStorageGroupParamType[1];
        for (int i = 0; i < sloType.length; i++) {
            sloType[i] = new SloBasedStorageGroupParamType(new Long(1), aType);
        }
        CreateStorageGroupParamType groupParamType = new CreateStorageGroupParamType(volume.getStorageGroupId());
        groupParamType.setSrpId(volume.getStoragePoolId()).setSloBasedStorageGroupParam(sloType);
        String restParam = groupParamType.toJsonString();
        String path = String.format(RestAPI.URI_HTTPS + RestVmaxEndpoint.SLOPROVISIONING_SYMMETRIX__STORAGEGROUP,
                restAPI.getHost(), restAPI.getPort(), restAPI.getPathVendorPrefix(), volume.getStorageSystemId());
        ClientResponse postResponse = RestAPI.post(path, restParam, false, BackendType.VMAX,
                restAPI.getUser(), restAPI.getPassword());
        String respnseString = postResponse.getEntity(String.class);
        GenericResultType type = (new Gson().fromJson(sanitize(respnseString), GenericResultType.class));
        _log.info("post response: " + type.getSuccess() + "post message: " + type.getMessage());
    }

    private ClientResponse doGetVolumeId(StorageVolume volume) {
        String getPath = String.format(RestAPI.URI_HTTPS + RestVmaxEndpoint.SLOPROVISIONING_SYMMETRIX__VOLUME_QUERY,
                restAPI.getHost(), restAPI.getPort(), restAPI.getPathVendorPrefix(), volume.getStorageSystemId(),
                volume.getStorageGroupId());
        ClientResponse getResponse = RestAPI.get(getPath, false, BackendType.VMAX,
                restAPI.getUser(), restAPI.getPassword());
        return getResponse;
    }

    private ClientResponse doGetVolumeDetails (StorageVolume volume, ClientResponse getResponse) {
        String getResponseString = getResponse.getEntity(String.class);
        IteratorType<VolumesListType> iType = new Gson().fromJson(sanitize(getResponseString),
                new TypeToken<IteratorType<VolumesListType>>() {
                }.getType());
        ResultListType<VolumesListType> resultList = iType.getResultList();
        String volumeId = resultList.getResult()[0].getVolumeId();
        String getVolumePath = String.format(RestAPI.URI_HTTPS + RestVmaxEndpoint.SLOPROVISIONING_SYMMETRIX__VOLUME_ID,
                restAPI.getHost(), restAPI.getPort(), restAPI.getPathVendorPrefix(), volume.getStorageSystemId(),
                volumeId);
        ClientResponse getVolumeResponse = RestAPI.get(getVolumePath, false, BackendType.VMAX,
                restAPI.getUser(), restAPI.getPassword());
        return getVolumeResponse;
    }

    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        restAPI = new RestAPI(storageProvider.getProviderHost(), storageProvider.getPortNumber(),
                storageProvider.getUsername(), storageProvider.getPassword(), BackendType.VMAX.getPathVendorPrefix());
        String path = String.format(RestAPI.URI_HTTPS + RestVmaxEndpoint.SYSTEM__VERSION,
                restAPI.getHost(), restAPI.getPort(), restAPI.getPathVendorPrefix());
        _log.info("path {}", path);
        // get request and covert Json object into
        ClientResponse response = RestAPI.get(path, false, BackendType.VMAX,
                restAPI.getUser(), restAPI.getPassword());
        String respnseString = response.getEntity(String.class);
        GetVersionResultType type = (new Gson().fromJson(sanitize(respnseString), GetVersionResultType.class));
        storageProvider.setProviderVersion(type.getVersion());
        storageProvider.setIsSupportedVersion(true);
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s------version:%s", driverName, "discover-storage-provider",
                UUID.randomUUID().toString(), "type.getVersion()");
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);
        String msg = String.format("%s: %s\nversion: %s", driverName, "discover-storage-provider", type.getVersion());
        task.setMessage(msg);
        return task;
    }

}
