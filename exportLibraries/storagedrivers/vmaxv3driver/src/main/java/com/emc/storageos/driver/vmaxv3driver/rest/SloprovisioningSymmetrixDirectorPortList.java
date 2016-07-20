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
 * REST API call to get the port list of a given director.
 *
 * Created by gang on 6/24/16.
 */
public class SloprovisioningSymmetrixDirectorPortList extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixDirectorList.class);

    private String symmetrixId;
    private String directorId;

    public SloprovisioningSymmetrixDirectorPortList(String symmetrixId, String directorId) {
        this.symmetrixId = symmetrixId;
        this.directorId = directorId;
    }

    @Override
    public List<String> perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_DIRECTOR_PORT, this.symmetrixId, this.directorId);
        String responseBody = client.request(path);
        List<String> portIds = parseRestResult(responseBody);
        return portIds;
    }

    /**
     * Parse the REST response below and return the port list:
     *
     * {
     * "symmetrixPortKey": [
     * {
     * "directorId": "DF-1C",
     * "portId": "12"
     * },
     * {
     * "directorId": "DF-1C",
     * "portId": "13"
     * },
     * {
     * "directorId": "DF-1C",
     * "portId": "16"
     * },
     * {
     * "directorId": "DF-1C",
     * "portId": "17"
     * }
     * ],
     * "success": true,
     * "num_of_ports": 4
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
        JsonArray list = root.getAsJsonArray("symmetrixPortKey");
        Iterator<JsonElement> it = list.iterator();
        while (it.hasNext()) {
            result.add(it.next().getAsJsonObject().get("portId").getAsString());
        }
        return result;
    }
}
