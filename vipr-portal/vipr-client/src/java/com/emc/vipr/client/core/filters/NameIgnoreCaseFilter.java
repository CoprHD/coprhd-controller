/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.DataObjectRestRep;

public class NameIgnoreCaseFilter<T extends DataObjectRestRep> extends DefaultResourceFilter<T> {
    private String name;

    public NameIgnoreCaseFilter(String name) {
        this.name = name;
    }

    @Override
    public boolean accept(T item) {
        return name.equalsIgnoreCase(item.getName());
    }
}
