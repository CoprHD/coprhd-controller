/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;

public class DDExportClientModify {

    private String name;

    private String options;

    // false: add clients, true: remove clients. Default is add
    private Boolean delete;

    public DDExportClientModify(String name, String options, Boolean delete) {
        this.name = name;
        this.options = options;
        this.delete = delete;
    }

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

    public Boolean getDelete() {
        return delete;
    }

    public void setDelete(Boolean delete) {
        this.delete = delete;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
