/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.provisioning.create;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixStorageGroupPost;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixVolumeGet;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixVolumeListByStorageGroup;
import com.emc.storageos.driver.vmaxv3driver.rest.request.RequestStorageGroupPost;
import com.emc.storageos.driver.vmaxv3driver.rest.response.Volume;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * As described in the email from "Byrne, Kenneth", the current VMAXV3 APIs do not support
 * create volume(s) directly. Instead, volume(s) must be created within a StorageGroup. In
 * this case, in the creating volume operation implementation here, a StorageGroup with the
 * volume(s) is created instead of creating volume(s) directly.
 *
 * Email subject: "RE: How to create volume with VMAX3 REST API"
 *
 * Created by gang on 9/19/16.
 */
public class CreateVolumesOperation extends OperationImpl {

    private static final Logger logger = LoggerFactory.getLogger(CreateVolumesOperation.class);

    protected List<StorageVolume> volumes;
    protected StorageCapabilities capabilities;
    protected String storageSystemId;

    @Override
    public boolean isMatch(String name, Object... parameters) {
        if ("createVolumes".equals(name)) {
            this.volumes = (List<StorageVolume>) parameters[0];
            this.capabilities = (StorageCapabilities) parameters[1];
            if(this.volumes == null || this.volumes.isEmpty()) {
                throw new IllegalArgumentException("The given 'volumes' argument is empty.");
            }
            this.storageSystemId = this.volumes.get(0).getStorageSystemId();
            this.setClient(this.getRegistry(), this.storageSystemId);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<String, Object> execute() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. Post(create) a StorageGroup which contains the new volumes.
            CreateVolumesRequestBodyParser requestBodyParser = new CreateVolumesRequestBodyParser(
                this.volumes, this.capabilities);
            RequestStorageGroupPost requestBean = requestBodyParser.parse();
            String requestBody = requestBean.getRequestBody();
            Boolean postResult = (Boolean) new SloprovisioningSymmetrixStorageGroupPost(
                this.storageSystemId, requestBody).perform(this.getClient());
            logger.debug("CreateVolumesOperation POST StorageGroup result: {}", postResult);
            // 2. Query the created volumes in the StorageGroup and prepare the returned volumes
            // for the SDK method call.
            List<String> volumeIds = (List<String>)new SloprovisioningSymmetrixVolumeListByStorageGroup(
                this.storageSystemId, requestBean.getStorageGroupId());
            List<Volume> volumes = new ArrayList<>();
            for (String volumeId : volumeIds) {
                Volume volume = (Volume)new SloprovisioningSymmetrixVolumeGet(
                    this.storageSystemId, volumeId).perform(this.getClient());
                volumes.add(volume);
            }
            // 3. Parse the created volumes and update the passed-in-out "volumes" argument.
            new CreateVolumesOperationResultParser(volumes, this.volumes).parse();
            result.put("success", true);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
