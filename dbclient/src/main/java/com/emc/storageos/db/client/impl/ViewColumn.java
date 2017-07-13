/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.db.client.impl;

/**
 * Created by wangs12 on 7/6/2017.
 */
public class ViewColumn {
    private String name;
    private Object value;

    public ViewColumn(String name, Object val) {
        this.name = name;
        this.value = val;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "ViewColumn{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
