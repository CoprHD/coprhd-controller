/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;
import org.codehaus.jackson.map.annotate.JsonRootName;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value = "mtree_delete")
public class DDMTreeDelete {

    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
