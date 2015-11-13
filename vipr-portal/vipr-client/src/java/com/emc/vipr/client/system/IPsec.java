/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.vipr.client.system;

import com.emc.storageos.model.ipsec.IPsecStatus;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.system.impl.PathConstants;

/**
 * IPsec relevant APIs
 */
public class IPsec {

    private RestClient client;

    public IPsec(RestClient client) {
        this.client = client;
    }

    /**
     * Rotate ipsec key for the entire system
     * <p>
     * API Call: <tt>POST /ipsec</tt>
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
        return client.get(IPsecStatus.class, PathConstants.IPSEC_KEY_URL);
    }
}
