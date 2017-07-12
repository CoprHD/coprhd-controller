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
    private String value;
    private Object binVal;

    public ViewColumn(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public ViewColumn(String name, Object val) {
        this.name = name;
        this.binVal = val;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Object getBinValue() {
        return binVal;
    }

    @Override
    public String toString() {
        return "ViewColumn{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
