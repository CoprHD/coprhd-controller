package com.emc.storageos.db.client.upgrade.callbacks;
/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

import java.util.List;

import com.google.common.collect.Lists;

/**
 * Migration handler to allow recurring for snapshot/fullcopy related application snapshot/fullcopy services  
 */
public class AllowRecurringSchedulerForApplicationServicesMigration extends AllowRecurringSchedulerMigration {

    public static final List RECURRING_ALLOWED_CATALOG_SERVICES = Lists.newArrayList("CreateSnapshotOfApplication", 
            "CreateCloneOfApplication");
    @Override
    public void process() {
        enableScheduler(RECURRING_ALLOWED_CATALOG_SERVICES);
    }
}
