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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.server.upgrade.util.BaseTestCustomMigrationCallback;
import com.emc.storageos.db.server.upgrade.util.models.updated.Resource6;
import com.emc.storageos.db.server.upgrade.util.models.updated.Resource3;

/**
 *  initialize flags on Resource 3 and its subclasses
 */
public class Resource3FlagsInitializer extends BaseTestCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(Resource3FlagsInitializer.class);
    public static final Long FLAG = 2L;

    @Override
    public void process(){
        DbClient dbClient = getDbClient();
        // Check Resource3
        List<URI> res3Keys = dbClient.queryByType(Resource3.class, false);
        Iterator<Resource3> res3Objs =
                dbClient.queryIterativeObjects(Resource3.class, res3Keys);
        while (res3Objs.hasNext()) {
            Resource3 res3 = res3Objs.next();
            log.info("Check Resource3 flag: " + res3.getId() + ","+ res3.getExtraFlags());
            if (res3.getExtraFlags() == null) {
                res3.setExtraFlags(FLAG);
                dbClient.persistObject(res3);
            }
        }
        
        // Check Resource6
        List<URI> res6Keys = dbClient.queryByType(Resource6.class, false);
        Iterator<Resource6> res6Objs =
                dbClient.queryIterativeObjects(Resource6.class, res6Keys);
        while (res6Objs.hasNext()) {
            Resource6 res6 = res6Objs.next();
            log.info("Check Resource6 flag: " + res6.getId() + ","+ res6.getExtraFlags());
            if (res6.getExtraFlags() == null) {
                res6.setExtraFlags(FLAG);
                dbClient.persistObject(res6);
            }
        }
    }
    
    @Override
    public void verify(){
        DbClient dbClient = getDbClient();
        // Check Resource3
        List<URI> res3Keys = dbClient.queryByType(Resource3.class, false);
        Iterator<Resource3> res3Objs =
                dbClient.queryIterativeObjects(Resource3.class, res3Keys);
        Assert.assertTrue(res3Objs.hasNext());
        while (res3Objs.hasNext()) {
            Assert.assertEquals(FLAG, res3Objs.next().getExtraFlags());
        }
        
        // Check Resource6
        List<URI> res6Keys = dbClient.queryByType(Resource6.class, false);
        Iterator<Resource6> res6Objs =
                dbClient.queryIterativeObjects(Resource6.class, res6Keys);
        Assert.assertTrue(res6Objs.hasNext());
        while (res6Objs.hasNext()) {
            Assert.assertEquals(FLAG, res6Objs.next().getExtraFlags());
        }
    }
}
