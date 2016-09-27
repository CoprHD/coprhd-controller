/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.request;

import com.emc.storageos.driver.vmaxv3driver.operation.provisioning.create.CreateStorageGroupRequest;
import com.emc.storageos.driver.vmaxv3driver.rest.request.BaseRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by gang on 9/26/16.
 */
public class StorageGroupPost extends BaseRequest {

    private CreateStorageGroupRequest request;

    public StorageGroupPost(CreateStorageGroupRequest request) {
        this.request = request;
    }

    @Override
    public Map<String, Object> getArguments() {





        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "driver");
        return arguments;
    }
}
