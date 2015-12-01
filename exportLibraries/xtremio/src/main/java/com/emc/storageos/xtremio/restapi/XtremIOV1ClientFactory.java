package com.emc.storageos.xtremio.restapi;

import java.net.URI;

import com.emc.storageos.common.http.RestAPIFactory;

public class XtremIOV1ClientFactory extends RestAPIFactory<XtremIOV1Client> {
    
    @Override
    public XtremIOV1Client getRESTClient(URI endpoint) {
        return null;
    }

    @Override
    public XtremIOV1Client getRESTClient(URI endpoint, String username, String password) {
        return new XtremIOV1Client(endpoint, username, password, getRestClient());
    }

}
