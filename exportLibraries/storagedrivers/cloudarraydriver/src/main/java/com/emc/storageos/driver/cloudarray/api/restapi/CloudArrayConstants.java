/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.cloudarray.api.restapi;

public class CloudArrayConstants {
    public static final String ERROR_CODE = "httpStatusCode";

    // URIs
    public static final String API_LOGIN = "/zelda/utils/login";
    public static final String GET_CACHES = "/zelda/caches";

    // Methods
    public static String getAPIBaseURI(String host, String port) {
        return String.format("https://%s%s", host, (port != null) ? ":" + port : "");
    }
}
