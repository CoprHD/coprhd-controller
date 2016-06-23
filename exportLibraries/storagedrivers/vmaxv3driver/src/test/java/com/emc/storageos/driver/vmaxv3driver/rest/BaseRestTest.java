package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.utils.rest.HttpRestClient;

/**
 * Created by gang on 6/23/16.
 */
public abstract class BaseRestTest {

    protected HttpRestClient getClient() {
        // new HttpRestClient("https", "lgloc227.lss.emc.com", 8443, "smc", "smc");
        return new HttpRestClient("https", "lglw7150.lss.emc.com", 8443, "smc", "smc");
    }

    protected String getDefaultArrayId() {
        // "000196801468";
        return "000196801612";
    }
}
