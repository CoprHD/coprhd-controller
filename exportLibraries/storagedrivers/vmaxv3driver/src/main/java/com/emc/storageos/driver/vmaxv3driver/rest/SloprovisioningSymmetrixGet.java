/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.Symmetrix;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * REST API call to get given storage array information.
 *
 * Created by gang on 6/23/16.
 */
public class SloprovisioningSymmetrixGet extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixGet.class);

    private String symmetrixId;

    public SloprovisioningSymmetrixGet(String symmetrixId) {
        this.symmetrixId = symmetrixId;
    }

    @Override
    public Symmetrix perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_ID, this.symmetrixId);
        String responseBody = client.request(path);
        Symmetrix result = parseRestResult(responseBody);
        return result;
    }

    /**
     * Parse the REST response below and return the Symmetrix instance:
     *
     * {
     * "symmetrix": [
     * {
     * "symmetrixId": "000196801612",
     * "sloCompliance": {
     * "slo_marginal": 0,
     * "slo_stable": 0,
     * "slo_critical": 0
     * },
     * "model": "VMAX100K",
     * "ucode": "5977.802.781",
     * "device_count": 2419,
     * "local": true,
     * "virtualCapacity": {
     * "used_capacity_gb": 420.71,
     * "total_capacity_gb": 76102.03
     * }
     * }
     * ],
     * "success": true
     * }
     * 
     * @param responseBody
     * @return
     */
    private Symmetrix parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        Boolean success = root.get("success").getAsBoolean();
        if (!success) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        Symmetrix bean = new Gson().fromJson(root.getAsJsonArray("symmetrix").get(0), Symmetrix.class);
        logger.debug("Parsed bean = {}", bean);
        return bean;
    }
}
