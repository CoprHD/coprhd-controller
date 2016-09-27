/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Query StorageGroup list by given name prefix.
 *
 * Created by gang on 9/27/16.
 */
public class SloprovisioningSymmetrixStorageGroupListByName extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixStorageGroupListByName.class);

    private String symmetrixId;
    private String namePrefix;

    public SloprovisioningSymmetrixStorageGroupListByName(String symmetrixId, String namePrefix) {
        this.symmetrixId = symmetrixId;
        this.namePrefix = namePrefix;
    }

    @Override
    public Object perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_STORAGE_GROUP_LIST_BY_NAME,
            this.symmetrixId, this.namePrefix);
        String responseBody = client.request(path);
        List<String> storageGroupIds = parseRestResult(responseBody);
        return storageGroupIds;
    }

    /**
     * Parse the REST response below and return the array list:
     *
     * {
     *   "success": true,
     *   "num_of_storage_groups": 5,
     *   "storageGroupId": [
     *     "test_vmaxv3_001",
     *     "test_vmaxv3_002",
     *     "test_vmaxv3_003",
     *     "test_vmaxv3_003_1",
     *     "test_vmaxv3_003_2"
     *   ]
     * } or:
     * {
     *   "success": true,
     *   "message": "No Storage Groups Found"
     * }
     *
     * @param responseBody
     */
    private List<String> parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        Boolean success = root.get("success").getAsBoolean();
        if (!success) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        List<String> result = new ArrayList<>();
        JsonArray list = root.getAsJsonArray("storageGroupId");
        Iterator<JsonElement> it = list.iterator();
        while (it.hasNext()) {
            result.add(it.next().getAsString());
        }
        return result;
    }
}
