/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.model.util.TenantUtils;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.Vcenter;
import com.google.common.collect.Lists;

public class VCenterFinder extends TenantResourceFinder<Vcenter> {

    public VCenterFinder(DBClientWrapper client) {
        super(Vcenter.class, client);
    }

    public List<Vcenter> findByHostname(String tenant, String hostname) {
        if (StringUtils.isBlank(hostname) || StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        List<NamedElement> vcenterIds = client.findByAlternateId(Vcenter.class, "ipAddress", hostname);

        return TenantUtils.filter(findByIds(toURIs(vcenterIds)), tenant);
    }

}
