package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.Srp;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API call to get given storage pool information.
 *
 * Created by gang on 6/23/16.
 */
public class SloprovisioningSymmetrixSrpGet extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixSrpGet.class);

    private String symmetrixId;
    private String srpId;

    public SloprovisioningSymmetrixSrpGet(String symmetrixId, String srpId) {
        this.symmetrixId = symmetrixId;
        this.srpId = srpId;
    }

    @Override
    public Srp perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_SRP_ID, this.symmetrixId, this.srpId);
        String responseBody = client.request(path);
        Srp result = parseRestResult(responseBody);
        return result;
    }

    /**
     * Parse the REST response below and return the Srp instance:
     *
     * {
     * "srp": [
     * {
     * "rdfa_dse": true,
     * "num_of_disk_groups": 5,
     * "srpSgDemandId": [
     * "ESXi6_Cluster_612_SG_mized_NONE_SRP_1",
     * "host1export27580_612_SG_mized_NONE_SRP_1",
     * "lglal042lssemccom_612_SG_mized_NONE_SRP_1",
     * "lglal043lssemccom_612_SG_mized_NONE_SRP_1",
     * "Syracuse_612_SG_mized_NONE_SRP_1",
     * "Syracuse_612_SG_NonFast",
     * "Syracuse_612_SG_NonFast_1",
     * "Syracuse_612_SG_NonFast_2",
     * "ViPR_Optimized_NONE_SRP_1_77a3039c-7992-42e8-989f-378336a98d7e",
     * "vplex_0288_0001_cl2_612_SG_mized_NONE_SRP_1"
     * ],
     * "srpId": "SRP_1",
     * "total_allocated_cap_gb": 420.71,
     * "emulation": "FBA",
     * "description": "",
     * "num_of_srp_slo_demands": 2,
     * "total_usable_cap_gb": 76102.03,
     * "total_subscribed_cap_gb": 832.36,
     * "num_of_srp_sg_demands": 10,
     * "total_snapshot_allocated_cap_gb": 0.0,
     * "total_srdf_dse_allocated_cap_gb": 0.0,
     * "reserved_cap_percent": 10,
     * "diskGroupId": [
     * "1",
     * "2",
     * "3",
     * "4",
     * "5"
     * ],
     * "srpSloDemandId": [
     * "<none>",
     * "Optimized"
     * ]
     * }
     * ],
     * "success": true
     * }
     *
     * @param responseBody
     * @return
     */
    private Srp parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        Boolean success = root.get("success").getAsBoolean();
        if (!success) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        Srp bean = new Gson().fromJson(root.getAsJsonArray("srp").get(0), Srp.class);
        logger.debug("Parsed bean = {}", bean);
        return bean;
    }
}
