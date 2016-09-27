/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * REST API call to get managed array list.
 *
 * Created by gang on 6/22/16.
 */
public class SloprovisioningSymmetrixList extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixList.class);

    @Override
    public List<String> perform(RestClient client) {
        String responseBody = client.request(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX);
        List<String> arrayIds = parseRestResult(responseBody);
        return arrayIds;
    }

    /**
     * Parse the REST response below and return the array list:
     *
     * {
     * "symmetrixId": [
     * "000196701029",
     * "000196701035",
     * "000196701343",
     * "000196701405",
     * "000196800794",
     * "000196801468",
     * "000196801612",
     * "000197000143"
     * ],
     * "success": true,
     * "num_of_symmetrix_arrays": 8
     * }
     *
     * @param responseBody
     */
    private List<String> parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        if (root.get("success") == null || !root.get("success").getAsBoolean()) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        List<String> result = new ArrayList<>();
        JsonArray list = root.getAsJsonArray("symmetrixId");
        Iterator<JsonElement> it = list.iterator();
        while (it.hasNext()) {
            result.add(it.next().getAsString());
        }
        return result;
    }
}
