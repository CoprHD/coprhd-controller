/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax;

import java.net.URI;
import java.net.URISyntaxException;

public class VMAXRestUtils {
    public static URI getUnisphereRestServerInfo(String ipAddress, Integer portNumber, Boolean useSSL) throws URISyntaxException {
        return new URI(useSSL ? VMAXConstants.HTTPS_URL : VMAXConstants.HTTP_URL, null, ipAddress, portNumber, VMAXConstants.UNIVMAX_BASE_URI, null, null);
    }
}
