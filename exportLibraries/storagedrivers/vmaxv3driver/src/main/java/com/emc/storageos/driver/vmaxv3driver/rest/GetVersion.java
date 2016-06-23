/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.JsonObject;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;

/**
 * REST API call to get version information.
 *
 * Created by gang on 6/22/16.
 */
public class GetVersion extends RestActionImpl {

    @Override
    public String execute(RestClient client) {
        String responseBody = client.request(Vmaxv3Constants.REST_PATH_SYSTEM_VERSION);
        String version = parseRestResult(responseBody);
        return version;
    }

    /**
     * Parse the REST result below, and return the version string.
     *
     * {
     * "version": "V8.2.0.5"
     * }
     *
     * @param body
     */
    private String parseRestResult(String body) {
        JsonObject root = this.parseResponse(body);
        String version = root.get("version") != null ? root.get("version").getAsString() : null;
        if (version == null) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        return version;
    }
}
