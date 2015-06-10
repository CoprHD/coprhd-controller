/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import junit.framework.Assert;

import org.junit.BeforeClass;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.BlockObjectNormalizeWwnMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test proper normalizing of the BlockObject wwn field
 * 
 * Here's the basic execution flow for the test case:
 * - setup() runs, bringing up a "pre-migration" version
 *   of the database, using the DbSchemaScannerInterceptor
 *   you supply to hide your new field or column family
 *   when generating the "before" schema. 
 * - Your implementation of prepareData() is called, allowing
 *   you to use the internal _dbClient reference to create any 
 *   needed pre-migration test data.
 * - The database is then shutdown and restarted (without using
 *   the interceptor this time), so the full "after" schema
 *   is available.
 * - The dbsvc detects the diffs in the schema and executes the
 *   migration callbacks as part of the startup process.
 * - Your implementation of verifyResults() is called to
 *   allow you to confirm that the migration of your prepared
 *   data went as expected.
 * 
 */
public class BlockObjectNormalizeWwnMigrationTest extends DbSimpleMigrationTestBase {
    
    private final int INSTANCES_TO_CREATE = 10;
    private final int WWN_LENGTH = 32;
    private Map<URI, String> blockObjectWwns = new HashMap<URI, String>();
    
    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.1", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new BlockObjectNormalizeWwnMigration());
        }});
        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "2.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.2";
    }

    @Override
    protected void prepareData() throws Exception {
        prepareBlockObjectData(Volume.class);
        prepareBlockObjectData(BlockSnapshot.class);
        prepareBlockObjectData(BlockMirror.class);
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyBlockObjectData(Volume.class);
        verifyBlockObjectData(BlockSnapshot.class);
        verifyBlockObjectData(BlockMirror.class);
    }
    
    private void prepareBlockObjectData(Class<? extends BlockObject> clazz) throws Exception {

        for (int i = 0; i < INSTANCES_TO_CREATE; i++) {
            BlockObject blockObject = clazz.newInstance();
            String lowerCaseWwn = generateLowerCaseWwn();
            blockObject.setId(URIUtil.createId(clazz));
            blockObjectWwns.put(blockObject.getId(), lowerCaseWwn);
            blockObject.setWWN(lowerCaseWwn);
            _dbClient.createObject(blockObject);
        }
        
        List<URI> keys = _dbClient.queryByType(clazz, false);
        int count = 0;       
        for (@SuppressWarnings("unused") URI ignore : keys) {
            count++;
        }
        Assert.assertTrue("Expected " + INSTANCES_TO_CREATE + " prepared " + clazz.getSimpleName() + ", found only " + count, count == INSTANCES_TO_CREATE); 
    }
    
    private void verifyBlockObjectData(Class<? extends BlockObject> clazz) throws Exception {
        
        List<URI> keys = _dbClient.queryByType(clazz, false);
        int count = 0;
        Iterator<? extends BlockObject> objs =
                _dbClient.queryIterativeObjects(clazz, keys);
        while (objs.hasNext()) {
            BlockObject blockObject = objs.next();
            count++;
            Assert.assertTrue("Wwn should be upper case ", isUpperCase(blockObject.getWWN()));
            Assert.assertTrue("Block object id should be in the map", blockObjectWwns.containsKey(blockObject.getId()));
            Assert.assertEquals("Wwn should be upper case equivalent of lower case Wwn", blockObjectWwns.get(blockObject.getId()).toUpperCase(), blockObject.getWWN());
        }
        Assert.assertTrue("We should still have " + INSTANCES_TO_CREATE + " " + clazz.getSimpleName() + " after migration, not " + count, count == INSTANCES_TO_CREATE);        
    }
    
    private String generateLowerCaseWwn() {
        Random r = new Random();
        StringBuffer sb = new StringBuffer();
        while(sb.length() < WWN_LENGTH){
            sb.append(Integer.toHexString(r.nextInt()));
        }
        return sb.toString().substring(0, WWN_LENGTH).toLowerCase();
    }

    private boolean isUpperCase(String s) {
        int size = s.length();
        for (int i = 0; i < size; i++) {
            if (!Character.isDigit(s.charAt(i)) 
                    && !Character.isUpperCase(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
