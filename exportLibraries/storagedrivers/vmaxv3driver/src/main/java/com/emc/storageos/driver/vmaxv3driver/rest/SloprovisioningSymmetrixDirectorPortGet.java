/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.SymmetrixPort;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API call to get given port information.
 *
 * Created by gang on 6/24/16.
 */
public class SloprovisioningSymmetrixDirectorPortGet extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixDirectorPortGet.class);

    private String symmetrixId;
    private String directorId;
    private String portId;

    public SloprovisioningSymmetrixDirectorPortGet(String symmetrixId, String directorId, String portId) {
        this.symmetrixId = symmetrixId;
        this.directorId = directorId;
        this.portId = portId;
    }

    @Override
    public SymmetrixPort perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_DIRECTOR_PORT_ID,
                this.symmetrixId, this.directorId, this.portId);
        String responseBody = client.request(path);
        SymmetrixPort result = parseRestResult(responseBody);
        return result;
    }

    /**
     * Parse the REST response below and return the Symmetrix instance:
     *
     * {
     * "success": true,
     * "symmetrixPort": [
     * {
     * "port_interface": "C",
     * "director_status": "Online",
     * "num_of_cores": 8,
     * "symmetrixPortKey": {
     * "directorId": "DF-1C",
     * "portId": "12"
     * },
     * "port_status": "ON",
     * "type": "DISK",
     * "num_of_hypers": 1136
     * }
     * ]
     * }
     *
     * @param responseBody
     * @return
     */
    private SymmetrixPort parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        Boolean success = root.get("success").getAsBoolean();
        if (!success) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        SymmetrixPort bean = new Gson().fromJson(root.getAsJsonArray("symmetrixPort").get(0), SymmetrixPort.class);
        logger.debug("Parsed bean = {}", bean);
        return bean;
    }
}
