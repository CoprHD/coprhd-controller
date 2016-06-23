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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API call to get version information.
 *
 * Created by gang on 6/22/16.
 */
public class SystemVersionGet extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SystemVersionGet.class);

    @Override
    public String perform(RestClient client) {
        String responseBody = client.request(Vmaxv3Constants.RA_SYSTEM_VERSION);
        String version = parseRestResult(responseBody);
        return version;
    }

    /**
     * Parse the REST response below and return the version string:
     * {
     * "version": "V8.2.0.5"
     * }
     * @param responseBody
     * @return
     */
    private String parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        String version = root.get("version") != null ? root.get("version").getAsString() : null;
        if (version == null) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        return version;
    }
}
