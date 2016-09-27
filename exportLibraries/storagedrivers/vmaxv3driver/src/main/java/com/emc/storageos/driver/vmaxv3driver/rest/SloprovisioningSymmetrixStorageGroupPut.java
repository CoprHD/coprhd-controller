package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RequestType;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by gang on 9/27/16.
 */
public class SloprovisioningSymmetrixStorageGroupPut extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixStorageGroupPut.class);

    private String symmetrixId;
    private String storageGroupId;
    private String body;

    public SloprovisioningSymmetrixStorageGroupPut(String symmetrixId, String storageGroupId, String body) {
        this.symmetrixId = symmetrixId;
        this.storageGroupId = storageGroupId;
        this.body = body;
    }

    @Override
    public Object perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_STORAGE_GROUP_ID,
            this.symmetrixId, this.storageGroupId);
        String responseBody = client.request(path, RequestType.PUT, this.body);
        Boolean result = parseRestResult(responseBody);
        return result;
    }

    /**
     * Parse the REST response below and return the operation result:
     *
     * {
     *   "success": true
     * }
     *
     * @param responseBody
     */
    private Boolean parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        Boolean success = root.get("success").getAsBoolean();
        if (!success) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        return success;
    }
}