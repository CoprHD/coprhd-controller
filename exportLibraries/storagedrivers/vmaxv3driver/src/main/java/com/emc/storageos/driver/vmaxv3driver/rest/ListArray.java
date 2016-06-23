package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.utils.rest.HttpRestClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exceptions.Vmaxv3RestCallException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by gang on 6/22/16.
 */
public class ListArray extends RestActionImpl {

    @Override
    public List<String> execute(HttpRestClient client) {
        String responseBody = client.request(Vmaxv3Constants.REST_PATH_SLOPROVISIONING_SYMMETRIX);
        List<String> arrayIds = parseRestResult(responseBody);
        return arrayIds;
    }

    /**
     * Parse the REST result below, and set proper values into the "storageProvider" and
     * "storageSystems" instances.
     *
     {
     "symmetrixId": [
     "000196701029",
     "000196701035",
     "000196701343",
     "000196701405",
     "000196800794",
     "000196801468",
     "000196801612",
     "000197000143"
     ],
     "success": true,
     "num_of_symmetrix_arrays": 8
     }
     *
     * @param body
     */
    private List<String> parseRestResult(String body) {
        JsonObject root = this.parseResponse(body);
        Boolean success = root.get("success").getAsBoolean();
        if(!success) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        List<String> result = new ArrayList<>();
        JsonArray list = root.getAsJsonArray("symmetrixId");
        Iterator<JsonElement> it = list.iterator();
        while(it.hasNext()) {
            result.add(it.next().getAsString());
        }
        return result;
    }
}
