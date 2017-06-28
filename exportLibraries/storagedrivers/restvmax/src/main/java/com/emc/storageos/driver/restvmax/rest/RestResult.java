/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.rest;

import com.google.gson.Gson;

public class RestResult {
    public String toJsonString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
