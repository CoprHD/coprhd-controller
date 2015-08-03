/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util.callbacks2;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.server.upgrade.util.BaseTestCustomMigrationCallback;
import com.emc.storageos.db.server.upgrade.util.models.updated2.Resource5;

/**
 * custom callback to set this value to something fixed, if not already set
 */
public class Resource5Initializer extends BaseTestCustomMigrationCallback {
    public static final int COUNT = 10;

    @Override
    public void process() {
        DbClient dbClient = getDbClient();
        for (int i = 0; i < COUNT; i++) {
            Resource5 res5 = new Resource5();
            res5.setId(URIUtil.createId(Resource5.class));
            dbClient.createObject(res5);
        }
    }

    @Override
    public void verify() {
        DbClient dbClient = getDbClient();
        List<URI> res5Ids = dbClient.queryByType(Resource5.class, false);
        Iterator<Resource5> res5Objs =
                dbClient.queryIterativeObjects(Resource5.class, res5Ids);
        Assert.assertTrue(res5Objs.hasNext());
        int count = 0;
        while (res5Objs.hasNext()) {
            res5Objs.next();
            count++;
        }
        Assert.assertEquals(COUNT, count);
    }
}
