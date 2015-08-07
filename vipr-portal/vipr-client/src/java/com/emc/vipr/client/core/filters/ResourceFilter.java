/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import java.net.URI;

import com.emc.storageos.model.DataObjectRestRep;

public interface ResourceFilter<T extends DataObjectRestRep> {
    public boolean acceptId(URI id);

    public boolean accept(T item);
}
