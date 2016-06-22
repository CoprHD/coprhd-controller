package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.utils.rest.HttpRestClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import exceptions.Vmaxv3RestCallException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gang on 6/22/16.
 */
public class ListArray {

    private String sloprovisioning_symmetrix = "/univmax/restapi/sloprovisioning/symmetrix";

    public List<String> execute(HttpRestClient client) {
        String path = sloprovisioning_symmetrix;
        String responseBody = client.request(path);
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
        JsonParser parser = new JsonParser();
        JsonElement json = parser.parse(body);
        JsonObject root = json.getAsJsonObject();
        Boolean success = root.get("success").getAsBoolean();
        if(!success) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        List<String> result = new ArrayList<>();
        JsonArray list = root.getAsJsonArray("symmetrixId");
        while(list.iterator().hasNext()) {
            String arrayId = list.iterator().next().getAsString();
            result.add(arrayId);
        }
        return result;
    }
}
