/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.TenantDataObject;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.TenantResource;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class TenantUtils {
    public static <T extends DataObject> List<T> filter(List<T> values, final String tenant) {
        if (StringUtils.isBlank(tenant)) {
            throw new IllegalArgumentException("Tenant can not be blank");
        }
        return Lists.newArrayList(Iterables.filter(values, new Predicate<T>() {
            public boolean apply(T input) {
                return StringUtils.equals(tenant, getTenant(input));
            }
        }));
    }

    public static <T extends DataObject> String getTenant(T value) {
        if (value instanceof TenantDataObject) {
            return ((TenantDataObject) value).getTenant();
        }
        if (value instanceof TenantResource) {
            URI tenant = ((TenantResource) value).getTenant();
            return (tenant != null) ? tenant.toString() : null;
        }
        return null;
    }
}
