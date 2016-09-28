/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

import com.google.gson.Gson;

/**
 * The base class for all POST/PUT REST API requests which have a body in JSON format.
 * The instance of this abstract class will be used as the POST/PUT HTTP request body.
 *
 * Created by gang on 9/26/16.
 */
public abstract class RequestBody {

    /**
     * Convert the current request bean instance into JSON format string.
     *
     * @return
     */
    public String getRequestBody() {
        return new Gson().toJson(this);
    }
}
