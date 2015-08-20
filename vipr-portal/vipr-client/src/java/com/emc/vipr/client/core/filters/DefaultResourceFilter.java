/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import java.net.URI;

import com.emc.storageos.model.DataObjectRestRep;

public class DefaultResourceFilter<T extends DataObjectRestRep> implements ResourceFilter<T> {
    @Override
    public boolean acceptId(URI id) {
        return true;
    }

    @Override
    public boolean accept(T item) {
        return true;
    }

    /**
     * Creates a filter that will <b>not</b> accept values where {@link #accept(DataObjectRestRep)} returns true.
     * 
     * @return the new filter.
     */
    public FilterChain<T> not() {
        return new FilterChain<T>(this).not();
    }

    /**
     * Creates a filter that will <b>not</b> accept values where {@link #acceptId(URI)} returns true.
     * 
     * @return the new filter.
     */
    public ResourceFilter<T> notId() {
        return new FilterChain<T>(this).notId();
    }

    public FilterChain<T> and(ResourceFilter<T> filter) {
        return new FilterChain<T>(this).and(filter);
    }

    public FilterChain<T> or(ResourceFilter<T> filter) {
        return new FilterChain<T>(this).or(filter);
    }
}
