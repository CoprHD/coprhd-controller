/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.SymmetrixPort;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.SymmetrixPortFc;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.SymmetrixPortIscsi;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.SymmetrixPortRdf;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API call to get given port information.
 *
 * Created by gang on 6/24/16.
 */
public class SloprovisioningSymmetrixDirectorPortGet extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixDirectorPortGet.class);

    private static Map<String, Class<? extends SymmetrixPort>> typeClassMap = new HashMap<>();

    static {
        typeClassMap.put("FibreChannel (563)", SymmetrixPortFc.class);
        typeClassMap.put("GigE", SymmetrixPortIscsi.class);
        typeClassMap.put("RDF-BI-DIR", SymmetrixPortRdf.class);
    }


    private String symmetrixId;
    private String directorId;
    private String portId;

    public SloprovisioningSymmetrixDirectorPortGet(String symmetrixId, String directorId, String portId) {
        this.symmetrixId = symmetrixId;
        this.directorId = directorId;
        this.portId = portId;
    }

    @Override
    public SymmetrixPort perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_DIRECTOR_PORT_ID,
                this.symmetrixId, this.directorId, this.portId);
        String responseBody = client.request(path);
        SymmetrixPort result = parseRestResult(responseBody);
        return result;
    }

    /**
     * Parse the REST response below and return the Symmetrix instance. Note that current 3 type storage ports
     * is needed by SBSDK: "FC", "iSCSI" and "RDF". For other types, this method returns null for outer invoker
     * to ignore.
     *
     * @param responseBody The given response body in JSON format.
     * @return The storage port instance or null if it's not the required port type.
     */
    private SymmetrixPort parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        Boolean success = root.get("success").getAsBoolean();
        if (!success) {
            throw new Vmaxv3RestCallException(root.get("message").getAsString());
        }
        String type = root.getAsJsonArray("symmetrixPort").get(0).getAsJsonObject().get("type").getAsString();
        SymmetrixPort bean = null;
        if (typeClassMap.get(type) != null) {
            bean = new Gson().fromJson(root.getAsJsonArray("symmetrixPort").get(0), typeClassMap.get(type));
        } else {
            logger.debug("The type of this bean is unsupported: {}.", type);
        }
        return bean;
    }
}
