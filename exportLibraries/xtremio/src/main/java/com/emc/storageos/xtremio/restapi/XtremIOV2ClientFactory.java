package com.emc.storageos.xtremio.restapi;

import java.net.URI;

import org.springframework.stereotype.Component;

import com.emc.storageos.common.http.RestAPIFactory;

@Component
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
