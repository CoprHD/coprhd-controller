/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;

/**
 * The base testing class for all REST API call testing class.
 *
 * Created by gang on 6/23/16.
 */
public abstract class BaseRestTest {

    protected RestClient getClient() {
        // new RestClient("https", "lgloc227.lss.emc.com", 8443, "smc", "smc");
        return new RestClient("https", "lglw7150.lss.emc.com", 8443, "smc", "smc");
    }

    protected String getDefaultArrayId() {
        // "000196801468";
        return "000196801612";
    }

    protected String getDefaultSrpId() {
        return "SRP_1";
    }
}
