package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.rest.response.Volume;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RequestType;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by gang on 9/27/16.
 */
public class SloprovisioningSymmetrixVolumeDelete extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixVolumeDelete.class);

    private String symmetrixId;
    private String volumeId;

    public SloprovisioningSymmetrixVolumeDelete(String symmetrixId, String volumeId) {
        this.symmetrixId = symmetrixId;
        this.volumeId = volumeId;
    }

    @Override
    public Object perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_VOLUME,
            this.symmetrixId, this.volumeId);
        String responseBody = client.request(path, RequestType.DELETE);
        Boolean result = parseRestResult(responseBody);
        return result;
    }

    /**
     * Parse the REST response below and return the operation result:
     *
     * {"protoVersion":{"protocol":"HTTP","major":1,"minor":1},"statusCode":204,"reasonPhrase":"No Content"}
     *
     * @param responseBody
     */
    private Boolean parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        if (root.get("statusCode").getAsInt() != 204) {
            throw new Vmaxv3RestCallException(root.get("reasonPhrase").getAsString());
        }
        return Boolean.TRUE;
    }
}
