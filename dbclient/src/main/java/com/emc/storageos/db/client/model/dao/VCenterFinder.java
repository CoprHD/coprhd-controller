/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.util.TenantUtils;
import com.google.common.collect.Lists;

public class VCenterFinder extends TenantResourceFinder<Vcenter> {

    public VCenterFinder(ModelClient client) {
        super(Vcenter.class, client);
    }
    
    public Iterable<Vcenter> findByHostname(String tenant, String hostname, boolean activeOnly) {
        if (StringUtils.isBlank(hostname) || StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        List<NamedElement> vcenterIds = client.findByAlternateId(Vcenter.class, "ipAddress", hostname);
        
        return TenantUtils.filter(findByIds(toURIs(vcenterIds), activeOnly), tenant);
    }

    public Iterable<Vcenter> findByNativeGuid(String nativeGuid, boolean activeOnly) {
        List<NamedElement> vcenterIds = client.findByAlternateId(Vcenter.class, HostFinder.NATIVEGUID_COLUMN_NAME, nativeGuid);
        return findByIds(toURIs(vcenterIds), activeOnly);
    }
}
