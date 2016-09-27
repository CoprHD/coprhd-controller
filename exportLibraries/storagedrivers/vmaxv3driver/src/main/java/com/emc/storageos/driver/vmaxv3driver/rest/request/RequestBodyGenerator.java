/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.request;

/**
 * Created by gang on 9/26/16.
 */
public interface RequestBodyGenerator {
    /**
     * Get the request body(in JSON format) for the target REST API.
     *
     * @return The request body in JSON format.
     */
    public String getRequestBody();
}
