/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.TenantResource;
import com.emc.storageos.db.client.model.dao.DataAccessException;
import com.emc.storageos.db.client.model.util.TenantUtils;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import java.util.List;

public class TenantResourceFinder<T extends DataObject> extends BaseModelFinder<T> {

    private static final String TENANT_COLUMN_NAME = "tenant";

    public TenantResourceFinder(Class<T> clazz, ModelClient client) {
        super(clazz, client);
        if (!TenantResource.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class " + clazz + " is not a TenantResource");
        }
    }

    public Iterable<T> findAll(String tenant, boolean activeOnly) throws DataAccessException {
        if (StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        List<NamedElement> ids = findAllIds(tenant);
        return findByIds(toURIs(ids), activeOnly);
    }

    public List<NamedElement> findAllIds(String tenant) throws DataAccessException {
        if (StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        return client.findByAlternateId(clazz, TENANT_COLUMN_NAME, tenant);
    }

    public Iterable<T> findByLabel(String tenant, String prefix, boolean activeOnly) {
        Iterable<T> objects = findByIds(toURIs(client.findByPrefix(clazz, "label", prefix)), activeOnly);
        return TenantUtils.filter(objects, tenant);
    }
}
