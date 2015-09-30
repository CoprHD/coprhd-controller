/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;

public class DDExportClient {

    // Endpoint
    private String name;

    // Space separated options
    private String options;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public DDExportClient(String name, String options) {
        this.name = name;
        this.options = options;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
