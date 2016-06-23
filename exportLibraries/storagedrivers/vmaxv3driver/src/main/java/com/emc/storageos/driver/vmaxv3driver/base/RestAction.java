/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;

/**
 * Define the REST API call action.
 *
 * Created by gang on 6/23/16.
 */
public interface RestAction {

    /**
     * Execute the REST API call to VMAX V3 REST API.
     *
     * @param client The REST client instance.
     * @return The result of the REST call, or an thrown "Vmaxv3RestCallException"
     *         instance if error happens.
     */
    public Object execute(RestClient client);
}
