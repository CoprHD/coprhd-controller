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

    private String nativeArrayId;

    public SloprovisioningSymmetrixGet(String nativeArrayId) {
        this.nativeArrayId = nativeArrayId;
    }

    @Override
    public Symmetrix perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.REST_PATH_SLOPROVISIONING_SYMMETRIX_GET, this.nativeArrayId);
        String responseBody = client.request(path);
        Symmetrix result = parseRestResult(responseBody);
        return result;
    }

    private Symmetrix parseRestResult(String responseBody) {
        JsonObject root = this.parseResponse(responseBody);
        Boolean success = root.get("success").getAsBoolean();
        if (!success) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        // Parse the inside "symmetrix" instance.
        Symmetrix bean = new Gson().fromJson(root.getAsJsonArray("symmetrix").get(0), Symmetrix.class);
        logger.debug("Symmetrix bean = {}", bean);
        return bean;
    }
}
