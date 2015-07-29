/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import com.emc.sa.model.util.TenantUtils;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.TenantResource;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import java.util.List;

public class TenantResourceFinder<T extends DataObject> extends BaseModelFinder<T> {

    private static final String TENANT_COLUMN_NAME = "tenant";

    public TenantResourceFinder(Class<T> clazz, DBClientWrapper client) {
        super(clazz, client);
        if (!TenantResource.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class " + clazz + " is not a TenantResource");
        }
    }

    public List<T> findAll(String tenant) throws DataAccessException {
        if (StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        List<NamedElement> ids = findAllIds(tenant);
        return findByIds(toURIs(ids));
    }

    public List<NamedElement> findAllIds(String tenant) throws DataAccessException {
        if (StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        return client.findByAlternateId(clazz, TENANT_COLUMN_NAME, tenant);
    }

    public List<T> findByLabel(String tenant, String prefix) {
        List<T> objects = findByIds(toURIs(client.findByPrefix(clazz, "label", prefix)));
        return TenantUtils.filter(objects, tenant);
    }
}
