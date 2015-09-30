/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.uimodels.RecentService;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.google.common.collect.Lists;

@Deprecated
public class RecentServiceFinder extends ModelFinder<RecentService> {

    @Deprecated
    public RecentServiceFinder(DBClientWrapper client) {
        super(RecentService.class, client);
    }

    @Deprecated
    public List<RecentService> findByUserId(String userId) {
        if (StringUtils.isBlank(userId)) {
            return Lists.newArrayList();
        }

        List<NamedElement> recentServiceIds = client.findByAlternateId(RecentService.class, RecentService.USER_ID, userId);

        return findByIds(toURIs(recentServiceIds));
    }

    @Deprecated
    public List<RecentService> findByCatalogService(URI catalogServiceId) {
        if (catalogServiceId == null) {
            return Lists.newArrayList();
        }

        List<NamedElement> recentServiceIds = client.findBy(RecentService.class, RecentService.CATALOG_SERVICE_ID, catalogServiceId);

        return findByIds(toURIs(recentServiceIds));
    }

}
