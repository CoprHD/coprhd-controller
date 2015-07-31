/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.emc.storageos.model.DataObjectRestRep;

public class IdFilter<T extends DataObjectRestRep> extends DefaultResourceFilter<T> {
    private Set<URI> ids;

    public IdFilter(URI... ids) {
        this(Arrays.asList(ids));
    }

    public IdFilter(Collection<URI> ids) {
        this.ids = new HashSet<URI>();
        for (URI id : ids) {
            this.ids.add(id);
        }
    }

    @Override
    public boolean acceptId(URI id) {
        return ids.contains(id);
    }
}
