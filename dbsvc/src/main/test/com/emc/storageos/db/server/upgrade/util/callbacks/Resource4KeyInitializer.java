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
import com.emc.storageos.db.server.upgrade.util.BaseTestCustomMigrationCallback;
import com.emc.storageos.db.server.upgrade.util.models.updated.Resource4;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

/**
 * custom callback to set this value to something fixed, if not already set
 */
public class Resource4KeyInitializer extends BaseTestCustomMigrationCallback {
    public static final String KEY_CONST = "testkey";
    @Override
    public void process(){
        DbClient dbClient = getDbClient();
        List<URI> res4Keys = dbClient.queryByType(Resource4.class, false);
        Iterator<Resource4> res4Objs =
                dbClient.queryIterativeObjects(Resource4.class, res4Keys);
        while (res4Objs.hasNext()) {
            Resource4 res4 = res4Objs.next();
            res4.setKey(KEY_CONST);
            dbClient.persistObject(res4);
        }
    }
    
    @Override
    public void verify(){
        DbClient dbClient = getDbClient();
        List<URI> res4Keys = dbClient.queryByType(Resource4.class, false);
        Iterator<Resource4> res4Objs =
                dbClient.queryIterativeObjects(Resource4.class, res4Keys);
        Assert.assertTrue(res4Objs.hasNext());
        while (res4Objs.hasNext()) {
            Resource4 res4 = res4Objs.next();
            Assert.assertEquals(KEY_CONST, res4.getKey());
        }
    }
}
