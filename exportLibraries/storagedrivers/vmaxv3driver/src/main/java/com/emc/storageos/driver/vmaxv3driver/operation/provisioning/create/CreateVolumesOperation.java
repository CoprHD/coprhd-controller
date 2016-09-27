/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.provisioning.create;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Created by gang on 9/19/16.
 */
public class CreateVolumesOperation extends OperationImpl {

    private static final Logger logger = LoggerFactory.getLogger(CreateVolumesOperation.class);

    protected List<StorageVolume> volumes;
    protected StorageCapabilities capabilities;

    @Override
    public boolean isMatch(String name, Object... parameters) {
        if ("createVolumes".equals(name)) {
            this.volumes = (List<StorageVolume>) parameters[0];
            this.capabilities = (StorageCapabilities) parameters[1];
            if(this.volumes == null || this.volumes.isEmpty()) {
                throw new IllegalArgumentException("The given 'volumes' argument is empty.");
            }
            this.setClient(this.getRegistry(), this.volumes.get(0).getStorageSystemId());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<String, Object> execute() {

        // 1. Compose the volume arguments needed by the REST API call.



        return null;
    }
}
