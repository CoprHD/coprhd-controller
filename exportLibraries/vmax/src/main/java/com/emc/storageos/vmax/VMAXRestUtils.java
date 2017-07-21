/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax;

import java.net.URI;
import java.net.URISyntaxException;

public class VMAXRestUtils {

    public static URI getUnisphereRestServerInfo(String ipAddress, Integer portNumber, Boolean useSSL) throws URISyntaxException {
        String protocol = VMAXConstants.HTTP_URL;
        if (Boolean.TRUE.equals(useSSL)) {
            protocol = VMAXConstants.HTTPS_URL;
        }
        URI uri = new URI(protocol, null, ipAddress, portNumber, VMAXConstants.UNIVMAX_BASE_URI, null, null);
        return uri;
    }

}
