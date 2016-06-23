/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The abstract base implementation of "RestAction" which is the super class of
 * all concrete "RestAction" implementation class.
 *
 * Created by gang on 6/23/16.
 */
public abstract class RestActionImpl implements RestAction {

    /**
     * Parse the JSON response body into JsonObject instance.
     *
     * @param body The JSON format string.
     * @return The parsed JsonObject instance.
     */
    protected JsonObject parseResponse(String body) {
        JsonParser parser = new JsonParser();
        JsonElement json = parser.parse(body);
        JsonObject root = json.getAsJsonObject();
        return root;
    }
}
