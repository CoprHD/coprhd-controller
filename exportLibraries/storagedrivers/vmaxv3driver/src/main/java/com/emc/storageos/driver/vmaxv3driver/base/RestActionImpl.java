package com.emc.storageos.driver.vmaxv3driver.base;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Created by gang on 6/23/16.
 */
public abstract class RestActionImpl implements RestAction {
    protected JsonObject parseResponse(String body) {
        JsonParser parser = new JsonParser();
        JsonElement json = parser.parse(body);
        JsonObject root = json.getAsJsonObject();
        return root;
    }
}
