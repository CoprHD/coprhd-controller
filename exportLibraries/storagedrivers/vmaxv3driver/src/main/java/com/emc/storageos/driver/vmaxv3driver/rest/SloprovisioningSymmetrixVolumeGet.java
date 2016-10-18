/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.rest.response.Volume;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query volume details.
 *
 * Created by gang on 9/26/16.
 */
public class SloprovisioningSymmetrixVolumeGet extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixVolumeGet.class);

    private String symmetrixId;
    private String volumeId;

    public SloprovisioningSymmetrixVolumeGet(String symmetrixId, String volumeId) {
        this.symmetrixId = symmetrixId;
        this.volumeId = volumeId;
    }

    @Override
    public Object perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_VOLUME,
            this.symmetrixId, this.volumeId);
        logger.info("SloprovisioningSymmetrixVolumeGet request path = {}", path);
        String responseBody = client.request(path);
        logger.info("SloprovisioningSymmetrixVolumeGet response body = {}", responseBody);
        Volume result = parseRestResult(responseBody);
        return result;
    }

    /**
     * Parse the REST response below and return the Volume instance:
     *
     * {
     *   "volume": [
     *     {
     *       "pinned": false,
     *       "physical_name": "",
     *       "symmetrixPortKey": [
     *         {
     *           "directorId": "FA-4D",
     *           "portId": "33"
     *         }
     *       ],
     *       "allocated_percent": 100,
     *       "emulation": "FBA",
     *       "num_of_front_end_paths": 1,
     *       "type": "Int+TDEV",
     *       "cap_cyl": 3,
     *       "ssid": "FFFF",
     *       "volume_identifier": "N/A",
     *       "wwn": "60000970000196801612533030334539",
     *       "cap_gb": 0.01,
     *       "reserved": false,
     *       "encapsulated": false,
     *       "num_of_storage_groups": 0,
     *       "volumeId": "003E9",
     *       "cap_mb": 6.0,
     *       "status": "Ready"
     *     }
     *   ],
     *   "success": true
     * }
     *
     * @param responseBody
     * @return
     */
    private Volume parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        if (root.get("success") == null || !root.get("success").getAsBoolean()) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        Volume bean = new Gson().fromJson(root.getAsJsonArray("volume").get(0), Volume.class);
        logger.debug("Parsed bean = {}", bean);
        return bean;
    }
}
