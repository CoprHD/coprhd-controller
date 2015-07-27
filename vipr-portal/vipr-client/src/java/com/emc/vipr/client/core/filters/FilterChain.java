/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import java.net.URI;

import com.emc.storageos.model.DataObjectRestRep;

public class FilterChain<T extends DataObjectRestRep> implements ResourceFilter<T> {
    private ResourceFilter<T> filter;

    public FilterChain(ResourceFilter<T> filter) {
        this.filter = filter;
    }

    public FilterChain<T> not() {
        this.filter = new NotFilter<T>(this.filter);
        return this;
    }

    public FilterChain<T> notId() {
        this.filter = new NotIdFilter<T>(this.filter);
        return this;
    }

    @SuppressWarnings("unchecked")
    public FilterChain<T> and(ResourceFilter<T> filter) {
        this.filter = new AndFilter<T>(this.filter, filter);
        return this;
    }

    @SuppressWarnings("unchecked")
    public FilterChain<T> or(ResourceFilter<T> filter) {
        this.filter = new OrFilter<T>(this.filter, filter);
        return this;
    }

    @Override
    public boolean acceptId(URI id) {
        return filter.acceptId(id);
    }

    @Override
    public boolean accept(T item) {
        return filter.accept(item);
    }

    public static class NotFilter<T extends DataObjectRestRep> implements ResourceFilter<T> {
        private ResourceFilter<T> filter;

        public NotFilter(ResourceFilter<T> filter) {
            this.filter = filter;
        }

        @Override
        public boolean acceptId(URI id) {
            return filter.acceptId(id);
        }

        @Override
        public boolean accept(T item) {
            return !filter.accept(item);
        }
    }

    public static class NotIdFilter<T extends DataObjectRestRep> implements ResourceFilter<T> {
        private ResourceFilter<T> filter;

        public NotIdFilter(ResourceFilter<T> filter) {
            this.filter = filter;
        }

        @Override
        public boolean acceptId(URI id) {
            return !filter.acceptId(id);
        }

        @Override
        public boolean accept(T item) {
            return filter.accept(item);
        }
    }

    public static class AndFilter<T extends DataObjectRestRep> implements ResourceFilter<T> {
        private ResourceFilter<T>[] filters;

        public AndFilter(ResourceFilter<T>... filters) {
            this.filters = filters;
        }

        @Override
        public boolean acceptId(URI id) {
            for (ResourceFilter<T> filter : filters) {
                if (!filter.acceptId(id)) {
                    return false;
                }
            }
            return filters.length > 0;
        }

        @Override
        public boolean accept(T item) {
            for (ResourceFilter<T> filter : filters) {
                if (!filter.accept(item)) {
                    return false;
                }
            }
            return filters.length > 0;
        }
    }

    public static class OrFilter<T extends DataObjectRestRep> implements ResourceFilter<T> {
        private ResourceFilter<T>[] filters;

        public OrFilter(ResourceFilter<T>... filters) {
            this.filters = filters;
        }

        @Override
        public boolean acceptId(URI id) {
            for (ResourceFilter<T> filter : filters) {
                if (filter.acceptId(id)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean accept(T item) {
            for (ResourceFilter<T> filter : filters) {
                if (filter.accept(item)) {
                    return true;
                }
            }
            return false;
        }
    }

}
