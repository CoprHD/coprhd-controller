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
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.server.upgrade.util.BaseTestCustomMigrationCallback;
import com.emc.storageos.db.server.upgrade.util.models.updated.Resource2;
import com.emc.storageos.db.server.upgrade.util.models.updated.Resource1;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Assert;

/**
 * Callback for populating Resource3 reference on Resource 2
 */
public class Resource3RefPopulator extends BaseTestCustomMigrationCallback {
    
    List<URI> expected = new ArrayList<URI>();
    
    @Override
    public void process(){
        DbClient dbClient = getDbClient();
        List<URI> res2Keys = dbClient.queryByType(Resource2.class, false);
        Iterator<Resource2> res2Objs =
                dbClient.queryIterativeObjects(Resource2.class, res2Keys);
        while (res2Objs.hasNext()) {
            Resource2 res2 = res2Objs.next();
            if (res2.getRes1() != null ) {
                Resource1 res1 = dbClient.queryObject(
                        Resource1.class, res2.getRes1().getURI());
                if (res1 != null) {
                    StringMap map = res1.getRes3Map();
                    Set<String> keys = map.keySet();
                    URI res3Id = URI.create((String)keys.toArray()[0]);
                    res2.setRes3(res3Id);
                    dbClient.persistObject(res2);
                    expected.add(res3Id);
                }
            }
        }
    }
    
    @Override
    public void verify(){
        DbClient dbClient = getDbClient();
        List<URI> res2Keys = dbClient.queryByType(Resource2.class, false);
        Iterator<Resource2> res2Objs =
                dbClient.queryIterativeObjects(Resource2.class, res2Keys);
        Assert.assertNotNull(res2Objs);
        while (res2Objs.hasNext()) {
            Resource2 res2 = res2Objs.next();
            Assert.assertNotNull(res2.getRes1());
            Resource1 res1 = dbClient.queryObject(
                    Resource1.class, res2.getRes1().getURI());
            Assert.assertNotNull(res1);
            Assert.assertTrue(expected.contains(res2.getRes3()));
        }
    }
}
