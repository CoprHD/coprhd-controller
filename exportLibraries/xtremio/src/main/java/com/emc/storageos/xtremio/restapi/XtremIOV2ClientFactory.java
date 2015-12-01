package com.emc.storageos.xtremio.restapi;

import java.net.URI;

import com.emc.storageos.common.http.RestAPIFactory;

public class XtremIOV2ClientFactory extends RestAPIFactory<XtremIOV2Client>{

    @Override
    public XtremIOV2Client getRESTClient(URI endpoint) {
        return null;
    }

    @Override
    public XtremIOV2Client getRESTClient(URI endpoint, String username, String password) {
        return new XtremIOV2Client(endpoint, username, password, getRestClient());
    }

}
