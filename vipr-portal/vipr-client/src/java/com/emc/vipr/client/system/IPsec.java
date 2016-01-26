/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import com.emc.storageos.model.ipsec.IPsecStatus;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.system.impl.PathConstants;

import javax.ws.rs.core.UriBuilder;

import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;

/**
 * IPsec relevant APIs
 */
public class IPsec {

    private RestClient client;
    private static final String STATUS_PARAM = "status";

    public IPsec(RestClient client) {
        this.client = client;
    }

    /**
     * Rotate ipsec key for the entire system
     * <p>
     * API Call: <tt>POST /ipsec/key</tt>
     *
     * @return VdcConfigVersion
     */
    public String rotateIpsecKey() {
        return client.post(String.class, PathConstants.IPSEC_KEY_URL);
    }

    /**
     * Check ipsec status against entire system
     * <p>
     * API Call: <tt>GET /ipsec</tt>
     *
     * @return the ipsec status of entire system.
     */
    public IPsecStatus checkStatus() {
        return client.get(IPsecStatus.class, PathConstants.IPSEC_URL);
    }

    /**
     * change ipsec status, valid values: enabled, disabled
     * <p>
     * API Call: <tt>POST /ipsec</tt>
     *
     * @return the ipsec status.
     */
    public String changeStatus(String status) {
        UriBuilder builder = client.uriBuilder(PathConstants.IPSEC_URL);
        addQueryParam(builder, STATUS_PARAM, status);
        return client.postURI(String.class, builder.build());
    }
}
