/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.db.client.model.TenantOrg;
import com.google.common.base.Function;

public class MapTenant implements Function<TenantOrg,TenantOrgRestRep> {
    public static final MapTenant instance = new MapTenant();

    public static MapTenant getInstance() {
        return instance;
    }

    private MapTenant() {
    }

    @Override
    public TenantOrgRestRep apply(TenantOrg resource) {
        return DbObjectMapper.map(resource);
    }
}
