/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;

public class DDShareListModify {

    private String name;

    // action: false: add, true: delete. Default is add
    private Boolean delete;

    public DDShareListModify(String name, Boolean delete) {
        this.name = name;
        this.delete = delete;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
