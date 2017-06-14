package com.emc.storageos.db.client.upgrade.callbacks;
/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.google.common.collect.Lists;

/**
 * Migration handler to allow recurring for snapshot/fullcopy related catalog services  
 */
public class AllowRecurringSchedulerMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(AllowRecurringSchedulerMigration.class);

    public static List RECURRING_ALLOWED_CATALOG_SERVICES = Lists.newArrayList("CreateBlockSnapshot", 
            "CreateFileSnapshot", "CreateFullCopy");
    @Override
    public void process() {
        enableScheduler(RECURRING_ALLOWED_CATALOG_SERVICES);
    }
    
    protected void enableScheduler(List<String> allowedCatalogServices) {
        List<URI> catalogServiceIds = dbClient.queryByType(CatalogService.class, true);
        int cnt = 0;
        for(URI catalogServiceId : catalogServiceIds) {
            CatalogService catalogService = dbClient.queryObject(CatalogService.class, catalogServiceId);
            String baseService = catalogService.getBaseService();
            if (allowedCatalogServices.contains(baseService)) {
                log.info("Allow recurring for catalog service {}", catalogService.getTitle());
                catalogService.setRecurringAllowed(true);
                dbClient.updateObject(catalogService);
                cnt ++;
            }
        }
        log.info("Completed updating recurringAllowed flag for catalog services - {}", cnt);

    }
}
