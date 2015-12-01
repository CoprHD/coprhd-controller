package com.emc.storageos.xtremio.restapi;

import java.net.URI;

import org.springframework.stereotype.Component;

import com.emc.storageos.common.http.RestAPIFactory;

@Component
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
