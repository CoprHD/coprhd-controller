/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.server.upgrade.util.callbacks;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.server.upgrade.util.BaseTestCustomMigrationCallback;
import com.emc.storageos.db.server.upgrade.util.models.updated.Resource3;
import com.emc.storageos.db.server.upgrade.util.models.updated.Resource4;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

/**
 *  custom callback to initialize res4 field value on Resource 3 based on flags initialized in other callback
 */
public class Resource3Resource4RefInitializer extends BaseTestCustomMigrationCallback {
    
    List<URI> expected = new ArrayList<URI>();

    @Override
    public void process(){
        DbClient dbClient = getDbClient();
        List<URI> res3Keys = dbClient.queryByType(Resource3.class, false);
        Iterator<Resource3> res3Objs =
                dbClient.queryIterativeObjects(Resource3.class, res3Keys);
        while (res3Objs.hasNext()) {
            Resource3 res3 = res3Objs.next();
            if (res3.getExtraFlags() > 0L) {
                Resource4 res4 = new Resource4();
                res4.setId(URIUtil.createId(Resource4.class));
                res4.setLabel("res4 for "+res3.getLabel());
                dbClient.createObject(res4);
                res3.setRes4(res4.getId());
                dbClient.persistObject(res3);
                expected.add(res4.getId());
            }
        }
    }
    
    @Override
    public void verify(){
        DbClient dbClient = getDbClient();
        List<URI> res3Keys = dbClient.queryByType(Resource3.class, false);
        Iterator<Resource3> res3Objs =
                dbClient.queryIterativeObjects(Resource3.class, res3Keys);
        Assert.assertTrue(res3Objs.hasNext());
        while (res3Objs.hasNext()) {
            Resource3 res3 = res3Objs.next();
            Assert.assertNotNull(res3.getRes4());
            Resource4 res4 = dbClient.queryObject(Resource4.class, res3.getRes4());
            Assert.assertNotNull(res4);
            Assert.assertTrue(expected.contains(res4.getId()));
        }
    }
}
