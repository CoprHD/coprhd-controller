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
 * REST API call to get the director list of a given array.
 *
 * Created by gang on 6/24/16.
 */
public class SloprovisioningSymmetrixDirectorList extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixDirectorList.class);

    private String symmetrixId;

    public SloprovisioningSymmetrixDirectorList(String symmetrixId) {
        this.symmetrixId = symmetrixId;
    }

    @Override
    public List<String> perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_DIRECTOR, this.symmetrixId);
        String responseBody = client.request(path);
        List<String> directorIds = parseRestResult(responseBody);
        return directorIds;
    }

    /**
     * Parse the REST response below and return the director list:
     *
     * {
     * "num_of_directors": 17,
     * "directorId": [
     * "DF-1C",
     * "DF-2C",
     * "DF-3C",
     * "DF-4C",
     * "ED-1B",
     * "ED-2B",
     * "ED-3B",
     * "ED-4B",
     * "FA-1D",
     * "FA-2D",
     * "FA-3D",
     * "FA-4D",
     * "IM-1A",
     * "IM-2A",
     * "IM-3A",
     * "IM-4A",
     * "SE-1E"
     * ],
     * "success": true
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
        JsonArray list = root.getAsJsonArray("directorId");
        Iterator<JsonElement> it = list.iterator();
        while (it.hasNext()) {
            result.add(it.next().getAsString());
        }
        return result;
    }
}
