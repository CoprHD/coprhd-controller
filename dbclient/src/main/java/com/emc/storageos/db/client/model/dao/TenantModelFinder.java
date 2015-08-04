/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.dao.DataAccessException;
import com.emc.storageos.db.client.model.util.TenantDataObject;
import com.emc.storageos.db.client.model.util.TenantUtils;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

import java.util.List;

public class TenantModelFinder<T extends DataObject> extends BaseModelFinder<T> {

    public TenantModelFinder(Class<T> clazz, ModelClient client) {
        super(clazz, client);
    }

    public Iterable<T> findAll(String tenant, boolean activeOnly) throws DataAccessException {
        if (StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        List<NamedElement> ids = findAllIds(tenant);
        return findByIds(toURIs(ids), activeOnly);
    }

    public Iterable<T> findByLabel(String tenant, String prefix, boolean activeOnly) {
        Iterable<T> objects = findByIds(toURIs(client.findByPrefix(clazz, "label", prefix)), activeOnly);
        return TenantUtils.filter(objects, tenant);
    }

    public List<NamedElement> findAllIds(String tenant) throws DataAccessException {
        if (StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        return client.findByAlternateId(clazz, TenantDataObject.TENANT_COLUMN_NAME, tenant);
    }
}
