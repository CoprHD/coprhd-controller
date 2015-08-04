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
package com.emc.storageos.xtremio.restapi;

import java.net.URI;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.sun.jersey.api.client.Client;

public class XtremIOClientFactory extends RestClientFactory {

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username,
            String password, Client client) {
        //We will first create a version 2 client and check for version 2 api. If yes, return the version api client.
        //Otherwise create a version 1 client and return it.
        XtremIOClient version2Client = new XtremIOV2Client(endpoint, username, password, client);
        if(version2Client.isVersion2()) {
            return version2Client;
        } else {
            version2Client = null;
            return new XtremIOV1Client(endpoint, username, password, client);
        }
    }
}
