/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.datadomain.restapi;

import com.emc.storageos.services.restutil.RestClientItf;
import com.emc.storageos.services.restutil.RestClientFactory;

import java.net.URI;

/**
 * Created by zeldib on 1/30/14.
 */
public class DataDomainClientFactory extends RestClientFactory {

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username, String password, com.sun.jersey.api.client.Client client) {
        return new DataDomainClient(endpoint, username, password, client);
    }
}
