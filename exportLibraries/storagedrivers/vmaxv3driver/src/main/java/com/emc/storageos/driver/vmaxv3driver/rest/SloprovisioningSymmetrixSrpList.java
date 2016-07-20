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
 * Created by gang on 6/23/16.
 */
public class SloprovisioningSymmetrixSrpList extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixSrpList.class);

    private String symmetrixId;

    public SloprovisioningSymmetrixSrpList(String symmetrixId) {
        this.symmetrixId = symmetrixId;
    }

    @Override
    public List<String> perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_SRP, this.symmetrixId);
        String responseBody = client.request(path);
        List<String> srpIds = parseRestResult(responseBody);
        return srpIds;
    }

    /**
     * Parse the REST response below and return the array list:
     *
     * {
     * "srpId": [
     * "SRP_0x102",
     * "SRP_1"
     * ],
     * "success": true,
     * "num_of_srps": 2
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
        JsonArray list = root.getAsJsonArray("srpId");
        Iterator<JsonElement> it = list.iterator();
        while (it.hasNext()) {
            result.add(it.next().getAsString());
        }
        return result;
    }
}
