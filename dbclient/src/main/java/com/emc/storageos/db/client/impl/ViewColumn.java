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
public class ViewColumn<T> {
    private String name;
    private Object value;
    private Class<T> classType;

    public ViewColumn(String name, Object val, Class<T> clazz) {
        this.name = name;
        this.value = val;
        this.classType = clazz;
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

    public Class<T> getClassType() {
        return classType;
    }
}
